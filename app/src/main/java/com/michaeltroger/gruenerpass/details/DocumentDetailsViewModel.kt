package com.michaeltroger.gruenerpass.details

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaeltroger.gruenerpass.documents.DocumentEditor
import com.michaeltroger.gruenerpass.documents.DocumentFilter
import com.michaeltroger.gruenerpass.documents.DocumentList
import com.michaeltroger.gruenerpass.render.BarcodeScan
import com.michaeltroger.gruenerpass.render.DocumentRenderer
import com.michaeltroger.gruenerpass.settings.AppSettings
import com.michaeltroger.gruenerpass.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = DocumentDetailsViewModel.Factory::class)
class DocumentDetailsViewModel @AssistedInject constructor(
    @Assisted val initialDocumentId: String,
    filter: DocumentFilter,
    private val renderer: DocumentRenderer,
    private val settingsRepository: SettingsRepository,
    val editor: DocumentEditor,
) : ViewModel() {

    /** null while loading */
    val documentList: StateFlow<DocumentList?> = filter.documentList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    suspend fun scanBarcodes(id: String, settings: AppSettings): BarcodeScan =
        renderer.scanBarcodes(id, settings.barcodeSearchMode, settings.generateNewBarcode)

    suspend fun page(id: String, pageIndex: Int): Bitmap? = renderer.page(id, pageIndex)

    fun onToggleHalfSizeBarcodes() = viewModelScope.launch {
        settingsRepository.update { it.copy(halfSizeBarcodes = !it.halfSizeBarcodes) }
    }

    @AssistedFactory
    interface Factory {
        fun create(initialDocumentId: String): DocumentDetailsViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
