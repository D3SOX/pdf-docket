package me.d3sox.pdfwallet.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface CertificateDao {
    @Transaction
    @Query("SELECT * FROM certificates ORDER BY $MANUAL_ORDER")
    fun getAllWithTags(): Flow<List<CertificateWithTags>>

    @Query("SELECT id FROM certificates")
    suspend fun ids(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM certificates WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(certificate: Certificate)

    @Query("UPDATE certificates SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("SELECT MIN(position) FROM certificates")
    suspend fun minPosition(): Int?

    @Query("SELECT MAX(position) FROM certificates")
    suspend fun maxPosition(): Int?

    @Query("SELECT id FROM certificates ORDER BY $MANUAL_ORDER")
    suspend fun idsInManualOrder(): List<String>

    @Query("UPDATE certificates SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    /**
     * Puts the documents with the [reorderedIds] in their new order. They take the places they had among each other,
     * so documents that are filtered out keep theirs.
     */
    @Transaction
    suspend fun reorder(reorderedIds: List<String>) {
        val ids = idsInManualOrder()
        val reordered = reorderedIds.filter { it in ids }.iterator()
        val reorderedSet = reorderedIds.toSet()
        ids.map { if (it in reorderedSet) reordered.next() else it }
            .forEachIndexed { position, id -> updatePosition(id, position) }
    }

    /** null if there is no such document */
    @Query("SELECT archived FROM certificates WHERE id = :id")
    suspend fun isArchived(id: String): Boolean?

    /** Also cancels their automatic archiving */
    @Query("UPDATE certificates SET archived = 1, archiveAt = NULL WHERE id IN (:ids)")
    suspend fun archive(ids: List<String>)

    @Query("UPDATE certificates SET archived = 0 WHERE id IN (:ids)")
    suspend fun unarchive(ids: List<String>)

    @Query("UPDATE certificates SET archiveAt = :archiveAt WHERE id = :id")
    suspend fun setArchiveAt(id: String, archiveAt: Long?)

    /** The earliest time a document gets archived automatically, or null if none does */
    @Query("SELECT MIN(archiveAt) FROM certificates")
    fun nextArchiveAt(): Flow<Long?>

    @Query("UPDATE certificates SET archived = 1, archiveAt = NULL WHERE archiveAt <= :now")
    suspend fun archiveDue(now: Long)

    @Query("DELETE FROM certificates WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("SELECT * FROM document_positions WHERE certificateId = :id")
    suspend fun position(id: String): DocumentPosition?

    @Upsert
    suspend fun savePosition(position: DocumentPosition)
}

private const val MANUAL_ORDER = "position ASC, importedAt DESC"
