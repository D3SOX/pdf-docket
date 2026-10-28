@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package me.d3sox.pdfwallet.documents.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.documents.Document
import me.d3sox.pdfwallet.ui.components.ConfirmDialog
import me.d3sox.pdfwallet.ui.components.segmentedColorsWithoutSelection
import me.d3sox.pdfwallet.ui.components.withoutSelection

@Composable
fun BulkTagsDialog(
    tags: List<Tag>,
    documents: List<Document>,
    onConfirm: (Set<Long>, Set<Long>) -> Unit,
    onCreateTag: (String, TagIcon?) -> Unit,
    onDismiss: () -> Unit,
) {
    var changes by rememberSaveable { mutableStateOf(emptyMap<Long, Boolean>()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_assign_tags_title)) },
        text = {
            TagList(tags) { tag, shapes ->
                val assignedCount = documents.count { document -> document.tags.any { it.id == tag.id } }
                val state = when (changes[tag.id]) {
                    true -> ToggleableState.On
                    false -> ToggleableState.Off
                    null -> when (assignedCount) {
                        0 -> ToggleableState.Off
                        documents.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }
                }
                ListItem(
                    onClick = { changes = changes + (tag.id to (state != ToggleableState.On)) },
                    shapes = shapes,
                    colors = segmentedColorsWithoutSelection(),
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = { TriStateCheckbox(state = state, onClick = null) },
                    modifier = Modifier.semantics {
                        role = Role.Checkbox
                        toggleableState = state
                    },
                ) {
                    TagLabel(tag)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val validChanges = changes.filterKeys { id -> tags.any { it.id == id } }
                onConfirm(validChanges.filterValues { it }.keys, validChanges.filterValues { !it }.keys)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                AddTagButton { showCreateDialog = true }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
    if (showCreateDialog) {
        TagEditorDialog(
            title = stringResource(R.string.add_tag_dialog_text),
            onConfirm = onCreateTag,
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
fun AssignTagsDialog(
    tags: List<Tag>,
    assignedTagIds: Set<Long>,
    onAssignedTagsChange: (Set<Long>) -> Unit,
    onCreateTag: (String, TagIcon?) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_assign_tags_title)) },
        text = {
            TagList(tags) { tag, shapes ->
                val isAssigned = tag.id in assignedTagIds
                ListItem(
                    checked = isAssigned,
                    onCheckedChange = {
                        onAssignedTagsChange(if (it) assignedTagIds + tag.id else assignedTagIds - tag.id)
                    },
                    shapes = shapes,
                    colors = segmentedColorsWithoutSelection(),
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = { Checkbox(checked = isAssigned, onCheckedChange = null) },
                ) {
                    TagLabel(tag)
                }
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
        TagEditorDialog(
            title = stringResource(R.string.add_tag_dialog_text),
            onConfirm = onCreateTag,
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
fun ManageTagsDialog(
    tags: List<Tag>,
    onCreateTag: (String, TagIcon?) -> Unit,
    onEditTag: (Tag, String, TagIcon?) -> Unit,
    onDeleteTag: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<Tag?>(null) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_item_manage_tags)) },
        text = {
            TagList(tags) { tag, shapes ->
                ListItem(
                    shapes = shapes,
                    colors = segmentedColorsWithoutSelection(),
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = { TagIconView(tag, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { tagToEdit = tag }) {
                                Icon(
                                    painterResource(R.drawable.edit_24px),
                                    contentDescription = stringResource(R.string.edit_tag),
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
                ) {
                    Text(tag.name)
                }
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
        TagEditorDialog(
            title = stringResource(R.string.add_tag_dialog_text),
            onConfirm = onCreateTag,
            onDismiss = { showCreateDialog = false },
        )
    }
    tagToEdit?.let { tag ->
        TagEditorDialog(
            title = stringResource(R.string.edit_tag),
            initialName = tag.name,
            initialIcon = tag.tagIcon,
            onConfirm = { name, icon -> onEditTag(tag, name, icon) },
            onDismiss = { tagToEdit = null },
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
private fun TagList(tags: List<Tag>, item: @Composable (Tag, ListItemShapes) -> Unit) {
    if (tags.isEmpty()) {
        Text(
            stringResource(R.string.no_tags),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
        ) {
            itemsIndexed(tags, key = { _, tag -> tag.id }) { index, tag ->
                item(tag, ListItemDefaults.segmentedShapes(index, tags.size).withoutSelection())
            }
        }
    }
}

@Composable
private fun TagLabel(tag: Tag) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TagIconView(
            tag,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(20.dp),
        )
        Text(tag.name)
    }
}

@Composable
private fun AddTagButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.add_tag_dialog_text))
    }
}

/**
 * Asks for the name and the icon of a new or an existing tag.
 */
@Composable
private fun TagEditorDialog(
    title: String,
    onConfirm: (String, TagIcon?) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialIcon: TagIcon? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var icon by rememberSaveable { mutableStateOf(initialIcon) }
    val isValid = name.isNotBlank()
    val confirm = {
        if (isValid) {
            onConfirm(name.trim(), icon)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_tag_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.tag_icon),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconPicker(selected = icon, onSelect = { icon = it })
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = isValid) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun IconPicker(selected: TagIcon?, onSelect: (TagIcon?) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilledTonalIconToggleButton(checked = selected == null, onCheckedChange = { onSelect(null) }) {
            Icon(painterResource(R.drawable.tag_24), contentDescription = stringResource(R.string.default_tag_icon))
        }
        TagIcon.entries.forEach { icon ->
            FilledTonalIconToggleButton(checked = icon == selected, onCheckedChange = { onSelect(icon) }) {
                Icon(painterResource(icon.drawable), contentDescription = stringResource(icon.label))
            }
        }
    }
}
