package com.michaeltroger.gruenerpass.documents

import com.michaeltroger.gruenerpass.db.Tag
import com.michaeltroger.gruenerpass.settings.AppSettings
import com.michaeltroger.gruenerpass.settings.SettingsRepository
import com.michaeltroger.gruenerpass.settings.SortOrder
import com.michaeltroger.gruenerpass.settings.TagFilterMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class DocumentList(
    val all: List<Document>,
    val visible: List<Document>,
    val tags: List<Tag>,
    val query: String,
    val selectedTagIds: Set<Long>,
) {
    val isFiltered: Boolean get() = visible.size != all.size
}

/**
 * Holds the search and tag filter of the current session.
 * It is shared, so the document pager shows the same documents in the same order as the list.
 */
@Singleton
class DocumentFilter @Inject constructor(
    repository: DocumentRepository,
    settingsRepository: SettingsRepository,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())

    val documentList: Flow<DocumentList> = combine(
        repository.documents,
        repository.tags,
        settingsRepository.settings,
        _query,
        selectedTagIds,
    ) { documents, tags, settings, query, selectedTagIds ->
        val existingSelection = selectedTagIds.filterTo(mutableSetOf()) { id -> tags.any { it.id == id } }
        DocumentList(
            all = documents,
            visible = documents
                .filter { it.matches(query.trim(), existingSelection, settings) }
                .sortedWith(settings.sortOrder.comparator),
            tags = tags,
            query = query,
            selectedTagIds = existingSelection,
        )
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun toggleTag(id: Long) {
        selectedTagIds.update { if (id in it) it - id else it + id }
    }

    fun clear() {
        _query.value = ""
        selectedTagIds.value = emptySet()
    }

    private fun Document.matches(query: String, tagIds: Set<Long>, settings: AppSettings): Boolean {
        val matchesQuery = query.isEmpty() || name.contains(query, ignoreCase = true)
        val ownTagIds = tags.map { it.id }
        val matchesTags = tagIds.isEmpty() || when (settings.tagFilterMode) {
            TagFilterMode.ALL -> ownTagIds.containsAll(tagIds)
            TagFilterMode.ANY -> tagIds.any { it in ownTagIds }
        }
        return matchesQuery && matchesTags
    }
}

private val SortOrder.comparator: Comparator<Document>
    get() {
        val byName = compareBy<Document, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        return when (this) {
            SortOrder.IMPORT_DATE_DESC -> compareByDescending { it.importedAt }
            SortOrder.IMPORT_DATE_ASC -> compareBy { it.importedAt }
            SortOrder.NAME_ASC -> byName
            SortOrder.NAME_DESC -> byName.reversed()
            SortOrder.SIZE_DESC -> compareByDescending { it.sizeBytes }
            SortOrder.SIZE_ASC -> compareBy { it.sizeBytes }
        }
    }
