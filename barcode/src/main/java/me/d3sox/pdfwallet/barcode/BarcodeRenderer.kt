package me.d3sox.pdfwallet.barcode

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.scale
import me.d3sox.pdfwallet.coroutines.dispatcher.di.DefaultDispatcher
import de.markusfisch.android.zxingcpp.ZxingCpp
import de.markusfisch.android.zxingcpp.ZxingCpp.toBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val BARCODE_SIZE = 400
private const val RENDER_MULTIPLIER = 10
private const val BARCODE_FOREGROUND_COLOR = 0xff000000.toInt()
private const val BARCODE_BACKGROUND_COLOR = 0xffffffff.toInt()

private val preferredPriority = listOf(
    ZxingCpp.BarcodeFormat.Aztec,
    ZxingCpp.BarcodeFormat.DataMatrix,
    ZxingCpp.BarcodeFormat.PDF417,
    ZxingCpp.BarcodeFormat.QRCode,
    ZxingCpp.BarcodeFormat.UPCA,
    ZxingCpp.BarcodeFormat.UPCE,
    ZxingCpp.BarcodeFormat.EAN8,
    ZxingCpp.BarcodeFormat.EAN13,
    ZxingCpp.BarcodeFormat.Code39,
    ZxingCpp.BarcodeFormat.Code93,
    ZxingCpp.BarcodeFormat.Code128,
    ZxingCpp.BarcodeFormat.Codabar,
    ZxingCpp.BarcodeFormat.ITF,
)

/**
 * Results may report a specific variant (e.g. a QR code model) of a requested format family.
 */
private fun ZxingCpp.BarcodeFormat.priority(): Int =
    preferredPriority.indexOfFirst { name.startsWith(it.name) }.takeIf { it >= 0 } ?: Int.MAX_VALUE

private val readerOptions = ZxingCpp.ReaderOptions(
    formats = preferredPriority.toSet(),
    tryHarder = true,
    tryRotate = true,
    tryInvert = true,
    tryDownscale = true,
    // e.g. one page with several tickets
    maxNumberOfSymbols = 0xff,
)

private data class BarcodeHit(
    val result: ZxingCpp.Result,
    val cropRect: Rect
)

/**
 * [complete] is false if a part of the page couldn't be searched, e.g. for lack of memory.
 */
public data class PageBarcodes(val bitmaps: List<Bitmap>, val complete: Boolean)

public interface BarcodeRenderer {
    /**
     * Returns the barcodes of the page in reading order. When a page contains different formats,
     * only the ones of the preferred format are returned, e.g. a ticket code instead of an advertisement.
     */
    public suspend fun getBarcodes(
        document: Bitmap,
        tryExtraHard: Boolean,
        generateNewBarcode: Boolean
    ): PageBarcodes
}

internal class BarcodeRendererImpl @Inject constructor(
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : BarcodeRenderer {

    override suspend fun getBarcodes(
        document: Bitmap,
        tryExtraHard: Boolean,
        generateNewBarcode: Boolean,
    ): PageBarcodes = withContext(dispatcher) {
        // The crop rectangles are independent, so they are searched in parallel
        val results = document.getCropRectangles(tryExtraHard).map { cropRect ->
            async { document.readBarcodes(cropRect) }
        }.awaitAll()
        val bitmaps = results.filterNotNull().flatten().preferred().mapNotNull { hit ->
            if (!isActive) return@withContext PageBarcodes(emptyList(), complete = false)
            encodeBarcodeAsBitmap(
                hit = hit,
                source = document,
                generateNewBarcode = generateNewBarcode
            )
        }
        PageBarcodes(bitmaps, complete = results.none { it == null })
    }

    private fun List<BarcodeHit>.preferred(): List<BarcodeHit> {
        if (isEmpty()) return this
        val preferredPriority = minOf { it.result.format.priority() }
        return filter { it.result.format.priority() == preferredPriority }
            // the overlapping crop rectangles find the same barcode several times
            .distinctBy { it.result.format to it.result.rawBytes.contentHashCode() }
            .sortedWith(compareBy({ it.top }, { it.left }))
    }

    /** Returns null if the search failed. */
    private fun Bitmap.readBarcodes(cropRect: Rect): List<BarcodeHit>? = try {
        ZxingCpp.readBitmap(
            bitmap = this,
            cropRect = cropRect,
            rotation = 0,
            options = readerOptions,
        )?.map { BarcodeHit(it, cropRect) } ?: emptyList()
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    private val BarcodeHit.top: Int
        get() = cropRect.top + minOf(result.position.topLeft.y, result.position.topRight.y)

    private val BarcodeHit.left: Int
        get() = cropRect.left + minOf(result.position.topLeft.x, result.position.bottomLeft.x)

    @Suppress("MagicNumber")
    private fun Bitmap.getCropRectangles(tryExtraHard: Boolean): List<Rect> {
        val list = mutableListOf(Rect(0, 0, width, height))
        if (tryExtraHard) {
            list += getCropRectangles(3, 1)
            list += getCropRectangles(4, 1)
            list += getCropRectangles(5, 1)
        }
        return list
    }

    private fun Bitmap.getCropRectangles(
        divisorLongerSize: Int,
        divisorShorterSize: Int
    ): List<Rect> {
        val divisorX: Int
        val divisorY: Int

        if (width > height) {
            divisorX = divisorLongerSize
            divisorY = divisorShorterSize
        } else {
            divisorX = divisorShorterSize
            divisorY = divisorLongerSize
        }

        val tempX = width / divisorX
        val tempY = height / divisorY

        val rects = mutableListOf<Rect>()
        for (x in 0 until divisorX) {
            for (y in 0 until divisorY) {
                rects += Rect(
                    tempX * x,
                    tempY * y,
                    tempX * (x + 1),
                    tempY * (y + 1)
                )
            }
        }
        return rects
    }

    @Suppress("ComplexCondition")
    private fun encodeBarcodeAsBitmap(
        source: Bitmap,
        hit: BarcodeHit,
        generateNewBarcode: Boolean,
    ): Bitmap? {
        return if (generateNewBarcode) {
            val bitMatrix = hit.result.symbol
             if (bitMatrix == null || bitMatrix.data.isEmpty() || bitMatrix.width == 0 || bitMatrix.height == 0) {
                createBarcodeFromBytesOrText(hit.result)
            } else {
                createBarcodeFromBitMatrix(bitMatrix)
            }
        } else {
            cropBarcodeBoundingBox(source, hit)
        }
    }

    private fun createBarcodeFromBitMatrix(bitMatrix: ZxingCpp.BitMatrix): Bitmap {
        val src = bitMatrix.toBitmap(
            setColor = BARCODE_FOREGROUND_COLOR,
            unsetColor = BARCODE_BACKGROUND_COLOR,
        )

        val dstWidth = src.width * RENDER_MULTIPLIER
        val dstHeight = src.height * RENDER_MULTIPLIER

        return src.scale(dstWidth, dstHeight, false)
    }

    private fun createBarcodeFromBytesOrText(originalCode: ZxingCpp.Result): Bitmap {
        val content = if (originalCode.contentType == ZxingCpp.ContentType.TEXT) {
            originalCode.text
        } else {
            originalCode.rawBytes
        }

        return ZxingCpp.encodeAsBitmap(
            content = content,
            format = originalCode.format,
            width = BARCODE_SIZE,
            height = BARCODE_SIZE,
            setColor = BARCODE_FOREGROUND_COLOR,
            unsetColor = BARCODE_BACKGROUND_COLOR,
            addQuietZones = false,
        )
    }

    @Suppress("ReturnCount", "MagicNumber")
    private fun cropBarcodeBoundingBox(
        source: Bitmap,
        hit: BarcodeHit
    ): Bitmap? {
        val pos = hit.result.position
        val cropRect = hit.cropRect

        val xs = intArrayOf(
            pos.topLeft.x + cropRect.left,
            pos.topRight.x + cropRect.left,
            pos.bottomLeft.x + cropRect.left,
            pos.bottomRight.x + cropRect.left
        )

        val ys = intArrayOf(
            pos.topLeft.y + cropRect.top,
            pos.topRight.y + cropRect.top,
            pos.bottomLeft.y + cropRect.top,
            pos.bottomRight.y + cropRect.top
        )

        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()

        if (minX >= maxX || minY >= maxY) return null

        val padX = ((maxX - minX) * 0.1f).toInt()
        val padY = ((maxY - minY) * 0.1f).toInt()

        val left = (minX - padX).coerceAtLeast(0)
        val top = (minY - padY).coerceAtLeast(0)
        val right = (maxX + padX).coerceAtMost(source.width)
        val bottom = (maxY + padY).coerceAtMost(source.height)

        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null

        return Bitmap.createBitmap(source, left, top, width, height)
    }
}
