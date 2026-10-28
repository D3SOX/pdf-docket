package me.d3sox.pdfwallet.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    /** Keeps the tag unchanged if its new name is already taken */
    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun update(tag: Tag)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(crossRefs: List<CertificateTagCrossRef>)

    @Query("DELETE FROM CertificateTagCrossRef WHERE certificateId = :certificateId")
    suspend fun deleteCrossRefsForCertificate(certificateId: String)

    @Transaction
    suspend fun setTags(certificateId: String, tagIds: Collection<Long>) {
        deleteCrossRefsForCertificate(certificateId)
        insertCrossRefs(tagIds.map { CertificateTagCrossRef(certificateId, it) })
    }

    @Query("DELETE FROM CertificateTagCrossRef WHERE certificateId IN (:certificateIds) AND tagId IN (:tagIds)")
    suspend fun deleteTags(certificateIds: List<String>, tagIds: Set<Long>)

    @Transaction
    suspend fun updateTags(certificateIds: List<String>, addedTagIds: Set<Long>, removedTagIds: Set<Long>) {
        deleteTags(certificateIds, removedTagIds)
        insertCrossRefs(certificateIds.flatMap { id -> addedTagIds.map { CertificateTagCrossRef(id, it) } })
    }
}
