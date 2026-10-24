package me.d3sox.pdfwallet.documents.tags

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.ui.components.ConfirmDialog
import me.d3sox.pdfwallet.ui.components.TextInputDialog

private val transparentListItem
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
fun AssignTagsDialog(
    tags: List<Tag>,
    assignedTagIds: Set<Long>,
    onAssignedTagsChange: (Set<Long>) -> Unit,
    onCreateTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_assign_tags_title)) },
        text = {
            TagList(tags) { tag ->
                val isAssigned = tag.id in assignedTagIds
                ListItem(
                    headlineContent = { Text(tag.name) },
                    leadingContent = { Checkbox(checked = isAssigned, onCheckedChange = null) },
                    colors = transparentListItem,
                    modifier = Modifier.toggleable(
                        value = isAssigned,
                        role = Role.Checkbox,
                        onValueChange = {
                            onAssignedTagsChange(if (it) assignedTagIds + tag.id else assignedTagIds - tag.id)
                        },
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            AddTagButton { showCreateDialog = true }
        },
    )
    if (showCreateDialog) {
        CreateTagDialog(onCreateTag = onCreateTag, onDismiss = { showCreateDialog = false })
    }
}

@Composable
fun ManageTagsDialog(
    tags: List<Tag>,
    onCreateTag: (String) -> Unit,
    onRenameTag: (Long, String) -> Unit,
    onDeleteTag: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var tagToRename by remember { mutableStateOf<Tag?>(null) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_item_manage_tags)) },
        text = {
            TagList(tags) { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { tagToRename = tag }) {
                                Icon(
                                    painterResource(R.drawable.edit_24px),
                                    contentDescription = stringResource(R.string.rename_tag_dialog_text),
                                )
                            }
                            IconButton(onClick = { tagToDelete = tag }) {
                                Icon(
                                    painterResource(R.drawable.delete_24),
                                    contentDescription = stringResource(R.string.delete),
                                )
                            }
                        }
                    },
                    colors = transparentListItem,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            AddTagButton { showCreateDialog = true }
        },
    )
    if (showCreateDialog) {
        CreateTagDialog(onCreateTag = onCreateTag, onDismiss = { showCreateDialog = false })
    }
    tagToRename?.let { tag ->
        TextInputDialog(
            title = stringResource(R.string.rename_tag_dialog_text),
            label = stringResource(R.string.dialog_tag_name_hint),
            initialValue = tag.name,
            onConfirm = { onRenameTag(tag.id, it) },
            onDismiss = { tagToRename = null },
        )
    }
    tagToDelete?.let { tag ->
        ConfirmDialog(
            title = tag.name,
            text = stringResource(R.string.delete_tag_confirmation),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { onDeleteTag(tag.id) },
            onDismiss = { tagToDelete = null },
        )
    }
}

@Composable
private fun TagList(tags: List<Tag>, item: @Composable (Tag) -> Unit) {
    if (tags.isEmpty()) {
        Text(
            stringResource(R.string.no_tags),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(tags, key = { it.id }) { item(it) }
        }
    }
}

@Composable
private fun AddTagButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.add_tag_dialog_text))
    }
}

@Composable
private fun CreateTagDialog(onCreateTag: (String) -> Unit, onDismiss: () -> Unit) {
    TextInputDialog(
        title = stringResource(R.string.add_tag_dialog_text),
        label = stringResource(R.string.dialog_tag_name_hint),
        onConfirm = onCreateTag,
        onDismiss = onDismiss,
    )
}
