package com.michaeltroger.gruenerpass.documents

import com.michaeltroger.gruenerpass.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Document and tag modifications, shared by the list and the detail screen.
 */
class DocumentEditor @Inject constructor(
    private val repository: DocumentRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun rename(id: String, name: String) = scope.launch { repository.rename(id, name) }

    fun delete(ids: List<String>) = scope.launch { repository.delete(ids) }

    fun setTags(id: String, tagIds: Set<Long>) = scope.launch { repository.setTags(id, tagIds) }

    fun createTag(name: String, assignTo: Document? = null) = scope.launch {
        val tagId = repository.createTag(name)
        if (assignTo != null && tagId > 0) {
            repository.setTags(assignTo.id, assignTo.tags.map { it.id }.toSet() + tagId)
        }
    }

    fun renameTag(id: Long, name: String) = scope.launch { repository.renameTag(id, name) }

    fun deleteTag(id: Long) = scope.launch { repository.deleteTag(id) }
}
