package me.d3sox.pdfwallet.render

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarcodeScanStoreTest {

    private val store = BarcodeScanStore(ApplicationProvider.getApplicationContext())

    private fun barcode(width: Int, color: Int): Bitmap = createBitmap(width, 10).apply { eraseColor(color) }

    @Test
    fun load_returnsTheSavedScan() = runTest {
        store.save(
            "a.pdf",
            "EXTENDED-false",
            BarcodeScan(
                pageCount = 3,
                barcodes = listOf(PageBarcode(0, barcode(20, Color.BLACK)), PageBarcode(2, barcode(30, Color.RED))),
            ),
        )

        val scan = store.load("a.pdf", "EXTENDED-false").shouldNotBeNull()
        scan.pageCount shouldBe 3
        scan.complete shouldBe true
        scan.barcodes.map { it.pageIndex } shouldContainExactly listOf(0, 2)
        scan.barcodes.map { it.bitmap.width } shouldContainExactly listOf(20, 30)
        scan.barcodes[1].bitmap.getPixel(0, 0) shouldBe Color.RED
    }

    @Test
    fun load_keepsScansWithoutBarcodes() = runTest {
        store.save("a.pdf", "REGULAR-false", BarcodeScan(pageCount = 2, barcodes = emptyList()))

        store.load("a.pdf", "REGULAR-false").shouldNotBeNull().barcodes shouldBe emptyList()
    }

    @Test
    fun load_ignoresScansWithOtherSettings() = runTest {
        store.save("a.pdf", "REGULAR-false", BarcodeScan(pageCount = 1, barcodes = emptyList()))

        store.load("a.pdf", "EXTENDED-false").shouldBeNull()
        store.load("b.pdf", "REGULAR-false").shouldBeNull()
    }

    @Test
    fun save_replacesThePreviousScan() = runTest {
        store.save("a.pdf", "REGULAR-false", BarcodeScan(1, listOf(PageBarcode(0, barcode(20, Color.BLACK)))))

        store.save("a.pdf", "EXTENDED-true", BarcodeScan(pageCount = 1, barcodes = emptyList()))

        store.load("a.pdf", "REGULAR-false").shouldBeNull()
        store.load("a.pdf", "EXTENDED-true").shouldNotBeNull().barcodes shouldBe emptyList()
    }

    @Test
    fun delete_removesTheScan() = runTest {
        store.save("a.pdf", "REGULAR-false", BarcodeScan(pageCount = 1, barcodes = emptyList()))
        store.save("b.pdf", "REGULAR-false", BarcodeScan(pageCount = 1, barcodes = emptyList()))

        store.delete(listOf("a.pdf"))

        store.load("a.pdf", "REGULAR-false").shouldBeNull()
        store.load("b.pdf", "REGULAR-false").shouldNotBeNull()
    }
}
