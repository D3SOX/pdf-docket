package me.d3sox.pdfwallet.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import me.d3sox.pdfwallet.R

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

/**
 * Shows [first] above [second], or next to each other in landscape.
 */
@Composable
internal fun SplitLayout(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { first() }
                VerticalDivider()
                Box(Modifier.weight(1f)) { second() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { first() }
                HorizontalDivider()
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

internal enum class PageIndicatorStyle { DOTS, NUMBERED_DOTS }

/**
 * A row of dots below the horizontally swiped pages, or a page count for long documents and when the dots
 * don't fit, e.g. in split screen on a narrow phone or with a large font.
 */
@Composable
internal fun PageIndicator(current: Int, count: Int, label: String, numbered: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val showDots = count <= MAX_PAGE_DOTS && dotsWidth(count, numbered) <= maxWidth
        PageIndicatorContent(current, count, label, numbered, showDots)
    }
}

@Composable
private fun numberedDotSize(): Dp = max(NUMBERED_DOT_SIZE, NUMBER_LINE_HEIGHT * LocalDensity.current.fontScale)

/**
 * The width of the dots including the padding around them. Numbered dots grow with the font size.
 */
@Composable
private fun dotsWidth(count: Int, numbered: Boolean): Dp {
    val dot = if (numbered) numberedDotSize() else DOT_SIZE
    val spacing = if (numbered) NUMBERED_DOT_SPACING else DOT_SPACING
    val padding = if (numbered) NUMBERED_DOTS_PADDING else DOTS_PADDING
    return dot * count + spacing * (count - 1) + padding * 2
}

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
