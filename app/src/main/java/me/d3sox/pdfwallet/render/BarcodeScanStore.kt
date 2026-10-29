package me.d3sox.pdfwallet.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import me.d3sox.pdfwallet.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val INDEX_FILE = "index"

/**
 * Keeps barcode scans across app restarts, so reopening a document doesn't search all its pages again.
 * The scans live in the cache directory, if the system clears it, documents are just scanned again.
 */
@Singleton
class BarcodeScanStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File get() = File(context.cacheDir, "barcodes")

    /** Returns null if the document wasn't scanned with these settings, or with an older app version. */
    suspend fun load(id: String, settingsKey: String): BarcodeScan? = withContext(Dispatchers.IO) {
        val dir = File(root, id)
        val index = File(dir, INDEX_FILE).takeIf { it.exists() }?.readLines() ?: return@withContext null
        if (index.firstOrNull() != key(settingsKey)) return@withContext null
        val pageCount = index.getOrNull(1)?.toIntOrNull() ?: return@withContext null
        val barcodes = index.drop(2).mapIndexed { position, pageIndex ->
            val bitmap = BitmapFactory.decodeFile(File(dir, "$position.png").path) ?: return@withContext null
            PageBarcode(pageIndex.toIntOrNull() ?: return@withContext null, bitmap)
        }
        BarcodeScan(pageCount, barcodes)
    }

    suspend fun save(id: String, settingsKey: String, scan: BarcodeScan): Unit = withContext(Dispatchers.IO) {
        val dir = File(root, id)
        dir.deleteRecursively()
        try {
            dir.mkdirs()
            scan.barcodes.forEachIndexed { position, barcode ->
                File(dir, "$position.png").outputStream().use {
                    barcode.bitmap.compress(Bitmap.CompressFormat.PNG, 0, it)
                }
            }
            // written last, so an interrupted save is never loaded
            File(dir, INDEX_FILE).writeText(
                (listOf(key(settingsKey), scan.pageCount.toString()) + scan.barcodes.map { it.pageIndex.toString() })
                    .joinToString("\n")
            )
        } catch (_: IOException) {
            // e.g. a full disk, the document is then scanned again next time
            dir.deleteRecursively()
        }
    }

    suspend fun delete(ids: List<String>): Unit = withContext(Dispatchers.IO) {
        ids.forEach { File(root, it).deleteRecursively() }
    }

    // app updates can find barcodes that older versions missed
    private fun key(settingsKey: String) = "${BuildConfig.VERSION_CODE}-$settingsKey"
}
