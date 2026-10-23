package com.michaeltroger.gruenerpass

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaeltroger.gruenerpass.documents.DocumentFilter
import com.michaeltroger.gruenerpass.documents.DocumentRepository
import com.michaeltroger.gruenerpass.lock.AppLockedRepo
import com.michaeltroger.gruenerpass.pdfimporter.PdfImportResult
import com.michaeltroger.gruenerpass.pdfimporter.PdfImporter
import com.michaeltroger.gruenerpass.settings.AppSettings
import com.michaeltroger.gruenerpass.settings.SettingsRepository
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
        while (importQueue.isNotEmpty()) {
            importFile(importQueue.removeFirst())?.let(imported::add)
        }
        if (imported.isEmpty()) return
        // make sure the new documents are part of the visible documents
        documentFilter.clear()
        // not suspending, so files selected meanwhile can't end up in the queue of a finished job
        _events.trySend(
            imported.singleOrNull()?.let(MainEvent::OpenDocument) ?: MainEvent.DocumentsImported(imported.size)
        )
    }

    /**
     * Imports a single file, asking for its password if needed. Returns the id of the new document.
     */
    @Suppress("ReturnCount")
    private suspend fun importFile(uri: Uri): String? {
        // files are only imported once the app is unlocked
        lockedRepo.isAppLocked.first { !it }
        // e.g. a second tap on OK for the previous file
        passwords.tryReceive()
        if (pdfImporter.preparePendingFile(uri) is PdfImportResult.ParsingError) {
            _events.trySend(MainEvent.ImportFailed)
            return null
        }
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
                    documentRepository.add(result.pendingCertificate)
                    return result.pendingCertificate.fileName
                }
                PdfImportResult.ParsingError -> {
                    _events.trySend(MainEvent.ImportFailed)
                    return null
                }
                PdfImportResult.NoFileToImport -> return null
                PdfImportResult.PasswordRequired -> password = passwords.receive() ?: run {
                    _passwordPrompt.value = PasswordPrompt.NONE
                    pdfImporter.deletePendingFile()
                    return null
                }
            }
        }
    }

    override fun onCleared() {
        pdfImporter.deletePendingFile()
    }
}
