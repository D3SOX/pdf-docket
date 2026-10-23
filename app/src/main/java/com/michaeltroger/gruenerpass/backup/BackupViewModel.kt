package com.michaeltroger.gruenerpass.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.bouncycastle.crypto.io.InvalidCipherTextIOException
import org.json.JSONException
import java.io.IOException
import javax.inject.Inject

sealed interface BackupState {
    data object Idle : BackupState
    data object AskingExportPassword : BackupState
    data class AskingRestorePassword(val uri: Uri, val isWrongPassword: Boolean = false) : BackupState
    data object CreatingBackup : BackupState
    data object RestoringBackup : BackupState
}

sealed interface BackupMessage {
    data object Created : BackupMessage
    data object CreateFailed : BackupMessage
    data class Restored(val count: Int) : BackupMessage
    data object NotABackup : BackupMessage
    data object RestoreFailed : BackupMessage
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private val _messages = Channel<BackupMessage>(Channel.BUFFERED)
    val messages: Flow<BackupMessage> = _messages.receiveAsFlow()

    // kept out of the saved state, so the password never ends up on disk
    private var exportPassword: String? = null

    fun onCreateBackup() {
        _state.value = BackupState.AskingExportPassword
    }

    /** Called before the file picker opens. */
    fun onExportPasswordSet(password: String) {
        exportPassword = password
        _state.value = BackupState.Idle
    }

    @Suppress("SwallowedException")
    fun onExportFileSelected(uri: Uri?) {
        val password = exportPassword
        exportPassword = null
        uri ?: return
        viewModelScope.launch {
            if (password == null) {
                // the app was restarted while the file picker was open
                repository.discard(uri)
                return@launch
            }
            _state.value = BackupState.CreatingBackup
            val message = try {
                repository.export(uri, password)
                BackupMessage.Created
            } catch (e: IOException) {
                BackupMessage.CreateFailed
            } catch (e: SecurityException) {
                BackupMessage.CreateFailed
            }
            _state.value = BackupState.Idle
            _messages.send(message)
        }
    }

    fun onRestoreFileSelected(uri: Uri) {
        _state.value = BackupState.AskingRestorePassword(uri)
    }

    @Suppress("SwallowedException")
    fun onRestorePasswordEntered(password: String) {
        val uri = (state.value as? BackupState.AskingRestorePassword)?.uri ?: return
        viewModelScope.launch {
            _state.value = BackupState.RestoringBackup
            val message = try {
                BackupMessage.Restored(repository.restore(uri, password))
            } catch (e: InvalidCipherTextIOException) {
                _state.value = BackupState.AskingRestorePassword(uri, isWrongPassword = true)
                return@launch
            } catch (e: NotABackupException) {
                BackupMessage.NotABackup
            } catch (e: IOException) {
                BackupMessage.RestoreFailed
            } catch (e: JSONException) {
                BackupMessage.RestoreFailed
            } catch (e: SecurityException) {
                BackupMessage.RestoreFailed
            }
            _state.value = BackupState.Idle
            _messages.send(message)
        }
    }

    fun onDialogDismissed() {
        _state.value = BackupState.Idle
    }
}
