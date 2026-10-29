package me.d3sox.pdfwallet

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.d3sox.pdfwallet.barcode.BarcodeRenderer
import me.d3sox.pdfwallet.barcode.PageBarcodes
import me.d3sox.pdfwallet.barcode.di.BarcodeModule
import me.d3sox.pdfwallet.render.BarcodeScanStore
import me.d3sox.pdfwallet.render.DocumentRenderer
import me.d3sox.pdfwallet.settings.BarcodeSearchMode
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Checks which barcode scans are kept across app restarts, with a barcode search that can fail.
 */
@HiltAndroidTest
@UninstallModules(BarcodeModule::class)
@RunWith(AndroidJUnit4::class)
class BarcodeScanStoreTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private var searchCompletes = true
    private var searches = 0

    @BindValue
    @JvmField
    val barcodeRenderer: BarcodeRenderer = object : BarcodeRenderer {
        override suspend fun getBarcodes(document: Bitmap, tryExtraHard: Boolean, generateNewBarcode: Boolean) =
            PageBarcodes(listOf(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)), complete = searchCompletes)
                .also { searches++ }
    }

    @Inject lateinit var renderer: DocumentRenderer
    @Inject lateinit var scanStore: BarcodeScanStore

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val copiedFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        copiedFiles.forEach(File::delete)
        runBlocking { scanStore.delete(copiedFiles.map { it.name }) }
    }

    private fun document(id: String): String {
        val file = File(context.filesDir, id)
        instrumentation.context.assets.open("qr.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
        copiedFiles += file
        return id
    }

    private suspend fun scan(id: String) {
        renderer.scanBarcodes(id, BarcodeSearchMode.REGULAR, generateNewBarcode = false).last()
    }

    @Test
    fun completeSearch_isSaved() = runTest {
        val id = document("test-complete.pdf")

        scan(id)

        scanStore.load(id, "REGULAR-false").shouldNotBeNull()
    }

    @Test
    fun incompleteSearch_isNotSaved() = runTest {
        searchCompletes = false
        val id = document("test-incomplete.pdf")

        scan(id)
        scan(id)

        scanStore.load(id, "REGULAR-false").shouldBeNull()
        // not kept in memory either, so reopening the document searches again
        searches shouldBe 2
    }
}
