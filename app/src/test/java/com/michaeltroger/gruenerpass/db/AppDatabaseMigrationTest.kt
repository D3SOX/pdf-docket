package com.michaeltroger.gruenerpass.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val DATABASE_NAME = "migration-test"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    /**
     * Opening a database of the first release with the current database fails if a version bump lacks a migration.
     */
    @Test
    fun firstReleasedVersion_keepsItsDataWhenOpened() = runTest {
        helper.createDatabase(DATABASE_NAME, 1).use { db ->
            db.execSQL("INSERT INTO certificates (id, name, importedAt) VALUES ('a.pdf', 'Ticket', 1)")
            db.execSQL("INSERT INTO tags (id, name) VALUES (1, 'Travel')")
            db.execSQL("INSERT INTO CertificateTagCrossRef (certificateId, tagId) VALUES ('a.pdf', 1)")
        }

        val database = AppDatabase.build(InstrumentationRegistry.getInstrumentation().targetContext, DATABASE_NAME)
        try {
            database.certificateDao().getAllWithTags().first() shouldContainExactly listOf(
                CertificateWithTags(Certificate("a.pdf", "Ticket", 1), listOf(Tag(1, "Travel"))),
            )
        } finally {
            database.close()
        }
    }
}
