package me.d3sox.pdfwallet.documents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import android.graphics.Bitmap
import android.text.format.DateUtils
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.documents.tags.iconDrawable
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date

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
    // the other layout's tiles show up with their previews during the switch
    cachedThumbnail: (String) -> Bitmap?,
    onClick: () -> Unit,
    onDialog: (DocumentDialog) -> Unit,
    editor: DocumentEditor,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    /** shown next to the list */
    isOpen: Boolean = false,
    isInQuickSettings: Boolean = false,
    isDragging: Boolean = false,
    onLongClick: () -> Unit = {},
    asListItem: Boolean = false,
    // inside the clickable, which would take the pointer events after a long press otherwise
    dragHandle: Modifier = Modifier,
    previewModifier: Modifier = Modifier,
) {
    OutlinedCard(
        colors = if (isSelected || isOpen) {
            CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.outlinedCardColors()
        },
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = if (isDragging) 8.dp else 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.select_document),
                role = if (selectionMode) Role.Checkbox else Role.Button,
            )
            .then(dragHandle)
            .semantics { selected = if (selectionMode) isSelected else isOpen },
    ) {
        if (asListItem) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Preview(document.id, loadThumbnail, cachedThumbnail, previewModifier.width(LIST_PREVIEW_WIDTH))
                NameTagsAndActions(
                    document, onDialog, editor, selectionMode, isSelected, isInQuickSettings, asListItem = true,
                )
            }
        } else {
            // the tags are on the preview, which keeps its size, so they don't change the height of the tile
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(PREVIEW_ASPECT_RATIO)
                    .clipToBounds()
            ) {
                Preview(document.id, loadThumbnail, cachedThumbnail, previewModifier.fillMaxSize())
                TagChips(
                    document.tags,
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
                DateChips(
                    document,
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NameTagsAndActions(
                    document, onDialog, editor, selectionMode, isSelected, isInQuickSettings, asListItem = false,
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun RowScope.NameTagsAndActions(
    document: Document,
    onDialog: (DocumentDialog) -> Unit,
    editor: DocumentEditor,
    selectionMode: Boolean,
    isSelected: Boolean,
    isInQuickSettings: Boolean,
    // grid tiles show the tags, the reminder and the archive date on the preview
    asListItem: Boolean,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(start = 12.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            // reserves the lines, so a shorter name is centered in them,
            // and in a grid the same height in every tile, so the rows line up
            Text(text = "", style = MaterialTheme.typography.titleSmall, minLines = if (asListItem) 1 else 2)
            Text(
                text = document.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // an empty line would push the name off center
        if (asListItem && document.note.isNotEmpty()) NoteLine(document.note)
        if (asListItem && document.tags.isNotEmpty()) TagsLine(document.tags)
        if (asListItem) DateChips(document, Modifier.padding(top = 4.dp), inRow = true)
    }
    if (selectionMode) {
        Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
    } else Box {
        var showMenu by remember { mutableStateOf(false) }
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DocumentMenu(
            document = document,
            editor = editor,
            isInQuickSettings = isInQuickSettings,
            expanded = showMenu,
            onDismiss = { showMenu = false },
            onDialog = onDialog,
        )
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

/**
 * When the document reminds and gets archived automatically, below each other,
 * or with [inRow] next to each other, wrapping if they don't fit.
 */
@Composable
private fun DateChips(document: Document, modifier: Modifier, inRow: Boolean = false) {
    if (document.reminder == null && document.archiveAt == null) return
    val chips = @Composable {
        document.reminder?.let { ReminderChip(it, Modifier) }
        document.archiveAt?.let { ArchiveDateChip(it, Modifier) }
    }
    if (inRow) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier,
        ) { chips() }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) { chips() }
    }
}

/**
 * When the document reminds, with the time if there is one.
 */
@Composable
private fun ReminderChip(reminder: Reminder, modifier: Modifier) {
    val context = LocalContext.current
    val text = remember(reminder) {
        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or
            if (reminder.time != null) DateUtils.FORMAT_SHOW_TIME else 0
        DateUtils.formatDateTime(context, reminder.at(ZoneId.systemDefault()), flags)
    }
    val description = stringResource(R.string.reminds_on, text)
    TagChip(
        text = text,
        icon = R.drawable.notifications_24px,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        maxWidth = Dp.Infinity,
    )
}

/**
 * When the document gets archived automatically.
 */
@Composable
private fun ArchiveDateChip(archiveAt: Long, modifier: Modifier) {
    val date = remember(archiveAt) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(archiveAt)) }
    val description = stringResource(R.string.archives_on, date)
    TagChip(
        text = date,
        icon = R.drawable.event_upcoming_24px,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        maxWidth = Dp.Infinity,
    )
}

@Composable
private fun TagChip(
    text: String,
    icon: Int?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    // tag names can be long, dates are short enough to show in full
    maxWidth: Dp = TAG_CHIP_MAX_WIDTH,
) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.92f),
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = maxWidth)
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
private fun Preview(
    id: String,
    loadThumbnail: suspend (String) -> Bitmap?,
    cachedThumbnail: (String) -> Bitmap?,
    modifier: Modifier,
) {
    val cached = remember(id) { cachedThumbnail(id)?.asImageBitmap() }
    val thumbnail by produceState(cached, id) {
        value = cached ?: loadThumbnail(id)?.asImageBitmap()
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

@Composable
private fun NoteLine(note: String) {
    Text(
        text = note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
