package me.d3sox.pdfwallet

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.d3sox.pdfwallet.render.DocumentRenderer
import me.d3sox.pdfwallet.settings.BarcodeSearchMode
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Renders the sample PDFs of testdata_1d and testdata_2d and checks that their barcode is found.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BarcodeDetectionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var renderer: DocumentRenderer

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
    }

    /** Copies a test asset into the app's files, where the renderer expects documents. */
    private fun document(asset: String): String {
        val id = "test-$asset"
        val file = File(context.filesDir, id)
        instrumentation.context.assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
        copiedFiles += file
        return id
    }

    private suspend fun barcodeCount(asset: String, mode: BarcodeSearchMode = BarcodeSearchMode.EXTENDED) =
        renderer.scanBarcodes(document(asset), mode, generateNewBarcode = false).last().barcodes.size

    @Test
    fun everySupportedFormat_isFound() = runTest {
        val samples = listOf(
            "aztec.pdf", "datamatrix.pdf", "datamatrix_erezept.pdf", "datamatrix_small.pdf", "pdf417.pdf",
            "qr.pdf", "qr_logo.pdf", "codabar.pdf", "code128.pdf", "code39.pdf", "code93.pdf", "ean13.pdf",
            "ean8.pdf", "itf.pdf", "usbca.pdf", "usbce.pdf",
        )
        val missed = samples.filter { barcodeCount(it) == 0 }
        missed.shouldBeEmpty()
    }

    @Test
    fun preferredFormat_winsOverOthersOnThePage() = runTest {
        barcodeCount("prefer_aztec.pdf") shouldBe 1
    }

    @Test
    fun newBarcode_isGeneratedWhenEnabled() = runTest {
        renderer.scanBarcodes(document("qr.pdf"), BarcodeSearchMode.REGULAR, generateNewBarcode = true)
            .last().barcodes.size shouldBe 1
    }

    @Test
    fun disabledSearch_findsNothing() = runTest {
        barcodeCount("qr.pdf", BarcodeSearchMode.DISABLED) shouldBe 0
    }

    @Test
    fun documentWithoutBarcode_hasNone() = runTest {
        barcodeCount("demo.pdf") shouldBe 0
    }
}
