package me.d3sox.pdfwallet.render

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import me.d3sox.pdfwallet.barcode.BarcodeRenderer
import me.d3sox.pdfwallet.pdfrenderer.PdfRenderer
import me.d3sox.pdfwallet.pdfrenderer.PdfRendererBuilder
import me.d3sox.pdfwallet.settings.BarcodeSearchMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val THUMBNAIL_WIDTH_PX = 512

private fun thumbnailKey(id: String) = "thumb-$id"

private fun pageKey(id: String, pageIndex: Int) = "page-$id-$pageIndex"

data class PageBarcode(val pageIndex: Int, val bitmap: Bitmap)

/**
 * [complete] is false while later pages are still being scanned.
 */
data class BarcodeScan(val pageCount: Int, val barcodes: List<PageBarcode>, val complete: Boolean = true) {
    val hasSeveralBarcodesPerPage: Boolean
        get() = barcodes.groupingBy { it.pageIndex }.eachCount().any { it.value > 1 }
}

/**
 * Renders PDF pages, thumbnails and barcodes. Results are cached in memory, since the files never change,
 * and barcode scans are also kept on disk by [BarcodeScanStore].
 */
@Singleton
class DocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val barcodeRenderer: BarcodeRenderer,
    private val scanStore: BarcodeScanStore,
) {
    // Android's PdfRenderer isn't meant to be used concurrently
    private val renderDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val cache = BitmapCache.memoryCache
    private val scans = BitmapCache.scans
    private val scanMutex = Mutex()
    // never full, so no saved scan is missed while the widgets are being updated
    private val savedScanIds = MutableSharedFlow<String>(extraBufferCapacity = Channel.UNLIMITED)

    /** The ids of documents whose barcodes were found on every page, once their scan is saved */
    val savedScans: SharedFlow<String> = savedScanIds.asSharedFlow()

    // only accessed on the render dispatcher
    private val renderers = mutableMapOf<String, OpenRenderer>()

    // the longer side, so pages are as sharp in landscape and pages cached in portrait can be used there as well
    private val maxPageWidth: Int
        get() = context.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }

    suspend fun thumbnail(id: String): Bitmap? = cached(thumbnailKey(id)) {
        withRenderer(id) { renderer ->
            renderer.renderPage(pageIndex = 0, highResolution = false, maxWidth = THUMBNAIL_WIDTH_PX)
        }
    }

    /** The thumbnail if it's already rendered, to show it without waiting a frame for [thumbnail] */
    fun cachedThumbnail(id: String): Bitmap? = cache.get(thumbnailKey(id))

    suspend fun page(id: String, pageIndex: Int): Bitmap? = cached(pageKey(id, pageIndex)) {
        withRenderer(id) { renderer ->
            renderer.renderPage(pageIndex = pageIndex, highResolution = false, maxWidth = maxPageWidth)
        }
    }

    suspend fun pageCount(id: String): Int = withRenderer(id) { it.getPageCount() }

    /**
     * Searches every page for a barcode and emits the barcodes found so far after each page, so the first ones
     * show up before long documents are fully scanned. Pages rendered along the way are cached for the PDF view.
     */
    fun scanBarcodes(id: String, mode: BarcodeSearchMode, generateNewBarcode: Boolean): Flow<BarcodeScan> = flow {
        if (mode == BarcodeSearchMode.DISABLED) {
            emit(BarcodeScan(pageCount(id), emptyList()))
            return@flow
        }
        val settingsKey = "$mode-$generateNewBarcode"
        val key = "$id-$settingsKey"
        scanMutex.withLock {
            val scan = savedScan(id, mode, generateNewBarcode)
                ?: withRenderer(id) { renderer ->
                    val pageCount = renderer.getPageCount()
                    val barcodes = mutableListOf<PageBarcode>()
                    var allPagesScanned = pageCount > 0
                    for (pageIndex in 0 until pageCount) {
                        val page = renderer.renderPage(
                            pageIndex = pageIndex,
                            highResolution = mode == BarcodeSearchMode.EXTENDED,
                        )
                        if (page == null) {
                            allPagesScanned = false
                            continue
                        }
                        val found = barcodeRenderer.getBarcodes(
                            document = page,
                            tryExtraHard = mode == BarcodeSearchMode.EXTENDED,
                            generateNewBarcode = generateNewBarcode,
                        )
                        if (!found.complete) allPagesScanned = false
                        barcodes += found.bitmaps.map { PageBarcode(pageIndex, it) }
                        if (cache.get(pageKey(id, pageIndex)) == null) {
                            cache.put(pageKey(id, pageIndex), page.scaleToWidth(maxPageWidth))
                        }
                        emit(BarcodeScan(pageCount, barcodes.toList(), complete = false))
                    }
                    // a page that couldn't be rendered or searched, e.g. for lack of memory, may work next time
                    BarcodeScan(pageCount, barcodes).also {
                        if (allPagesScanned) {
                            scans.put(key, it)
                            scanStore.save(id, settingsKey, it)
                            savedScanIds.tryEmit(id)
                        }
                    }
                }
            emit(scan)
        }
    }

    /** The barcodes of a complete earlier scan, without searching the document, or null if there's none */
    suspend fun savedScan(id: String, mode: BarcodeSearchMode, generateNewBarcode: Boolean): BarcodeScan? {
        val settingsKey = "$mode-$generateNewBarcode"
        val key = "$id-$settingsKey"
        return scans[key] ?: scanStore.load(id, settingsKey)?.also { scans.put(key, it) }
    }

    private suspend fun cached(key: String, render: suspend () -> Bitmap?): Bitmap? =
        cache.get(key) ?: render()?.also { cache.put(key, it) }

    /**
     * Opening a document parses it, so its renderer is shared while it's in use and stays open afterwards,
     * until another document's renderer is done, e.g. for the next page or thumbnail of the same document.
     */
    private suspend fun <T> withRenderer(id: String, block: suspend (PdfRenderer) -> T): T {
        // set where it's counted, as cancellation can discard the result of withContext
        var open: OpenRenderer? = null
        return try {
            val renderer = withContext(renderDispatcher) {
                renderers.getOrPut(id) {
                    OpenRenderer(PdfRendererBuilder.create(context, fileName = id, renderContext = renderDispatcher))
                }.also {
                    it.users++
                    open = it
                }.renderer
            }
            block(renderer)
        } finally {
            withContext(NonCancellable + renderDispatcher) {
                val open = open ?: return@withContext
                open.users--
                if (open.users == 0) {
                    renderers.entries.removeAll { (otherId, other) ->
                        (otherId != id && other.users == 0).also { if (it) other.renderer.close() }
                    }
                }
            }
        }
    }

    private class OpenRenderer(val renderer: PdfRenderer) {
        var users = 0
    }

    // Large pages take a while to scale, so this stays off the main thread
    private suspend fun Bitmap.scaleToWidth(targetWidth: Int): Bitmap =
        if (width <= targetWidth) {
            this
        } else {
            withContext(Dispatchers.Default) {
                scale(width = targetWidth, height = (targetWidth.toFloat() / width * height).toInt())
            }
        }
}
