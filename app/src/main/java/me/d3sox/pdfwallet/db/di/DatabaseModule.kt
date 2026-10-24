package me.d3sox.pdfwallet.db.di

import android.content.Context
import me.d3sox.pdfwallet.db.AppDatabase
import me.d3sox.pdfwallet.db.CertificateDao
import me.d3sox.pdfwallet.db.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideCertificateDao(appDatabase: AppDatabase): CertificateDao {
        return appDatabase.certificateDao()
    }

    @Singleton
    @Provides
    fun provideTagDao(appDatabase: AppDatabase): TagDao {
        return appDatabase.tagDao()
    }

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.build(context)
    }
}
