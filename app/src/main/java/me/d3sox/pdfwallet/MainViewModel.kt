package me.d3sox.pdfwallet

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.d3sox.pdfwallet.documents.DocumentFilter
import me.d3sox.pdfwallet.documents.DocumentRepository
import me.d3sox.pdfwallet.lock.AppLockedRepo
import me.d3sox.pdfwallet.pdfimporter.PdfImportResult
import me.d3sox.pdfwallet.pdfimporter.PdfImporter
import me.d3sox.pdfwallet.settings.AppSettings
import me.d3sox.pdfwallet.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

private val INTERACTION_TIMEOUT = 5.minutes

enum class PasswordPrompt { NONE, REQUIRED, WRONG }

data class MainUiState(
    val settings: AppSettings,
    val isLocked: Boolean,
)

sealed interface MainEvent {
    data class OpenDocument(val id: String) : MainEvent
    data class DocumentsImported(val count: Int) : MainEvent
    data class DocumentsAlreadyImported(val count: Int) : MainEvent
    data object ImportFailed : MainEvent
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val pdfImporter: PdfImporter,
    private val lockedRepo: AppLockedRepo,
    private val documentRepository: DocumentRepository,
    private val documentFilter: DocumentFilter,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** null until the settings are loaded */
    val uiState: StateFlow<MainUiState?> = combine(
        settingsRepository.settings,
        lockedRepo.isAppLocked,
        ::MainUiState,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _passwordPrompt = MutableStateFlow(PasswordPrompt.NONE)
    val passwordPrompt: StateFlow<PasswordPrompt> = _passwordPrompt.asStateFlow()

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events: Flow<MainEvent> = _events.receiveAsFlow()

    /** a password, or null when the password dialog was dismissed */
    private val passwords = Channel<String?>(Channel.CONFLATED)

    // only accessed on the main thread
    private val importQueue = ArrayDeque<Uri>()
    private var importJob: Job? = null
    private var timeoutJob: Job? = null

    init {
        onUserInteraction()
    }

    fun onFilesSelected(uris: List<Uri>) {
        importQueue += uris
        if (importJob?.isActive != true) {
            importJob = viewModelScope.launch { importQueuedFiles() }
        }
    }

    fun onShortcutOpened(id: String) = viewModelScope.launch {
        if (documentRepository.exists(id)) {
            // make sure the document is part of the visible documents
            documentFilter.clear()
            _events.send(MainEvent.OpenDocument(id))
        }
    }

    fun onPasswordEntered(password: String) {
        passwords.trySend(password)
    }

    fun onPasswordDialogDismissed() {
        passwords.trySend(null)
    }

    fun onUserInteraction() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(INTERACTION_TIMEOUT)
            lockedRepo.lockApp()
        }
    }

    fun onAuthenticated() {
        lockedRepo.unlockApp()
    }

    private suspend fun importQueuedFiles() {
        val imported = mutableListOf<String>()
        val duplicates = mutableListOf<String>()
        var hasFailures = false
        while (importQueue.isNotEmpty()) {
            when (val result = importFile(importQueue.removeFirst())) {
                ImportResult.Failed -> hasFailures = true
                ImportResult.Skipped -> Unit
                is ImportResult.Imported -> imported += result.id
                is ImportResult.Duplicate -> duplicates += result.existingId
            }
        }
        // not suspending, so files selected meanwhile can't end up in the queue of a finished job
        // a single file opens its document, even if it was already imported
        val event = (imported + duplicates).distinct().singleOrNull()?.let(MainEvent::OpenDocument)
            ?: imported.takeIf { it.isNotEmpty() }?.let { MainEvent.DocumentsImported(it.size) }
        if (event != null) {
            // make sure the documents are part of the visible documents
            documentFilter.clear()
            _events.trySend(event)
        }
        // one message each for the whole batch, after the documents are shown
        if (duplicates.isNotEmpty()) _events.trySend(MainEvent.DocumentsAlreadyImported(duplicates.size))
        if (hasFailures) _events.trySend(MainEvent.ImportFailed)
    }

    private sealed interface ImportResult {
        data class Imported(val id: String) : ImportResult
        data class Duplicate(val existingId: String) : ImportResult
        data object Failed : ImportResult
        /** e.g. the password dialog was dismissed */
        data object Skipped : ImportResult
    }

    /**
     * Imports a single file, asking for its password if needed.
     */
    @Suppress("ReturnCount")
    private suspend fun importFile(uri: Uri): ImportResult {
        // files are only imported once the app is unlocked
        lockedRepo.isAppLocked.first { !it }
        // e.g. a second tap on OK for the previous file
        passwords.tryReceive()
        if (pdfImporter.preparePendingFile(uri) is PdfImportResult.ParsingError) return ImportResult.Failed
        var password: String? = null
        while (true) {
            val result = pdfImporter.importPendingFile(password)
            _passwordPrompt.value = when {
                result !is PdfImportResult.PasswordRequired -> PasswordPrompt.NONE
                password == null -> PasswordPrompt.REQUIRED
                else -> PasswordPrompt.WRONG
            }
            when (result) {
                is PdfImportResult.Success -> {
                    return documentRepository.add(result.pendingCertificate)?.let(ImportResult::Duplicate)
                        ?: ImportResult.Imported(result.pendingCertificate.fileName)
                }
                PdfImportResult.ParsingError -> return ImportResult.Failed
                PdfImportResult.NoFileToImport -> return ImportResult.Skipped
                PdfImportResult.PasswordRequired -> password = passwords.receive() ?: run {
                    _passwordPrompt.value = PasswordPrompt.NONE
                    pdfImporter.deletePendingFile()
                    return ImportResult.Skipped
                }
            }
        }
    }

    override fun onCleared() {
        pdfImporter.deletePendingFile()
    }
}
