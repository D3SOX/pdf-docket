package me.d3sox.pdfwallet.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CertificateDao {
    @Transaction
    @Query("SELECT * FROM certificates")
    fun getAllWithTags(): Flow<List<CertificateWithTags>>

    @Query("SELECT id FROM certificates")
    suspend fun ids(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM certificates WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(certificate: Certificate)

    @Query("UPDATE certificates SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("DELETE FROM certificates WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("SELECT * FROM document_positions WHERE certificateId = :id")
    suspend fun position(id: String): DocumentPosition?

    @Upsert
    suspend fun savePosition(position: DocumentPosition)
}
