package me.d3sox.pdfwallet.documents

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.documents.tags.AssignTagsDialog
import me.d3sox.pdfwallet.ui.components.ConfirmDialog
import me.d3sox.pdfwallet.ui.components.ExpressiveMenu
import me.d3sox.pdfwallet.ui.components.MenuScope
import me.d3sox.pdfwallet.ui.components.TextInputDialog
import me.d3sox.pdfwallet.ui.components.action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface DocumentDialog {
    val document: Document

    data class Rename(override val document: Document) : DocumentDialog
    data class AssignTags(override val document: Document) : DocumentDialog
    data class Delete(override val document: Document) : DocumentDialog
}

/**
 * The menu for a single document, followed by the groups of [extraGroups].
 * [onDialog] opens the matching [DocumentDialog].
 */
@Composable
fun DocumentMenu(
    document: Document,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDialog: (DocumentDialog) -> Unit,
    extraGroups: MenuScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canAddToHomeScreen = remember { context.canAddToHomeScreen() }
    ExpressiveMenu(expanded = expanded, onDismissRequest = onDismiss) {
        group {
            action(R.string.rename, R.drawable.edit_24px, onDismiss) { onDialog(DocumentDialog.Rename(document)) }
            action(R.string.tags, R.drawable.tag_24, onDismiss) { onDialog(DocumentDialog.AssignTags(document)) }
        }
        group {
            action(R.string.share, R.drawable.share_24px, onDismiss) { context.sharePdfs(listOf(document)) }
            if (canAddToHomeScreen) {
                action(R.string.add_to_home_screen, R.drawable.add_to_home_screen_24, onDismiss) {
                    // the launcher can take a while to answer
                    scope.launch(Dispatchers.IO) { context.addToHomeScreen(document) }
                }
            }
        }
        group {
            action(R.string.delete, R.drawable.delete_24, onDismiss) { onDialog(DocumentDialog.Delete(document)) }
        }
        extraGroups()
    }
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
            onCreateTag = { name, icon -> editor.createTag(name, icon, assignTo = document) },
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
