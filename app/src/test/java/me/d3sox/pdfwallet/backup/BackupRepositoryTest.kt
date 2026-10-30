package me.d3sox.pdfwallet.backup

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.d3sox.pdfwallet.coroutines.dispatcher.di.CoroutineDispatchersModule
import me.d3sox.pdfwallet.db.CertificateDao
import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.db.di.DatabaseModule
import me.d3sox.pdfwallet.documents.Document
import me.d3sox.pdfwallet.documents.DocumentRepository
import me.d3sox.pdfwallet.documents.tags.TagIcon
import me.d3sox.pdfwallet.pdfimporter.PendingCertificate
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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
        documentRepository.updateTag(Tag(documentRepository.createTag("Travel"), "Travel", TagIcon.TRAIN.name))
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
        documentRepository.tags.first().map { it.name to it.icon } shouldContainExactly listOf(
            "Travel" to "TRAIN",
            "Unused" to null,
        )
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
    fun restore_failingBetweenADocumentAndItsTags_keepsNeither() = runTest {
        // the tags of the document can't be read, like an interruption right after adding the document
        ZipOutputStream(BackupCipher.encrypt(backup.outputStream(), PASSWORD)).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            val document = JSONObject().put("id", "a.pdf").put("name", "Ticket").put("importedAt", 1L)
                .put("tags", "not a list")
            val metadata = JSONObject().put("tags", JSONArray()).put("documents", JSONArray().put(document))
            zip.write(metadata.toString().toByteArray())
            zip.putNextEntry(ZipEntry("documents/a.pdf"))
            zip.write("content of a.pdf".toByteArray())
        }

        shouldThrow<JSONException> {
            backupRepository.restore(backupUri, PASSWORD)
        }
        // so restoring again doesn't skip it
        certificateDao.exists("a.pdf") shouldBe false
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
    fun restore_withScryptParametersNeedingTooMuchMemory_failsAsNotABackup() = runTest {
        backupRepository.export(backupUri, PASSWORD)
        val bytes = backup.readBytes()
        // log2(N) and r follow the 9 byte magic and the format version, 2 GiB together
        bytes[10] = 20
        bytes[11] = 16
        backup.writeBytes(bytes)

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
