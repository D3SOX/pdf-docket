package me.d3sox.pdfwallet.details

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test

class PageSyncTest {

    // barcodes on pages 1, 4 and two on page 6
    private val pages = listOf(1, 4, 6, 6)

    @Test
    fun pdfPosition_followsTheBarcodesPages() {
        pdfPosition(0f, pages) shouldBe 1f
        pdfPosition(1f, pages) shouldBe 4f
        pdfPosition(3f, pages) shouldBe 6f
    }

    @Test
    fun pdfPosition_movesBetweenThePagesWhileSwiping() {
        pdfPosition(0.5f, pages) shouldBe (2.5f plusOrMinus 0.001f)
        // the second barcode of a page keeps the PDF on that page
        pdfPosition(2.5f, pages) shouldBe 6f
    }

    @Test
    fun barcodePosition_restsOnABarcodeOnPagesWithoutOne() {
        barcodePosition(0f, pages) shouldBe 0f
        barcodePosition(1f, pages) shouldBe 0f
        barcodePosition(2f, pages) shouldBe 0f
        barcodePosition(3f, pages) shouldBe 0f
        barcodePosition(4f, pages) shouldBe 1f
        barcodePosition(7f, pages) shouldBe 2f
    }

    @Test
    fun barcodePosition_onlyMovesWhileTurningToTheNextBarcodesPage() {
        barcodePosition(2.5f, pages) shouldBe 0f
        barcodePosition(3.25f, pages) shouldBe (0.25f plusOrMinus 0.001f)
        // goes to the first barcode of a page with several
        barcodePosition(6f, pages) shouldBe 2f
    }

    @Test
    fun barcodePosition_keepsTheShownBarcodeOfItsPage() {
        // the second barcode on page 6 stays while the PDF is on that page or moves past it
        barcodePosition(6f, pages, keep = 3) shouldBe 3f
        barcodePosition(7f, pages, keep = 3) shouldBe 3f
        // and the barcodes move straight from it to the previous page's barcode
        barcodePosition(5.5f, pages, keep = 3) shouldBe (2f plusOrMinus 0.001f)
        barcodePosition(5f, pages, keep = 3) shouldBe 1f
        // a barcode on another page doesn't matter
        barcodePosition(6f, pages, keep = 0) shouldBe 2f
    }

    @Test
    fun positionsAreRoundTrips() {
        pages.indices.forEach { barcode ->
            if (barcode == 3) return@forEach // second barcode on the same page
            barcodePosition(pdfPosition(barcode.toFloat(), pages), pages) shouldBe barcode.toFloat()
        }
    }
}
