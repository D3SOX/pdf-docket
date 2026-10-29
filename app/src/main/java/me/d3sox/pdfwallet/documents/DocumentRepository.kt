package me.d3sox.pdfwallet.documents

import android.content.Context
import me.d3sox.pdfwallet.coroutines.dispatcher.di.IoDispatcher
import me.d3sox.pdfwallet.db.Certificate
import me.d3sox.pdfwallet.db.CertificateDao
import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.db.TagDao
import me.d3sox.pdfwallet.pdfimporter.PendingCertificate
import me.d3sox.pdfwallet.render.BarcodeScanStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val certificateDao: CertificateDao,
    private val tagDao: TagDao,
    private val scanStore: BarcodeScanStore,
) {
    val documents: Flow<List<Document>> = certificateDao.getAllWithTags()
        .map { list ->
            list.map {
                Document(
                    id = it.certificate.id,
                    name = it.certificate.name,
                    importedAt = it.certificate.importedAt,
                    sizeBytes = file(it.certificate.id).length(),
                    tags = it.tags.sortedBy { tag -> tag.name.lowercase() },
                )
            }
        }
        .flowOn(dispatcher)

    val tags: Flow<List<Tag>> = tagDao.getAll()

    fun file(id: String): File = File(context.filesDir, id)

    /**
     * Adds the document, unless one with the same content is already there, e.g. when a ticket is shared twice.
     * Then the file of the pending document is deleted and the id of the existing one is returned.
     */
    suspend fun add(pending: PendingCertificate): String? {
        val ids = certificateDao.ids()
        val duplicate = withContext(dispatcher) {
            val file = file(pending.fileName)
            val digest by lazy { file.sha256() }
            ids.firstOrNull { file(it).length() == file.length() && file(it).sha256().contentEquals(digest) }
                ?.also { file.delete() }
        }
        if (duplicate == null) {
            certificateDao.insert(
                Certificate(
                    id = pending.fileName,
                    name = pending.documentName,
                    importedAt = System.currentTimeMillis(),
                )
            )
        }
        return duplicate
    }

    suspend fun exists(id: String): Boolean = certificateDao.exists(id)

    suspend fun rename(id: String, name: String) = certificateDao.updateName(id, name)

    suspend fun delete(ids: List<String>) {
        certificateDao.delete(ids)
        withContext(dispatcher) {
            ids.forEach { file(it).delete() }
        }
        scanStore.delete(ids)
    }

    suspend fun setTags(id: String, tagIds: Set<Long>) = tagDao.setTags(id, tagIds)

    suspend fun updateTags(ids: List<String>, addedTagIds: Set<Long>, removedTagIds: Set<Long>) =
        tagDao.updateTags(ids, addedTagIds, removedTagIds)

    suspend fun createTag(name: String, icon: String? = null): Long = tagDao.insert(Tag(name = name, icon = icon))

    suspend fun updateTag(tag: Tag) = tagDao.update(tag)

    suspend fun deleteTag(id: Long) = tagDao.delete(id)
}

private fun File.sha256(): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest()
}
