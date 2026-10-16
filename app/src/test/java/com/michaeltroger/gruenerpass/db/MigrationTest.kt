package com.michaeltroger.gruenerpass.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val DB_NAME = "migration-test"

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate3To4_keepsDocumentsOrderAndTags() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL("INSERT INTO certificates (id, name, displayOrder) VALUES ('a.pdf', 'A', 1), ('b.pdf', 'B', 0)")
            db.execSQL("INSERT INTO tags (id, name) VALUES (1, 'Ticket')")
            db.execSQL("INSERT INTO CertificateTagCrossRef (certificateId, tagId) VALUES ('a.pdf', 1)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT id, name, importedAt FROM certificates ORDER BY importedAt").use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0) shouldBe "b.pdf"
                cursor.moveToNext()
                cursor.getString(0) shouldBe "a.pdf"
                cursor.getString(1) shouldBe "A"
                cursor.getLong(2) shouldBe 1
            }
            db.query("SELECT certificateId, tagId FROM CertificateTagCrossRef").use { cursor ->
                cursor.count shouldBe 1
                cursor.moveToFirst()
                cursor.getString(0) shouldBe "a.pdf"
            }
        }
    }
}
