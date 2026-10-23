package com.michaeltroger.gruenerpass.backup

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.michaeltroger.gruenerpass.coroutines.dispatcher.di.CoroutineDispatchersModule
import com.michaeltroger.gruenerpass.db.CertificateDao
import com.michaeltroger.gruenerpass.db.di.DatabaseModule
import com.michaeltroger.gruenerpass.documents.Document
import com.michaeltroger.gruenerpass.documents.DocumentRepository
import com.michaeltroger.gruenerpass.pdfimporter.PendingCertificate
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.bouncycastle.crypto.io.InvalidCipherTextIOException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import javax.inject.Inject

private const val PASSWORD = "correct horse battery staple"

@HiltAndroidTest
@UninstallModules(DatabaseModule::class, CoroutineDispatchersModule::class)
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class BackupRepositoryTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val tempFolder = TemporaryFolder()

    @Inject lateinit var backupRepository: BackupRepository
    @Inject lateinit var documentRepository: DocumentRepository
    @Inject lateinit var certificateDao: CertificateDao
    @Inject @ApplicationContext lateinit var context: Context

    private lateinit var backup: File
    private val backupUri: Uri get() = Uri.fromFile(backup)

    @Before
    fun setUp() {
        hiltRule.inject()
        backup = tempFolder.root.resolve("backup.pdfwallet")
    }

    private suspend fun addDocument(id: String, name: String, tags: Set<String> = emptySet()) {
        documentRepository.file(id).writeText("content of $id")
        documentRepository.add(PendingCertificate(fileName = id, documentName = name))
        val tagIds = documentRepository.tags.first().filter { it.name in tags }.map { it.id }.toSet()
        documentRepository.setTags(id, tagIds)
    }

    private suspend fun deleteEverything() {
        documentRepository.delete(documentRepository.documents.first().map { it.id })
        documentRepository.tags.first().forEach { documentRepository.deleteTag(it.id) }
    }

    @Test
    fun restore_bringsBackDocumentsNamesDatesAndTags() = runTest {
        documentRepository.createTag("Travel")
        documentRepository.createTag("Unused")
        addDocument("a.pdf", name = "Train ticket", tags = setOf("Travel"))
        addDocument("b.pdf", name = "Certificate")
        val original = documentRepository.documents.first()

        backupRepository.export(backupUri, PASSWORD)
        deleteEverything()
        val restored = backupRepository.restore(backupUri, PASSWORD)

        restored shouldBe 2
        // restored tags get new ids
        fun List<Document>.withoutTagIds() = map { it.copy(tags = it.tags.map { tag -> tag.copy(id = 0) }) }
        documentRepository.documents.first().withoutTagIds() shouldContainExactly original.withoutTagIds()
        documentRepository.tags.first().map { it.name } shouldContainExactly listOf("Travel", "Unused")
        documentRepository.file("a.pdf").readText() shouldBe "content of a.pdf"
        // the decrypted copy is deleted again
        context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("restore") }.shouldBeEmpty()
    }

    @Test
    fun restore_skipsDocumentsThatAreStillInTheApp() = runTest {
        addDocument("a.pdf", name = "Ticket")
        backupRepository.export(backupUri, PASSWORD)
        documentRepository.rename("a.pdf", "Renamed")

        backupRepository.restore(backupUri, PASSWORD) shouldBe 0

        documentRepository.documents.first().single().name shouldBe "Renamed"
    }

    @Test
    fun restore_withWrongPassword_fails() = runTest {
        addDocument("a.pdf", name = "Ticket")
        backupRepository.export(backupUri, PASSWORD)
        deleteEverything()

        shouldThrow<InvalidCipherTextIOException> {
            backupRepository.restore(backupUri, "wrong")
        }
        documentRepository.documents.first().shouldBeEmpty()
    }

    @Test
    fun restore_ofTamperedBackup_fails() = runTest {
        addDocument("a.pdf", name = "Ticket")
        backupRepository.export(backupUri, PASSWORD)
        deleteEverything()
        val bytes = backup.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        backup.writeBytes(bytes)

        shouldThrow<InvalidCipherTextIOException> {
            backupRepository.restore(backupUri, PASSWORD)
        }
        documentRepository.documents.first().shouldBeEmpty()
    }

    @Test
    fun restore_ofOtherFile_failsAsNotABackup() = runTest {
        backup.writeText("%PDF-1.7 definitely not a backup")

        shouldThrow<NotABackupException> {
            backupRepository.restore(backupUri, PASSWORD)
        }
    }

    @Test
    fun restore_ofEmptyFile_failsAsNotABackup() = runTest {
        backup.writeBytes(ByteArray(0))

        shouldThrow<NotABackupException> {
            backupRepository.restore(backupUri, PASSWORD)
        }
    }

    @Test
    fun export_encryptsTheContent() = runTest {
        addDocument("a.pdf", name = "Secret ticket name")

        backupRepository.export(backupUri, PASSWORD)

        val content = backup.readBytes().toString(Charsets.ISO_8859_1)
        content.contains("Secret ticket name") shouldBe false
        content.contains("content of a.pdf") shouldBe false
        certificateDao.exists("a.pdf") shouldBe true
    }
}
