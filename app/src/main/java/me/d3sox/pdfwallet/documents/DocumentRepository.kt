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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
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
                    position = it.certificate.position,
                    sizeBytes = file(it.certificate.id).length(),
                    tags = it.tags.sortedBy { tag -> tag.name.lowercase() },
                    archived = it.certificate.archived,
                    archiveAt = it.certificate.archiveAt,
                )
            }
        }
        .flowOn(dispatcher)

    val tags: Flow<List<Tag>> = tagDao.getAll()

    fun file(id: String): File = File(context.filesDir, id)

    /**
     * Adds the document at the top of the manual order, unless one with the same content is already there,
     * e.g. when a ticket is shared twice.
     * Then the file of the pending document is deleted, the existing one is taken out of the archive
     * and its id is returned.
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
                    position = (certificateDao.minPosition() ?: 0) - 1,
                )
            )
        } else {
            certificateDao.unarchive(listOf(duplicate))
        }
        return duplicate
    }

    /** null if there is no such document */
    suspend fun isArchived(id: String): Boolean? = certificateDao.isArchived(id)

    suspend fun rename(id: String, name: String) = certificateDao.updateName(id, name)

    /** @see CertificateDao.reorder */
    suspend fun reorder(ids: List<String>) = certificateDao.reorder(ids)

    /** Also cancels their automatic archiving */
    suspend fun archive(ids: List<String>) = certificateDao.archive(ids)

    suspend fun unarchive(ids: List<String>) = certificateDao.unarchive(ids)

    /** [archiveAt] in epoch millis, or null to keep the document */
    suspend fun setArchiveAt(id: String, archiveAt: Long?) = certificateDao.setArchiveAt(id, archiveAt)

    /**
     * Archives the documents whose archive date has passed, and then each one when its date is reached,
     * until cancelled.
     */
    suspend fun archiveWhenDue() = certificateDao.nextArchiveAt().collectLatest { archiveAt ->
        archiveAt ?: return@collectLatest
        delay(archiveAt - System.currentTimeMillis())
        certificateDao.archiveDue(System.currentTimeMillis())
    }

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
