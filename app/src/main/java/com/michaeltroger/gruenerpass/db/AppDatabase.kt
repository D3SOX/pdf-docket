package com.michaeltroger.gruenerpass.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Certificate::class, Tag::class, CertificateTagCrossRef::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun certificateDao(): CertificateDao
    abstract fun tagDao(): TagDao

    companion object {
        /**
         * Released databases have to keep their data. Every version bump needs an auto migration
         * or a migration added here, AppDatabaseMigrationTest fails otherwise.
         */
        fun build(context: Context, name: String = "greenpass"): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name).build()
    }
}
