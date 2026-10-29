package me.d3sox.pdfwallet.render

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.scale
import me.d3sox.pdfwallet.barcode.BarcodeRenderer
import me.d3sox.pdfwallet.pdfrenderer.PdfRenderer
import me.d3sox.pdfwallet.pdfrenderer.PdfRendererBuilder
import me.d3sox.pdfwallet.settings.BarcodeSearchMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val THUMBNAIL_WIDTH_PX = 512
private const val MAX_CACHED_BARCODE_RESULTS = 32

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
    private val scans = LruCache<String, BarcodeScan>(MAX_CACHED_BARCODE_RESULTS)
    private val scanMutex = Mutex()

    private val screenWidth: Int get() = context.resources.displayMetrics.widthPixels

    suspend fun thumbnail(id: String): Bitmap? = cached("thumb-$id") {
        withRenderer(id) { renderer ->
            renderer.renderPage(pageIndex = 0, highResolution = false)?.scaleToWidth(THUMBNAIL_WIDTH_PX)
        }
    }

    suspend fun page(id: String, pageIndex: Int): Bitmap? = cached(pageKey(id, pageIndex)) {
        withRenderer(id) { renderer ->
            renderer.renderPage(pageIndex = pageIndex, highResolution = false)?.scaleToWidth(screenWidth)
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
            val scan = scans[key]
                ?: scanStore.load(id, settingsKey)?.also { scans.put(key, it) }
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
                            cache.put(pageKey(id, pageIndex), page.scaleToWidth(screenWidth))
                        }
                        emit(BarcodeScan(pageCount, barcodes.toList(), complete = false))
                    }
                    // a page that couldn't be rendered or searched, e.g. for lack of memory, may work next time
                    BarcodeScan(pageCount, barcodes).also {
                        if (allPagesScanned) {
                            scans.put(key, it)
                            scanStore.save(id, settingsKey, it)
                        }
                    }
                }
            emit(scan)
        }
    }

    private fun pageKey(id: String, pageIndex: Int) = "page-$id-$pageIndex"

    private suspend fun cached(key: String, render: suspend () -> Bitmap?): Bitmap? =
        cache.get(key) ?: render()?.also { cache.put(key, it) }

    private suspend fun <T> withRenderer(id: String, block: suspend (PdfRenderer) -> T): T {
        val renderer = PdfRendererBuilder.create(context, fileName = id, renderContext = renderDispatcher)
        return try {
            block(renderer)
        } finally {
            renderer.close()
        }
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
