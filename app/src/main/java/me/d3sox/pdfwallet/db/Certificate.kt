package me.d3sox.pdfwallet.db

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
     * Import timestamp in epoch millis
     */
    @ColumnInfo(name = "importedAt") val importedAt: Long,
    /**
     * Place in the manual sort order, lower values come first
     */
    @ColumnInfo(name = "position", defaultValue = "0") val position: Int = 0,
    /**
     * Archived documents are only shown in the archive
     */
    @ColumnInfo(name = "archived", defaultValue = "0") val archived: Boolean = false,
    /**
     * When the document gets archived automatically, in epoch millis, or null
     */
    @ColumnInfo(name = "archiveAt") val archiveAt: Long? = null,
    /**
     * The user's note, e.g. a seat or booking number, empty if there is none
     */
    @ColumnInfo(name = "note", defaultValue = "") val note: String = "",
    /**
     * The day of the user's reminder, in epoch days, or null if there is none
     */
    @ColumnInfo(name = "reminderDate") val reminderDate: Long? = null,
    /**
     * The time of the reminder, in minutes of the day, or null for the morning
     */
    @ColumnInfo(name = "reminderTime") val reminderTime: Int? = null,
)
