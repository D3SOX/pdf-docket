package com.michaeltroger.gruenerpass.di

import android.content.Context
import androidx.room.Room
import com.michaeltroger.gruenerpass.db.AppDatabase
import com.michaeltroger.gruenerpass.db.CertificateDao
import com.michaeltroger.gruenerpass.db.TagDao
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
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
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
