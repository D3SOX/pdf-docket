package me.d3sox.pdfwallet.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.R

private const val MAX_PAGE_DOTS = 10

internal enum class ViewMode(val label: Int, val icon: Int) {
    BARCODE(R.string.view_barcode, R.drawable.qr_code_24),
    PDF(R.string.view_pdf, R.drawable.picture_as_pdf_24),
    BOTH(R.string.view_both, R.drawable.splitscreen_24),
}

@Composable
internal fun ViewModeBar(selected: ViewMode, onSelect: (ViewMode) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ViewMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ViewMode.entries.size),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = mode == selected) {
                            Icon(
                                painterResource(mode.icon),
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                            )
                        }
                    },
                    label = { Text(stringResource(mode.label)) },
                )
            }
        }
    }
}

/**
 * A vertical row of dots next to the vertically swiped pages, or a page count for long documents.
 */
@Composable
internal fun PageIndicator(current: Int, count: Int, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        if (count > MAX_PAGE_DOTS) {
            Text(
                text = "${current + 1}/$count",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        } else Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
        ) {
            repeat(count) { index ->
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        ),
                )
            }
        }
    }
}
