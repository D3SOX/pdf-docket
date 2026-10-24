package me.d3sox.pdfwallet.di

import me.d3sox.pdfwallet.coroutines.dispatcher.di.DefaultDispatcher
import me.d3sox.pdfwallet.coroutines.dispatcher.di.IoDispatcher
import me.d3sox.pdfwallet.coroutines.dispatcher.di.MainDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
@Module
@InstallIn(SingletonComponent::class)
object TestCoroutineDispatchersModule {

    @IoDispatcher
    @Provides
    fun providesIoDispatcher(): CoroutineDispatcher = UnconfinedTestDispatcher()

    @DefaultDispatcher
    @Provides
    fun providesDefaultDispatcher(): CoroutineDispatcher = UnconfinedTestDispatcher()

    @MainDispatcher
    @Provides
    fun providesMainDispatcher(): CoroutineDispatcher = UnconfinedTestDispatcher()
}
