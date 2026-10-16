package com.michaeltroger.gruenerpass.documents

import com.michaeltroger.gruenerpass.db.Tag

data class Document(
    /** name of the internally stored PDF file */
    val id: String,
    val name: String,
    val importedAt: Long,
    val sizeBytes: Long,
    val tags: List<Tag>,
)
