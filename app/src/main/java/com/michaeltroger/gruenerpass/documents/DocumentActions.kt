package com.michaeltroger.gruenerpass.documents

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.michaeltroger.gruenerpass.R
import com.michaeltroger.gruenerpass.db.Tag
import com.michaeltroger.gruenerpass.documents.tags.AssignTagsDialog
import com.michaeltroger.gruenerpass.ui.components.ConfirmDialog
import com.michaeltroger.gruenerpass.ui.components.TextInputDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface DocumentDialog {
    val document: Document

    data class Rename(override val document: Document) : DocumentDialog
    data class AssignTags(override val document: Document) : DocumentDialog
    data class Delete(override val document: Document) : DocumentDialog
}

/**
 * Menu entries for a single document. [onDialog] opens the matching [DocumentDialog].
 */
@Composable
fun DocumentMenuItems(
    document: Document,
    onDialog: (DocumentDialog) -> Unit,
    onDismissMenu: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DropdownMenuItem(
        text = { Text(stringResource(R.string.rename)) },
        leadingIcon = { Icon(painterResource(R.drawable.edit_24px), contentDescription = null) },
        onClick = {
            onDismissMenu()
            onDialog(DocumentDialog.Rename(document))
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.tags)) },
        leadingIcon = { Icon(painterResource(R.drawable.tag_24), contentDescription = null) },
        onClick = {
            onDismissMenu()
            onDialog(DocumentDialog.AssignTags(document))
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.share)) },
        leadingIcon = { Icon(painterResource(R.drawable.share_24px), contentDescription = null) },
        onClick = {
            onDismissMenu()
            context.sharePdfs(listOf(document))
        },
    )
    if (remember { context.canAddToHomeScreen() }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_to_home_screen)) },
            leadingIcon = { Icon(painterResource(R.drawable.add_to_home_screen_24), contentDescription = null) },
            onClick = {
                onDismissMenu()
                // the launcher can take a while to answer
                scope.launch(Dispatchers.IO) { context.addToHomeScreen(document) }
            },
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.delete)) },
        leadingIcon = { Icon(painterResource(R.drawable.delete_24), contentDescription = null) },
        onClick = {
            onDismissMenu()
            onDialog(DocumentDialog.Delete(document))
        },
    )
}

/**
 * Shows [dialog]. The document is looked up in [documents], so the dialog reflects changes while it's open.
 */
@Composable
fun DocumentDialogHost(
    dialog: DocumentDialog?,
    documents: List<Document>,
    tags: List<Tag>,
    editor: DocumentEditor,
    onDismiss: () -> Unit,
) {
    dialog ?: return
    val document = documents.firstOrNull { it.id == dialog.document.id } ?: dialog.document
    when (dialog) {
        is DocumentDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.dialog_document_name_title),
            label = stringResource(R.string.document_name),
            initialValue = document.name,
            onConfirm = { editor.rename(document.id, it) },
            onDismiss = onDismiss,
        )
        is DocumentDialog.AssignTags -> AssignTagsDialog(
            tags = tags,
            assignedTagIds = document.tags.map { it.id }.toSet(),
            onAssignedTagsChange = { editor.setTags(document.id, it) },
            onCreateTag = { editor.createTag(it, assignTo = document) },
            onDismiss = onDismiss,
        )
        is DocumentDialog.Delete -> ConfirmDialog(
            text = stringResource(R.string.dialog_delete_confirmation_message),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { editor.delete(listOf(document.id)) },
            onDismiss = onDismiss,
        )
    }
}
