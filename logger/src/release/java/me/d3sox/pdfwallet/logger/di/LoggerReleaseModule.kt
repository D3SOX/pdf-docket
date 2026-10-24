package me.d3sox.pdfwallet.logger.di

import me.d3sox.pdfwallet.logger.Logger
import me.d3sox.pdfwallet.logger.LoggerReleaseImpl

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
public abstract class LoggerReleaseModule {

    @Binds
    internal abstract fun bindLogger(
        loggerImpl: LoggerReleaseImpl
    ): Logger
}
