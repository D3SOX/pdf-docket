package com.michaeltroger.gruenerpass.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CertificateDao {
    @Transaction
    @Query("SELECT * FROM certificates")
    fun getAllWithTags(): Flow<List<CertificateWithTags>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(certificate: Certificate)

    @Query("UPDATE certificates SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("DELETE FROM certificates WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}
