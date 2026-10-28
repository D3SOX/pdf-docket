package me.d3sox.pdfwallet.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Where a document was last shown, so it opens there again.
 */
@Entity(
    tableName = "document_positions",
    foreignKeys = [
        ForeignKey(
            entity = Certificate::class,
            parentColumns = ["id"],
            childColumns = ["certificateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DocumentPosition(
    @PrimaryKey val certificateId: String,
    /** index of the barcode */
    val barcode: Int,
    /** index of the PDF page */
    val page: Int,
)
