package me.d3sox.pdfwallet.documents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import android.graphics.Bitmap
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.documents.tags.iconDrawable

private const val PREVIEW_ASPECT_RATIO = 4f / 3f
private val LIST_PREVIEW_WIDTH = 96.dp
private const val MAX_TAG_CHIPS = 3
private val TAG_CHIP_MAX_WIDTH = 120.dp

/**
 * A grid tile with a preview of the first page above the document name and tags,
 * or a list item with the preview next to them.
 */
@Composable
@Suppress("LongParameterList")
fun DocumentTile(
    document: Document,
    loadThumbnail: suspend (String) -> Bitmap?,
    onClick: () -> Unit,
    onDialog: (DocumentDialog) -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    asListItem: Boolean = false,
) {
    OutlinedCard(
        colors = if (isSelected) {
            CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.outlinedCardColors()
        },
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.select_document),
                role = if (selectionMode) Role.Checkbox else Role.Button,
            )
            .semantics { if (selectionMode) selected = isSelected },
    ) {
        if (asListItem) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Preview(document.id, loadThumbnail, Modifier.width(LIST_PREVIEW_WIDTH))
                NameTagsAndActions(document, onDialog, selectionMode, isSelected, minNameLines = 1, showTags = true)
            }
        } else {
            // the tags are on the preview, which keeps its size, so they don't change the height of the tile
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(PREVIEW_ASPECT_RATIO)
                    .clipToBounds()
            ) {
                Preview(document.id, loadThumbnail, Modifier.fillMaxSize())
                TagChips(
                    document.tags,
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                // reserves the same height in every tile, so the grid rows line up
                NameTagsAndActions(document, onDialog, selectionMode, isSelected, minNameLines = 2, showTags = false)
            }
        }
    }
}

@Composable
private fun RowScope.NameTagsAndActions(
    document: Document,
    onDialog: (DocumentDialog) -> Unit,
    selectionMode: Boolean,
    isSelected: Boolean,
    minNameLines: Int,
    showTags: Boolean,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(start = 12.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Text(
            text = document.name,
            style = MaterialTheme.typography.titleSmall,
            minLines = minNameLines,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // an empty line would push the name off center
        if (showTags && document.tags.isNotEmpty()) TagsLine(document.tags)
    }
    if (selectionMode) {
        Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
    } else Box {
        var showMenu by remember { mutableStateOf(false) }
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DocumentMenu(document = document, expanded = showMenu, onDismiss = { showMenu = false }, onDialog = onDialog)
    }
}

/**
 * The first [MAX_TAG_CHIPS] tags as chips, and how many more there are.
 */
@Composable
private fun TagChips(tags: List<Tag>, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        tags.take(MAX_TAG_CHIPS).forEach { tag ->
            TagChip(tag.name, tag.iconDrawable)
        }
        if (tags.size > MAX_TAG_CHIPS) {
            TagChip("+${tags.size - MAX_TAG_CHIPS}", icon = null)
        }
    }
}

@Composable
private fun TagChip(text: String, icon: Int?) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = TAG_CHIP_MAX_WIDTH)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            if (icon != null) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(14.dp),
                )
            }
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Preview(id: String, loadThumbnail: suspend (String) -> Bitmap?, modifier: Modifier) {
    val thumbnail by produceState<ImageBitmap?>(null, id) {
        value = loadThumbnail(id)?.asImageBitmap()
    }
    Box(
        modifier = modifier
            .aspectRatio(PREVIEW_ASPECT_RATIO)
            .background(Color.White),
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The tag names in one line, each after its icon.
 */
@Composable
private fun TagsLine(tags: List<Tag>) {
    val icons = tags.map { it.iconDrawable }.distinct()
    Text(
        text = buildAnnotatedString {
            tags.forEachIndexed { index, tag ->
                if (index > 0) append(" · ")
                appendInlineContent(tag.iconDrawable.toString())
                append(" ")
                append(tag.name)
            }
        },
        inlineContent = icons.associate { icon ->
            icon.toString() to InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
                Icon(painterResource(icon), contentDescription = null)
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
