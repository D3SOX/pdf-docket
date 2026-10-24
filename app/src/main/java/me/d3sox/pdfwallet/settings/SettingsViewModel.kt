package me.d3sox.pdfwallet.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.d3sox.pdfwallet.lock.AppLockedRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val lockedRepo: AppLockedRepo,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }

    /** Only called after a successful authentication. */
    fun onAuthenticationToggled() = viewModelScope.launch {
        lockedRepo.unlockApp()
        settingsRepository.update { it.copy(requireAuthentication = !it.requireAuthentication) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
