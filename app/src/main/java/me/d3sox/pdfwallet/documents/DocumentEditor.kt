package me.d3sox.pdfwallet.documents

import android.content.Context
import me.d3sox.pdfwallet.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Document and tag modifications, shared by the list and the detail screen.
 */
class DocumentEditor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun rename(id: String, name: String) = scope.launch {
        repository.rename(id, name)
        context.renameShortcut(id, name)
    }

    fun delete(ids: List<String>) = scope.launch {
        repository.delete(ids)
        context.disableShortcuts(ids)
    }

    fun setTags(id: String, tagIds: Set<Long>) = scope.launch { repository.setTags(id, tagIds) }

    fun updateTags(ids: List<String>, addedTagIds: Set<Long>, removedTagIds: Set<Long>) = scope.launch {
        repository.updateTags(ids, addedTagIds, removedTagIds)
    }

    fun createTag(name: String, assignTo: Document? = null) = scope.launch {
        val tagId = repository.createTag(name)
        if (assignTo != null && tagId > 0) {
            repository.setTags(assignTo.id, assignTo.tags.map { it.id }.toSet() + tagId)
        }
    }

    fun renameTag(id: Long, name: String) = scope.launch { repository.renameTag(id, name) }

    fun deleteTag(id: Long) = scope.launch { repository.deleteTag(id) }
}
