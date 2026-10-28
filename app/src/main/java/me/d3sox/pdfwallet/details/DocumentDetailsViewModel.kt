package me.d3sox.pdfwallet.details

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.d3sox.pdfwallet.db.DocumentPosition
import me.d3sox.pdfwallet.documents.DocumentEditor
import me.d3sox.pdfwallet.documents.DocumentFilter
import me.d3sox.pdfwallet.documents.DocumentList
import me.d3sox.pdfwallet.render.BarcodeScan
import me.d3sox.pdfwallet.render.DocumentRenderer
import me.d3sox.pdfwallet.settings.AppSettings
import me.d3sox.pdfwallet.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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
    private val positions: DocumentPositions,
    val editor: DocumentEditor,
) : ViewModel() {

    /** null while loading */
    val documentList: StateFlow<DocumentList?> = filter.documentList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun scanBarcodes(id: String, settings: AppSettings): Flow<BarcodeScan> =
        renderer.scanBarcodes(id, settings.barcodeSearchMode, settings.generateNewBarcode)

    suspend fun page(id: String, pageIndex: Int): Bitmap? = renderer.page(id, pageIndex)

    suspend fun position(id: String): DocumentPosition? = positions.get(id)

    fun savePosition(id: String, barcode: Int, page: Int) = positions.save(id, barcode, page)

    fun onToggleHalfSizeBarcodes() = viewModelScope.launch {
        settingsRepository.update { it.copy(halfSizeBarcodes = !it.halfSizeBarcodes) }
    }

    fun onSplitScreenChange(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { it.copy(splitScreen = enabled) }
    }

    @AssistedFactory
    interface Factory {
        fun create(initialDocumentId: String): DocumentDetailsViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
