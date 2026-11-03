package me.d3sox.pdfwallet.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.d3sox.pdfwallet.lock.AppLockedRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val lockedRepo: AppLockedRepo,
) : ViewModel() {

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }

    /** Only called after a successful authentication. */
    fun onAuthenticationToggled() = viewModelScope.launch {
        lockedRepo.unlockApp()
        settingsRepository.update { it.copy(requireAuthentication = !it.requireAuthentication) }
    }
}
