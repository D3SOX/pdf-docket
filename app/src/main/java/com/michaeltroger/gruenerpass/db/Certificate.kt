package com.michaeltroger.gruenerpass.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "certificates")
data class Certificate(
    /**
     * The name of the internally stored pdf file
     */
    @PrimaryKey val id: String,
    /**
     * The user defined und user facing document name
     */
    @ColumnInfo(name = "name") val name: String,
    /**
     * Import timestamp in epoch millis. Documents imported before this column existed
     * carry their former manual display order instead, which keeps their relative order.
     */
    @ColumnInfo(name = "importedAt", defaultValue = "0") val importedAt: Long,
)
