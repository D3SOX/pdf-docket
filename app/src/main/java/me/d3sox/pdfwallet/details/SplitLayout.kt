package me.d3sox.pdfwallet.details

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.R

private const val MAX_FRACTION = 0.85f
// a fitted split never takes the second pane more space than an even split
private const val EVEN_FRACTION = 0.5f
private val HANDLE_THICKNESS = 16.dp
private val HANDLE_LENGTH = 32.dp
private val HANDLE_WIDTH = 4.dp

/**
 * Shows [first] above [second], or next to each other in landscape. [first] takes the [fraction] of the space.
 * When it's null, [first] takes the height [fitHeight] returns for the width, at most half, and next to each other
 * both take half, since a portrait page wouldn't use more width.
 * Dragging the divider changes the fraction, but not below the length [minLength] returns for the width
 * in portrait or the height in landscape, at most the fitted length. Double tapping the divider returns to it.
 */
@Composable
@Suppress("LongParameterList")
internal fun SplitLayout(
    fraction: Float?,
    fitHeight: (width: Dp) -> Dp,
    minLength: (crossLength: Dp, sideBySide: Boolean) -> Dp,
    onFractionChange: (Float?) -> Unit,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    // follows the saved fraction, but changes right away while dragging
    var shownFraction by remember(fraction) { mutableStateOf(fraction) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideBySide = maxWidth > maxHeight
        val length = (if (sideBySide) maxWidth else maxHeight) - HANDLE_THICKNESS
        val fitted = if (sideBySide) EVEN_FRACTION else (fitHeight(maxWidth) / length).coerceAtMost(EVEN_FRACTION)
        // a barcode that would need more can still shrink to the fitted length
        val minFraction = (minLength(if (sideBySide) maxHeight else maxWidth, sideBySide) / length)
            .coerceAtMost(fitted)
        val share = (shownFraction ?: fitted).coerceIn(minFraction, MAX_FRACTION)
        val lengthPx = with(LocalDensity.current) { length.toPx() }
        val handle = @Composable {
            SplitHandle(
                sideBySide = sideBySide,
                fraction = share,
                range = minFraction..MAX_FRACTION,
                onDrag = { delta ->
                    shownFraction = ((shownFraction ?: fitted) + delta / lengthPx).coerceIn(minFraction, MAX_FRACTION)
                },
                onDragStopped = { onFractionChange(shownFraction) },
                onSet = {
                    shownFraction = it.coerceIn(minFraction, MAX_FRACTION)
                    onFractionChange(shownFraction)
                },
                onReset = {
                    shownFraction = null
                    onFractionChange(null)
                },
            )
        }
        if (sideBySide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(share)) { first() }
                handle()
                Box(Modifier.weight(1 - share)) { second() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(share)) { first() }
                handle()
                Box(Modifier.weight(1 - share)) { second() }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun SplitHandle(
    sideBySide: Boolean,
    fraction: Float,
    range: ClosedFloatingPointRange<Float>,
    onDrag: (Float) -> Unit,
    onDragStopped: () -> Unit,
    onSet: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val onDragState = rememberUpdatedState(onDrag)
    val onResetState = rememberUpdatedState(onReset)
    val description = stringResource(R.string.split_handle)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(
                if (sideBySide) {
                    Modifier.fillMaxHeight().width(HANDLE_THICKNESS)
                } else {
                    Modifier.fillMaxWidth().height(HANDLE_THICKNESS)
                }
            )
            .draggable(
                state = rememberDraggableState { onDragState.value(it) },
                orientation = if (sideBySide) Orientation.Horizontal else Orientation.Vertical,
                // the first pane is on the right in right-to-left layouts
                reverseDirection = sideBySide && rtl,
                onDragStopped = { onDragStopped() },
            )
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onResetState.value() }) }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, range)
                setProgress { onSet(it); true }
            },
    ) {
        if (sideBySide) VerticalDivider() else HorizontalDivider()
        Box(
            Modifier
                .size(
                    width = if (sideBySide) HANDLE_WIDTH else HANDLE_LENGTH,
                    height = if (sideBySide) HANDLE_LENGTH else HANDLE_WIDTH,
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}
