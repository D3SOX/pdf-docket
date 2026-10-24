package me.d3sox.pdfwallet.pdfrenderer

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sqrt

private const val REGULAR_PDF_RESOLUTION_MULTIPLIER = 2
private const val IMPROVED_PDF_RESOLUTION_MULTIPLIER = 4
private const val MAX_BITMAP_SIZE = 100 * 1024 * 1024
private const val BYTES_PER_PIXEL = 4

@Suppress("MagicNumber")
private val invertFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

public object PdfRendererBuilder {
    public fun create(
        context: Context,
        fileName: String,
        renderContext: CoroutineDispatcher
    ): me.d3sox.pdfwallet.pdfrenderer.PdfRenderer = PdfRendererImpl(
        context = context,
        fileName = fileName,
        renderContext = renderContext
    )
}

public interface PdfRenderer {
    @Throws(Exception::class)
    public suspend fun loadFile()
    public suspend fun getPageCount(): Int
    public fun close()
    public suspend fun renderPage(pageIndex: Int, highResolution: Boolean): Bitmap?
    public fun getInvertFilter(): ColorFilter
}

private class PdfRendererImpl(
    private val context: Context,
    fileName: String,
    private val renderContext: CoroutineDispatcher,
): me.d3sox.pdfwallet.pdfrenderer.PdfRenderer {

    private val file = File(context.filesDir, fileName)

    private val activityManager: ActivityManager?
        get() = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    @Throws(Exception::class)
    override suspend fun loadFile(): Unit = withContext(renderContext) {
        // Loading again would otherwise leak the previous file descriptor
        close()
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        fileDescriptor = descriptor
        renderer = PdfRenderer(descriptor).also { it.openPage(0).use { } }
    }

    override suspend fun getPageCount(): Int = withContext(renderContext) {
        if (renderer == null) {
            try {
                loadFile()
            }
            catch (ignore: Exception) {}
            if (!isActive) return@withContext 0
        }
        renderer?.pageCount ?: 0
    }

    override fun close() {
        try {
            renderer?.use {}
            fileDescriptor?.use {}
        } catch (ignore: Exception) {}
        renderer = null
        fileDescriptor = null
    }

    override suspend fun renderPage(
        pageIndex: Int,
        highResolution: Boolean,
    ): Bitmap? = withContext(renderContext) {
        if (renderer == null) {
            try {
                loadFile()
            }
            catch (ignore: Exception) {}
            if (!isActive) return@withContext null
        }
        renderer?.openPage(pageIndex)?.renderAndClose(highResolution) { isActive }
    }

    private fun PdfRenderer.Page.renderAndClose(highResolution: Boolean, isActive: () -> Boolean): Bitmap? = use {
        if (!isActive()) return@use null
        val bitmap = createBitmap(highResolution)
        if (!isActive()) return@use null
        render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
    }

    @Suppress("SwallowedException")
    private fun PdfRenderer.Page.createBitmap(highResolution: Boolean): Bitmap {
        val multiplier = when {
            activityManager?.isLowRamDevice != false -> 1
            highResolution -> IMPROVED_PDF_RESOLUTION_MULTIPLIER
            else -> REGULAR_PDF_RESOLUTION_MULTIPLIER
        }
        // Scale before allocating, so the bitmap never exceeds the size limit, even for huge pages
        val maxScale = sqrt(MAX_BITMAP_SIZE / (width.toDouble() * height * BYTES_PER_PIXEL))
        val bitmap = try {
            createScaledBitmap(min(multiplier.toDouble(), maxScale))
        } catch (e: OutOfMemoryError) {
            createScaledBitmap(min(1.0, maxScale))
        }

        // Pages can be transparent, so render them onto white
        Canvas(bitmap).drawColor(Color.WHITE)

        return bitmap
    }

    // The page is rendered without a transform, so its content is scaled to fit the bitmap
    private fun PdfRenderer.Page.createScaledBitmap(scale: Double): Bitmap {
        val maxPixels = MAX_BITMAP_SIZE / BYTES_PER_PIXEL
        // Rounding a very narrow page up to 1 pixel could otherwise push the bitmap over the limit
        val bitmapWidth = (width * scale).toInt().coerceIn(1, maxPixels)
        val bitmapHeight = (height * scale).toInt().coerceIn(1, maxPixels / bitmapWidth)
        return Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    }

    override fun getInvertFilter(): ColorFilter {
        return invertFilter
    }
}
