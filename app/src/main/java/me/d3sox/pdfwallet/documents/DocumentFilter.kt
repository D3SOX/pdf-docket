package me.d3sox.pdfwallet.documents

import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.settings.AppSettings
import me.d3sox.pdfwallet.settings.SettingsRepository
import me.d3sox.pdfwallet.settings.SortOrder
import me.d3sox.pdfwallet.settings.TagFilterMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class DocumentList(
    /** the documents of the list or the archive */
    val all: List<Document>,
    val visible: List<Document>,
    val tags: List<Tag>,
    val query: String,
    val selectedTagIds: Set<Long>,
    /** whether [all] are the archived documents */
    val archive: Boolean,
) {
    val isFiltered: Boolean get() = visible.size != all.size
}

/**
 * Holds the search and tag filter of the current session, and whether the archive is shown.
 * It is shared, so the document pager shows the same documents in the same order as the list.
 */
@Singleton
class DocumentFilter @Inject constructor(
    private val repository: DocumentRepository,
    settingsRepository: SettingsRepository,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())

    private val archive = MutableStateFlow(false)

    val documentList: Flow<DocumentList> = combine(
        repository.documents,
        repository.tags,
        settingsRepository.settings,
        _query,
        combine(selectedTagIds, archive, ::Pair),
    ) { allDocuments, tags, settings, query, (selectedTagIds, archive) ->
        val existingSelection = selectedTagIds.filterTo(mutableSetOf()) { id -> tags.any { it.id == id } }
        val documents = allDocuments.filter { it.archived == archive }
        DocumentList(
            all = documents,
            visible = documents
                .filter { it.matches(query.trim(), existingSelection, settings) }
                .sortedWith(settings.sortOrder.comparator),
            tags = tags,
            query = query,
            selectedTagIds = existingSelection,
            archive = archive,
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

    /** Clears the filters and shows the archived documents if [archive], the others otherwise */
    fun show(archive: Boolean) {
        clear()
        this.archive.value = archive
    }

    /** Shows the archive if the document with [id] is archived, the other documents otherwise, and keeps the filters */
    suspend fun showArchiveOf(id: String) {
        repository.isArchived(id)?.let { archive.value = it }
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
            SortOrder.MANUAL -> compareBy<Document> { it.position }.thenByDescending { it.importedAt }
        }
    }
