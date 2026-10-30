package me.d3sox.pdfwallet.render

import android.graphics.Bitmap
import android.util.LruCache

object BitmapCache {
    // The cache sizes are measured in kilobytes rather than number of items.
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()

    @Suppress("MagicNumber")
    val memoryCache: LruCache<String, Bitmap> by lazy {
        // Use 1/8th of the available memory for this memory cache.
        object : LruCache<String, Bitmap>(maxMemory / 8) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
        }
    }

    /** Barcode scans, limited by the size of their barcodes, since a document can have many large ones. */
    @Suppress("MagicNumber")
    val scans: LruCache<String, BarcodeScan> by lazy {
        object : LruCache<String, BarcodeScan>(maxMemory / 16) {
            // at least 1, so scans without barcodes are limited as well
            override fun sizeOf(key: String, scan: BarcodeScan): Int =
                (scan.barcodes.sumOf { it.bitmap.byteCount.toLong() } / 1024).toInt().coerceAtLeast(1)
        }
    }

    fun evictAll() {
        memoryCache.evictAll()
        scans.evictAll()
    }
}
