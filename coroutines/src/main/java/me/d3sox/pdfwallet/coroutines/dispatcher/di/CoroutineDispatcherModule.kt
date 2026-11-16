package me.d3sox.pdfwallet.coroutines.dispatcher.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

@Module
@InstallIn(SingletonComponent::class)
public object CoroutineDispatchersModule {
    @IoDispatcher
    @Provides
    internal fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @DefaultDispatcher
    @Provides
    internal fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}


@Retention(AnnotationRetention.BINARY)
@Qualifier
public annotation class IoDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
public annotation class DefaultDispatcher
