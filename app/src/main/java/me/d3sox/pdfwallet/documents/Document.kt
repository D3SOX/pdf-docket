package me.d3sox.pdfwallet.documents

import me.d3sox.pdfwallet.db.Tag

data class Document(
    /** name of the internally stored PDF file */
    val id: String,
    val name: String,
    val importedAt: Long,
    /** place in the manual sort order */
    val position: Int,
    val sizeBytes: Long,
    val tags: List<Tag>,
    val archived: Boolean = false,
    /** when the document gets archived automatically, in epoch millis */
    val archiveAt: Long? = null,
)
