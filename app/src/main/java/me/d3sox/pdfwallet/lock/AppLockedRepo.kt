package me.d3sox.pdfwallet.lock

import me.d3sox.pdfwallet.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockedRepo @Inject constructor(
    settingsRepository: SettingsRepository,
) {
    private val isLocked = MutableStateFlow(true)

    val isAppLocked: Flow<Boolean> = combine(settingsRepository.settings, isLocked) { settings, locked ->
        settings.requireAuthentication && locked
    }.distinctUntilChanged()

    fun lockApp() {
        isLocked.value = true
    }

    fun unlockApp() {
        isLocked.value = false
    }
}
