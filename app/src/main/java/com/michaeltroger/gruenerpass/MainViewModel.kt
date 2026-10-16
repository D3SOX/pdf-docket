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
import kotlinx.coroutines.flow.filter
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

    private var timeoutJob: Job? = null

    init {
        viewModelScope.launch {
            // pending files are only imported once the app is unlocked
            combine(lockedRepo.isAppLocked, pdfImporter.hasPendingFile()) { isLocked, hasPendingFile ->
                !isLocked && hasPendingFile
            }.filter { it }.collect {
                importPendingFile(password = null)
            }
        }
        onUserInteraction()
    }

    fun onFileSelected(uri: Uri) = viewModelScope.launch {
        if (pdfImporter.preparePendingFile(uri) is PdfImportResult.ParsingError) {
            _events.send(MainEvent.ImportFailed)
        }
    }

    fun onPasswordEntered(password: String) = viewModelScope.launch {
        importPendingFile(password)
    }

    fun onPasswordDialogDismissed() {
        _passwordPrompt.value = PasswordPrompt.NONE
        pdfImporter.deletePendingFile()
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

    private suspend fun importPendingFile(password: String?) {
        val result = pdfImporter.importPendingFile(password)
        _passwordPrompt.value = when {
            result !is PdfImportResult.PasswordRequired -> PasswordPrompt.NONE
            password == null -> PasswordPrompt.REQUIRED
            else -> PasswordPrompt.WRONG
        }
        when (result) {
            is PdfImportResult.Success -> {
                documentRepository.add(result.pendingCertificate)
                // make sure the new document is part of the visible documents
                documentFilter.clear()
                _events.send(MainEvent.OpenDocument(result.pendingCertificate.fileName))
            }
            PdfImportResult.ParsingError -> _events.send(MainEvent.ImportFailed)
            PdfImportResult.PasswordRequired, PdfImportResult.NoFileToImport -> Unit
        }
    }

    override fun onCleared() {
        pdfImporter.deletePendingFile()
    }
}
