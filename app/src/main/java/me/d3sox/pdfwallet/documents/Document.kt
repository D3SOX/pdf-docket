package me.d3sox.pdfwallet.documents

import me.d3sox.pdfwallet.db.Tag

data class Document(
    /** name of the internally stored PDF file */
    val id: String,
    val name: String,
    val importedAt: Long,
    val sizeBytes: Long,
    val tags: List<Tag>,
)
