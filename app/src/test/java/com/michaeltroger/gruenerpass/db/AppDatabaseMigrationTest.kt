package com.michaeltroger.gruenerpass.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val DATABASE_NAME = "migration-test"
// from schemas/.../1.json of version 1.1.1, the first release
private const val RELEASED_VERSION_1_HASH = "06a258ce39d2ffdfc2882c3d49f0e9f7"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    /**
     * Room regenerates the schema file of the current version, so changing an entity without a version bump
     * would silently replace the released schema the other test starts from.
     */
    @Test
    fun releasedSchema_isUnchanged() {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("${AppDatabase::class.java.name}/1.json")
            .use { JSONObject(it.reader().readText()) }
        schema.getJSONObject("database").getString("identityHash") shouldBe RELEASED_VERSION_1_HASH
    }

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
