package me.d3sox.pdfwallet.details

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.render.PageBarcode

private const val HALF_SIZE = 0.5f
private const val ROTATION_DEGREES = 90f
private const val BARCODE_PADDING_DIVISOR = 8
private val BARCODE_MARGIN = 24.dp
// the white quiet zone that scanners need around the code
private val MIN_QUIET_ZONE = 16.dp
private val MAX_QUIET_ZONE = 32.dp
// barcodes stay at least this large when the split screen is resized
private val MIN_BARCODE_SIZE = 64.dp

internal fun PageBarcode.aspectRatio(rotated: Boolean): Float =
    if (rotated) bitmap.height.toFloat() / bitmap.width else bitmap.width.toFloat() / bitmap.height

private fun quietZone(height: Dp) = (height / BARCODE_PADDING_DIVISOR).coerceIn(MIN_QUIET_ZONE, MAX_QUIET_ZONE)

/**
 * The size of a barcode in a pane of [maxWidth] and [maxHeight], as large as possible while keeping the aspect ratio.
 */
internal fun barcodeSize(maxWidth: Dp, maxHeight: Dp, aspectRatio: Float, halfSize: Boolean): DpSize {
    val inset = (BARCODE_MARGIN + quietZone(maxHeight)) * 2
    val availableWidth = (maxWidth - inset) * (if (halfSize) HALF_SIZE else 1f)
    val width = minOf(availableWidth, (maxHeight - inset) * aspectRatio)
    return DpSize(width, width / aspectRatio)
}

/**
 * The length of a barcode pane that shows the barcodes as large as the [crossLength] allows,
 * its height above the PDF or its width next to it.
 */
internal fun barcodePaneLength(
    aspectRatios: List<Float>,
    crossLength: Dp,
    sideBySide: Boolean,
    halfSize: Boolean,
    indicatorHeight: Dp,
): Dp {
    val scale = if (halfSize) HALF_SIZE else 1f
    return aspectRatios.maxOf { aspectRatio ->
        if (sideBySide) {
            val height = crossLength - indicatorHeight
            val inset = (BARCODE_MARGIN + quietZone(height)) * 2
            (height - inset) * aspectRatio / scale + inset
        } else {
            // the quiet zone grows with the height, which is the longest with the smallest or the largest one
            listOf(MIN_QUIET_ZONE, MAX_QUIET_ZONE).maxOf { quietZone ->
                val inset = (BARCODE_MARGIN + quietZone) * 2
                (crossLength - inset) * scale / aspectRatio + inset + indicatorHeight
            }
        }
    }
}

/**
 * The length of a barcode pane that keeps the shorter side of the barcodes at least [MIN_BARCODE_SIZE] large,
 * or shows them in full when that takes less space.
 */
internal fun barcodePaneMinLength(
    aspectRatios: List<Float>,
    crossLength: Dp,
    sideBySide: Boolean,
    halfSize: Boolean,
    indicatorHeight: Dp,
): Dp {
    val scale = if (halfSize) HALF_SIZE else 1f
    return aspectRatios.maxOf { aspectRatio ->
        val minLength = if (sideBySide) {
            val inset = (BARCODE_MARGIN + quietZone(crossLength - indicatorHeight)) * 2
            MIN_BARCODE_SIZE * maxOf(1f, aspectRatio) / scale + inset
        } else {
            // the quiet zone grows with the height, this leaves space for the largest one
            MIN_BARCODE_SIZE * maxOf(1f, 1 / aspectRatio) + (BARCODE_MARGIN + MAX_QUIET_ZONE) * 2 + indicatorHeight
        }
        minOf(minLength, barcodePaneLength(listOf(aspectRatio), crossLength, sideBySide, halfSize, indicatorHeight))
    }
}

@Composable
internal fun BarcodeView(barcode: PageBarcode, halfSize: Boolean, rotated: Boolean, onToggleRotation: () -> Unit) {
    val bitmap = remember(barcode) { barcode.bitmap.asImageBitmap() }
    val aspectRatio = barcode.aspectRatio(rotated)
    val onToggleRotationState = rememberUpdatedState(onToggleRotation)
    val rotateLabel = stringResource(R.string.rotate_barcode)
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            // e.g. a wide barcode gets taller next to the PDF
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onToggleRotationState.value() }) }
            .semantics {
                customActions = listOf(CustomAccessibilityAction(rotateLabel) { onToggleRotationState.value(); true })
            },
    ) {
        val (width, height) = barcodeSize(maxWidth, maxHeight, aspectRatio, halfSize)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(quietZone(maxHeight))
                .size(width = width, height = height),
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.show_barcode),
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.FillBounds,
                // laid out unturned, then turned into the space of the turned barcode
                modifier = Modifier
                    .requiredSize(
                        width = if (rotated) height else width,
                        height = if (rotated) width else height,
                    )
                    .rotate(if (rotated) ROTATION_DEGREES else 0f),
            )
        }
    }
}
