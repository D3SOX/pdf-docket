package me.d3sox.pdfwallet.details

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test

class BarcodeViewTest {

    private val qrCode = 1f
    private val linearBarcode = 3f

    @Test
    fun barcodePaneLength_fitsTheBarcodeToTheWidthAboveThePdf() {
        for (aspectRatio in listOf(qrCode, linearBarcode, 1 / linearBarcode)) {
            for (halfSize in listOf(false, true)) {
                val height = barcodePaneLength(listOf(aspectRatio), 400.dp, sideBySide = false, halfSize, 0.dp)
                val fitted = barcodeSize(400.dp, height, aspectRatio, halfSize).width
                // a taller pane doesn't show it larger
                barcodeSize(400.dp, height + 1.dp, aspectRatio, halfSize).width.value shouldBeLessThanOrEqual
                    fitted.value + 0.01f
            }
        }
    }

    @Test
    fun barcodePaneLength_fitsAQrCodeExactly() {
        val height = paneHeight(qrCode)
        barcodeSize(400.dp, height - 1.dp, qrCode, halfSize = false).width shouldBeLessThan
            barcodeSize(400.dp, height, qrCode, halfSize = false).width
    }

    @Test
    fun barcodePaneLength_fitsTheBarcodeToTheHeightNextToThePdf() {
        for (aspectRatio in listOf(qrCode, linearBarcode, 1 / linearBarcode)) {
            val width = barcodePaneLength(listOf(aspectRatio), 360.dp, sideBySide = true, halfSize = false, 0.dp)
            val fitted = barcodeSize(width, 360.dp, aspectRatio, halfSize = false).height
            val unlimited = barcodeSize(4000.dp, 360.dp, aspectRatio, halfSize = false).height
            fitted.value shouldBe (unlimited.value plusOrMinus 0.1f)
            barcodeSize(width - 1.dp, 360.dp, aspectRatio, halfSize = false).height shouldBeLessThan fitted
        }
    }

    @Test
    fun barcodePaneLength_leavesSpaceForThePageIndicator() {
        val withoutIndicator = barcodePaneLength(listOf(qrCode), 400.dp, sideBySide = false, false, 0.dp)
        barcodePaneLength(listOf(qrCode), 400.dp, sideBySide = false, false, 48.dp) shouldBe withoutIndicator + 48.dp
    }

    @Test
    fun barcodePaneLength_fitsTheLargestBarcode() {
        val lengths = listOf(qrCode, linearBarcode).map { paneHeight(it) }
        barcodePaneLength(listOf(linearBarcode, qrCode), 400.dp, sideBySide = false, false, 0.dp) shouldBe lengths.max()
    }

    @Test
    fun barcodePaneMinLength_keepsTheBarcodeVisible() {
        for (aspectRatio in listOf(qrCode, linearBarcode, 1 / linearBarcode)) {
            for (halfSize in listOf(false, true)) {
                // at least 64 dp, unless the whole barcode is smaller
                val height = barcodePaneMinLength(listOf(aspectRatio), 400.dp, sideBySide = false, halfSize, 0.dp)
                val fullHeight = barcodeSize(400.dp, 4000.dp, aspectRatio, halfSize).shorterSide()
                barcodeSize(400.dp, height, aspectRatio, halfSize).shorterSide() shouldBeGreaterThanOrEqual
                    minOf(64f, fullHeight) - 0.01f
                val width = barcodePaneMinLength(listOf(aspectRatio), 360.dp, sideBySide = true, halfSize, 0.dp)
                val fullWidth = barcodeSize(4000.dp, 360.dp, aspectRatio, halfSize).shorterSide()
                barcodeSize(width, 360.dp, aspectRatio, halfSize).shorterSide() shouldBeGreaterThanOrEqual
                    minOf(64f, fullWidth) - 0.01f
            }
        }
    }

    @Test
    fun barcodePaneMinLength_isAtMostTheFittedLength() {
        // a short barcode that is shown in full in a smaller pane than the minimum size needs
        val wide = 12f
        barcodePaneMinLength(listOf(wide), 400.dp, sideBySide = false, false, 0.dp) shouldBe paneHeight(wide)
    }

    private fun DpSize.shorterSide() = minOf(width, height).value

    private fun paneHeight(aspectRatio: Float): Dp =
        barcodePaneLength(listOf(aspectRatio), 400.dp, sideBySide = false, halfSize = false, indicatorHeight = 0.dp)
}
