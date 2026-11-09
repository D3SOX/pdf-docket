package me.d3sox.pdfwallet.details

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.ui.components.ExpressiveMenu
import me.d3sox.pdfwallet.ui.components.selectable

private const val MAX_PAGE_DOTS = 10
private val MIN_LABEL_FONT_SIZE = 10.sp
private const val LABEL_SHRINK_FACTOR = 0.9f
// the padding and icon space of a segmented button: 2 × 12 dp padding, the reserved icon slot (18 + 8 dp),
// the icon in the label (18 + 8 dp) and the borders
private val SEGMENT_SPACE_AROUND_LABEL = 80.dp
private val DOT_SIZE = 8.dp
private val DOT_SPACING = 6.dp
private val DOTS_PADDING = 10.dp
private val NUMBERED_DOT_SIZE = 24.dp
private val NUMBERED_DOT_SPACING = 4.dp
private val NUMBERED_DOTS_PADDING = 4.dp
// the line height of labelSmall, which sets the size of numbered dots with a large font
private val NUMBER_LINE_HEIGHT = 16.dp
// enlarges the area for tapping the small dots
private val INDICATOR_TAP_PADDING = 8.dp

internal enum class ViewMode(val label: Int, val icon: Int) {
    BARCODE(R.string.view_barcode, R.drawable.qr_code_24),
    PDF(R.string.view_pdf, R.drawable.picture_as_pdf_24),
    BOTH(R.string.view_both, R.drawable.splitscreen_24),
}

@Composable
internal fun ViewModeBar(selected: ViewMode, onSelect: (ViewMode) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val labelStyle = fittingStyle(
                labels = ViewMode.entries.map { stringResource(it.label) },
                style = MaterialTheme.typography.labelLarge,
                maxWidth = maxWidth / ViewMode.entries.size - SEGMENT_SPACE_AROUND_LABEL,
            )
            ViewModeButtons(selected, onSelect, labelStyle)
        }
    }
}

/**
 * Shows the [selected] view mode in the top bar, and the others in a menu, while the [ViewModeBar] is hidden.
 */
@Composable
internal fun ViewModeMenuButton(selected: ViewMode, onSelect: (ViewMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val onDismiss = { expanded = false }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(selected.icon), contentDescription = stringResource(R.string.switch_view))
        }
        ExpressiveMenu(expanded = expanded, onDismissRequest = onDismiss) {
            group {
                ViewMode.entries.forEach { mode ->
                    selectable(
                        text = { stringResource(mode.label) },
                        icon = { Icon(painterResource(mode.icon), contentDescription = null) },
                        selected = mode == selected,
                        onDismiss = onDismiss,
                        onClick = { onSelect(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewModeButtons(selected: ViewMode, onSelect: (ViewMode) -> Unit, labelStyle: TextStyle) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ViewMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, ViewMode.entries.size),
                // the icon is part of the label, so it stays centered next to a label that grows with the font
                icon = {},
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (mode == selected) {
                                rememberVectorPainter(Icons.Filled.Check)
                            } else {
                                painterResource(mode.icon)
                            },
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(mode.label), style = labelStyle, maxLines = 1, softWrap = false)
                    }
                },
            )
        }
    }
}

/**
 * The [style] with the largest font size, down to [MIN_LABEL_FONT_SIZE], at which all [labels] fit into [maxWidth].
 * Unlike auto sizing each label, all of them keep the same size.
 */
@Composable
private fun fittingStyle(labels: List<String>, style: TextStyle, maxWidth: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val maxWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
    return remember(labels, style, maxWidthPx) {
        var fontSize = style.fontSize
        fun fits() = labels.all {
            measurer.measure(it, style.copy(fontSize = fontSize), maxLines = 1).size.width <= maxWidthPx
        }
        while (fontSize > MIN_LABEL_FONT_SIZE && !fits()) fontSize *= LABEL_SHRINK_FACTOR
        style.copy(fontSize = fontSize)
    }
}

internal enum class PageIndicatorStyle { DOTS, NUMBERED_DOTS }

/**
 * A row of dots below the horizontally swiped pages, or a page count for long documents and when the dots
 * don't fit, e.g. in split screen on a narrow phone or with a large font. Tapping a dot selects its page.
 */
@Composable
internal fun PageIndicator(
    current: Int,
    count: Int,
    label: String,
    numbered: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        // numbered dots grow with the font size
        val dot = if (numbered) numberedDotSize() else DOT_SIZE
        val spacing = if (numbered) NUMBERED_DOT_SPACING else DOT_SPACING
        val padding = if (numbered) NUMBERED_DOTS_PADDING else DOTS_PADDING
        val dotsWidth = dot * count + spacing * (count - 1) + padding * 2
        val showDots = count <= MAX_PAGE_DOTS && dotsWidth <= maxWidth
        val onSelectState = rememberUpdatedState(onSelect)
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val tap = if (showDots) {
            Modifier.pointerInput(count, dot, spacing, padding, rtl) {
                detectTapGestures { offset ->
                    // each dot owns half of the spacing on both sides, the outer dots also the padding
                    val x = if (rtl) size.width - offset.x else offset.x
                    val slot = (dot + spacing).toPx()
                    val index = ((x - padding.toPx() + spacing.toPx() / 2) / slot).toInt()
                    onSelectState.value(index.coerceIn(0, count - 1))
                }
            }
        } else {
            Modifier
        }
        Box(tap.padding(vertical = INDICATOR_TAP_PADDING)) {
            PageIndicatorContent(current, count, label, numbered, showDots)
        }
    }
}

/** About the largest height of a [PageIndicator], to leave space for it. */
@Composable
internal fun pageIndicatorHeight(): Dp = INDICATOR_TAP_PADDING * 2 + numberedDotSize() + NUMBERED_DOTS_PADDING * 2

@Composable
private fun numberedDotSize(): Dp = max(NUMBERED_DOT_SIZE, NUMBER_LINE_HEIGHT * LocalDensity.current.fontScale)

@Composable
private fun PageIndicatorContent(current: Int, count: Int, label: String, numbered: Boolean, showDots: Boolean) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
    ) {
        if (!showDots) {
            Text(
                text = "${current + 1}/$count",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        } else Row(
            horizontalArrangement = Arrangement.spacedBy(if (numbered) NUMBERED_DOT_SPACING else DOT_SPACING),
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (numbered) {
                Modifier.padding(NUMBERED_DOTS_PADDING)
            } else {
                Modifier.padding(horizontal = DOTS_PADDING, vertical = 6.dp)
            },
        ) {
            repeat(count) { index ->
                val active = index == current
                val dot = Modifier
                    // stays round when the numbers grow with the font size
                    .size(if (numbered) numberedDotSize() else DOT_SIZE)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else if (numbered) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                Box(contentAlignment = Alignment.Center, modifier = dot) {
                    if (numbered) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Shown instead of a page or document that couldn't be rendered. */
@Composable
internal fun RenderError(@StringRes message: Int) {
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(24.dp),
    )
}
