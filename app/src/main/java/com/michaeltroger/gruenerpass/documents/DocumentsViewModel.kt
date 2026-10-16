package com.michaeltroger.gruenerpass.documents

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaeltroger.gruenerpass.lock.AppLockedRepo
import com.michaeltroger.gruenerpass.render.DocumentRenderer
import com.michaeltroger.gruenerpass.settings.SettingsRepository
import com.michaeltroger.gruenerpass.settings.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val filter: DocumentFilter,
    private val settingsRepository: SettingsRepository,
    private val lockedRepo: AppLockedRepo,
    private val renderer: DocumentRenderer,
    val editor: DocumentEditor,
) : ViewModel() {

    /** null while loading */
    val documentList: StateFlow<DocumentList?> = filter.documentList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val query: StateFlow<String> = filter.query

    fun onQueryChange(query: String) = filter.setQuery(query)

    fun onToggleTag(id: Long) = filter.toggleTag(id)

    fun onClearFilters() = filter.clear()

    fun onSortOrderSelected(sortOrder: SortOrder) = viewModelScope.launch {
        settingsRepository.update { it.copy(sortOrder = sortOrder) }
    }

    fun onLock() = lockedRepo.lockApp()

    suspend fun thumbnail(id: String): Bitmap? = renderer.thumbnail(id)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
