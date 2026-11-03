package me.d3sox.pdfwallet.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Certificate::class, Tag::class, CertificateTagCrossRef::class, DocumentPosition::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = AppDatabase.ManualOrderNewestFirst::class),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun certificateDao(): CertificateDao
    abstract fun tagDao(): TagDao

    /**
     * Starts the manual order like the default sort order, so switching to it doesn't shuffle the documents.
     */
    class ManualOrderNewestFirst : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                // the id orders documents imported in the same millisecond, so every position is unique
                "UPDATE certificates SET position = (SELECT COUNT(*) FROM certificates AS newer " +
                    "WHERE newer.importedAt > certificates.importedAt " +
                    "OR (newer.importedAt = certificates.importedAt AND newer.id < certificates.id))"
            )
        }
    }

    companion object {
        /**
         * Released databases have to keep their data. Every version bump needs an auto migration
         * or a migration added here, AppDatabaseMigrationTest fails otherwise.
         */
        fun build(context: Context, name: String = "greenpass"): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name).build()
    }
}
