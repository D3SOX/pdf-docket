package me.d3sox.pdfwallet.documents

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.R

private const val PREVIEW_ASPECT_RATIO = 4f / 3f

/**
 * A grid tile with a preview of the first page above the document name and tags.
 */
@Composable
fun DocumentTile(
    document: Document,
    loadThumbnail: suspend (String) -> Bitmap?,
    onClick: () -> Unit,
    onDialog: (DocumentDialog) -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
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
        Preview(document.id, loadThumbnail)
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 10.dp, bottom = 12.dp),
            ) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.titleSmall,
                    // reserves the same height in every tile, so the grid rows line up
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = document.tags.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            } else Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DocumentMenuItems(
                        document = document,
                        onDialog = onDialog,
                        onDismissMenu = { showMenu = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun Preview(id: String, loadThumbnail: suspend (String) -> Bitmap?) {
    val thumbnail by produceState<ImageBitmap?>(null, id) {
        value = loadThumbnail(id)?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
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
