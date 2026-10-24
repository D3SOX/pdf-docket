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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val THUMBNAIL_WIDTH_PX = 256
private const val MAX_CACHED_BARCODE_RESULTS = 32

data class PageBarcode(val pageIndex: Int, val bitmap: Bitmap)

data class BarcodeScan(val pageCount: Int, val barcodes: List<PageBarcode>) {
    val hasSeveralBarcodesPerPage: Boolean
        get() = barcodes.groupingBy { it.pageIndex }.eachCount().any { it.value > 1 }
}

/**
 * Renders PDF pages, thumbnails and barcodes. Results are cached in memory, since the files never change.
 */
@Singleton
class DocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val barcodeRenderer: BarcodeRenderer,
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
     * Searches every page for a barcode. Pages rendered along the way are cached for the PDF view.
     */
    suspend fun scanBarcodes(id: String, mode: BarcodeSearchMode, generateNewBarcode: Boolean): BarcodeScan {
        if (mode == BarcodeSearchMode.DISABLED) return BarcodeScan(pageCount(id), emptyList())
        val key = "$id-$mode-$generateNewBarcode"
        return scanMutex.withLock {
            scans[key] ?: withRenderer(id) { renderer ->
                val pageCount = renderer.getPageCount()
                val barcodes = (0 until pageCount).flatMap { pageIndex ->
                    val page = renderer.renderPage(
                        pageIndex = pageIndex,
                        highResolution = mode == BarcodeSearchMode.EXTENDED,
                    ) ?: return@flatMap emptyList()
                    val pageBarcodes = barcodeRenderer.getBarcodes(
                        document = page,
                        tryExtraHard = mode == BarcodeSearchMode.EXTENDED,
                        generateNewBarcode = generateNewBarcode,
                    )
                    if (cache.get(pageKey(id, pageIndex)) == null) {
                        cache.put(pageKey(id, pageIndex), page.scaleToWidth(screenWidth))
                    }
                    pageBarcodes.map { PageBarcode(pageIndex, it) }
                }
                BarcodeScan(pageCount, barcodes)
            }.also { scans.put(key, it) }
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

    private fun Bitmap.scaleToWidth(targetWidth: Int): Bitmap =
        if (width <= targetWidth) {
            this
        } else {
            scale(width = targetWidth, height = (targetWidth.toFloat() / width * height).toInt())
        }
}
