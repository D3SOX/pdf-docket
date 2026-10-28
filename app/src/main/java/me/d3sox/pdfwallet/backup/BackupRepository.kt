package me.d3sox.pdfwallet.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import me.d3sox.pdfwallet.coroutines.dispatcher.di.IoDispatcher
import me.d3sox.pdfwallet.db.Certificate
import me.d3sox.pdfwallet.db.CertificateDao
import me.d3sox.pdfwallet.db.CertificateTagCrossRef
import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.db.TagDao
import me.d3sox.pdfwallet.documents.Document
import me.d3sox.pdfwallet.documents.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val METADATA_ENTRY = "backup.json"
private const val DOCUMENTS_DIRECTORY = "documents/"
// the names of imported files, which also keeps entries from writing outside of the app's files
private val DOCUMENT_ID = Regex("[0-9a-f-]+\\.pdf")

/**
 * Backups are encrypted ZIP files with the PDFs and a JSON file with their names, import dates and tags.
 * Tag icons are stored separately, so older versions can still restore newer backups.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val documentRepository: DocumentRepository,
    private val certificateDao: CertificateDao,
    private val tagDao: TagDao,
) {

    suspend fun export(uri: Uri, password: String): Unit = withContext(dispatcher) {
        val documents = documentRepository.documents.first()
        val tags = documentRepository.tags.first()
        try {
            val output = context.contentResolver.openOutputStream(uri) ?: throw FileNotFoundException(uri.toString())
            ZipOutputStream(BackupCipher.encrypt(output.buffered(), password)).use { zip ->
                zip.putNextEntry(ZipEntry(METADATA_ENTRY))
                zip.write(metadata(documents, tags).toString().toByteArray())
                zip.closeEntry()
                documents.forEach { document ->
                    zip.putNextEntry(ZipEntry(DOCUMENTS_DIRECTORY + document.id))
                    documentRepository.file(document.id).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } catch (e: IOException) {
            discard(uri)
            throw e
        }
    }

    /**
     * Adds the documents of the backup that aren't in the app yet. Returns how many were added.
     *
     * @throws NotABackupException if the file isn't a backup
     * @throws org.bouncycastle.crypto.io.InvalidCipherTextIOException if the password is wrong
     */
    suspend fun restore(uri: Uri, password: String): Int = withContext(dispatcher) {
        // the content can only be trusted once it was decrypted completely
        val decrypted = File.createTempFile("restore", ".zip", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
            BackupCipher.decrypt(input.buffered(), password).use { content ->
                decrypted.outputStream().use { content.copyTo(it) }
            }
            ZipFile(decrypted).use { restore(it) }
        } finally {
            decrypted.delete()
        }
    }

    /** Deletes a file that was created for a backup that couldn't be written. */
    suspend fun discard(uri: Uri): Unit = withContext(dispatcher) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    private suspend fun restore(zip: ZipFile): Int {
        val metadata = zip.getEntry(METADATA_ENTRY)
            ?.let { entry -> zip.getInputStream(entry).use { JSONObject(it.reader().readText()) } }
            ?: throw NotABackupException()
        // added in 1.5.0
        val tagIcons = metadata.optJSONObject("tagIcons")
        metadata.getJSONArray("tags").strings().forEach { name ->
            tagDao.insert(Tag(name = name, icon = tagIcons?.optString(name)?.takeIf { it.isNotEmpty() }))
        }
        val tagIds = tagDao.getAll().first().associate { it.name to it.id }

        var restored = 0
        metadata.getJSONArray("documents").objects().forEach { json ->
            val id = json.getString("id")
            val entry = zip.getEntry(DOCUMENTS_DIRECTORY + id)
            if (!DOCUMENT_ID.matches(id) || entry == null || certificateDao.exists(id)) return@forEach
            zip.getInputStream(entry).use { input ->
                documentRepository.file(id).outputStream().use { input.copyTo(it) }
            }
            certificateDao.insert(
                Certificate(id = id, name = json.getString("name"), importedAt = json.getLong("importedAt"))
            )
            tagDao.insertCrossRefs(
                json.getJSONArray("tags").strings().mapNotNull(tagIds::get).map { CertificateTagCrossRef(id, it) }
            )
            restored++
        }
        return restored
    }

    private fun metadata(documents: List<Document>, tags: List<Tag>) = JSONObject()
        .put("tags", JSONArray(tags.map { it.name }))
        .put("tagIcons", JSONObject(tags.mapNotNull { tag -> tag.icon?.let { tag.name to it } }.toMap()))
        .put(
            "documents",
            JSONArray(
                documents.map { document ->
                    JSONObject()
                        .put("id", document.id)
                        .put("name", document.name)
                        .put("importedAt", document.importedAt)
                        .put("tags", JSONArray(document.tags.map { it.name }))
                }
            ),
        )
}

private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
