package me.d3sox.pdfwallet.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The name of a [me.d3sox.pdfwallet.documents.tags.TagIcon], or null for none */
    val icon: String? = null,
)
