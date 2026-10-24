package me.d3sox.pdfwallet.di

import android.content.Context
import androidx.room.Room
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
object TestDatabaseModule {

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

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
}
