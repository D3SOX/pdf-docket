package com.michaeltroger.gruenerpass.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber")
@Database(
    entities = [Certificate::class, Tag::class, CertificateTagCrossRef::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun certificateDao(): CertificateDao
    abstract fun tagDao(): TagDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE certificates ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE certificates SET displayOrder = rowid")
            }
        }

        /**
         * Replaces the manual display order with an import timestamp.
         * Foreign keys are not enforced while migrating, so recreating the table keeps tag assignments.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `certificates_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`importedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO certificates_new (id, name, importedAt) " +
                        "SELECT id, name, displayOrder FROM certificates"
                )
                db.execSQL("DROP TABLE certificates")
                db.execSQL("ALTER TABLE certificates_new RENAME TO certificates")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_CertificateTagCrossRef_tagId` " +
                        "ON `CertificateTagCrossRef` (`tagId`)"
                )
            }
        }

    }
}
