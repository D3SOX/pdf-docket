package me.d3sox.pdfwallet.di

import me.d3sox.pdfwallet.lock.AppLockTimer
import me.d3sox.pdfwallet.lock.AppLockedRepo
import me.d3sox.pdfwallet.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import javax.inject.Qualifier
import javax.inject.Singleton

/** For work that has to finish even when the screen that started it is closed. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @ApplicationScope
    @Provides
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Singleton
    @Provides
    fun provideAppLockTimer(settingsRepository: SettingsRepository, lockedRepo: AppLockedRepo): AppLockTimer =
        AppLockTimer(MainScope(), settingsRepository.settings.map { it.lockTimeout }, lockedRepo::lockApp)
}
