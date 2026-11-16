package me.d3sox.pdfwallet.documents

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.documents.tags.AssignTagsDialog
import me.d3sox.pdfwallet.quicksettings.removeQuickSettingsTile
import me.d3sox.pdfwallet.quicksettings.requestQuickSettingsTile
import me.d3sox.pdfwallet.reminders.rememberNotificationPermissionRequest
import me.d3sox.pdfwallet.ui.LocalSnackbarHostState
import me.d3sox.pdfwallet.ui.components.ConfirmDialog
import me.d3sox.pdfwallet.ui.components.ExpressiveMenu
import me.d3sox.pdfwallet.ui.components.MenuScope
import me.d3sox.pdfwallet.ui.components.TextInputDialog
import me.d3sox.pdfwallet.ui.components.action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A dialog for the document with [documentId], which stays open when the screen rotates with [Saver]. */
data class DocumentDialog(val kind: Kind, val documentId: String) {
    enum class Kind { RENAME, NOTE, ASSIGN_TAGS, ARCHIVE_DATE, DELETE }

    companion object {
        val Saver = Saver<DocumentDialog?, String>(
            save = { dialog -> dialog?.let { "${it.kind.name}/${it.documentId}" }.orEmpty() },
            restore = { saved ->
                saved.split('/', limit = 2).takeIf { it.size == 2 }?.let { (kind, id) ->
                    DocumentDialog(Kind.valueOf(kind), id)
                }
            },
        )
    }
}

/**
 * The menu for a single document, followed by the groups of [extraGroups].
 * Picking a document for the Quick Settings tile offers to add the tile,
 * or shows how to in the snackbar host of the screen.
 * [editor] archives the document right away.
 * [onDialog] opens the matching [DocumentDialog].
 * Without [showShare], sharing is left to a button next to the menu.
 */
@Composable
fun DocumentMenu(
    document: Document,
    editor: DocumentEditor,
    /** whether the Quick Settings tile opens the document */
    isInQuickSettings: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDialog: (DocumentDialog) -> Unit,
    showShare: Boolean = true,
    extraGroups: MenuScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canAddToHomeScreen = remember { context.canAddToHomeScreen() }
    val snackbarHostState = LocalSnackbarHostState.current
    val tileHint = stringResource(R.string.quick_settings_tile_hint)
    val open = { kind: DocumentDialog.Kind -> onDialog(DocumentDialog(kind, document.id)) }
    ExpressiveMenu(expanded = expanded, onDismissRequest = onDismiss) {
        group {
            action(R.string.rename, R.drawable.edit_24px, onDismiss) { open(DocumentDialog.Kind.RENAME) }
            action(R.string.note, R.drawable.sticky_note_2_24px, onDismiss) { open(DocumentDialog.Kind.NOTE) }
            action(R.string.tags, R.drawable.tag_24, onDismiss) { open(DocumentDialog.Kind.ASSIGN_TAGS) }
        }
        group {
            if (showShare) {
                action(R.string.share, R.drawable.share_24px, onDismiss) { context.sharePdfs(listOf(document)) }
            }
            if (canAddToHomeScreen) {
                action(R.string.add_to_home_screen, R.drawable.add_to_home_screen_24, onDismiss) {
                    // the launcher can take a while to answer
                    scope.launch(Dispatchers.IO) { context.addToHomeScreen(document) }
                }
            }
            if (isInQuickSettings) {
                action(R.string.remove_from_quick_settings, R.drawable.toggle_on_24px, onDismiss) {
                    editor.setQuickSettingsDocument(null)
                    context.removeQuickSettingsTile()
                }
            } else {
                action(R.string.add_to_quick_settings, R.drawable.toggle_on_24px, onDismiss) {
                    val onAdded: () -> Unit = { editor.setQuickSettingsDocument(document.id) }
                    if (!context.requestQuickSettingsTile(document.name, onAdded)) {
                        // the tile can't be offered, so it gets the document for when it's added
                        onAdded()
                        scope.launch { snackbarHostState.showSnackbar(tileHint) }
                    }
                }
            }
        }
        group {
            if (document.archived) {
                action(R.string.unarchive, R.drawable.unarchive_24px, onDismiss) {
                    editor.setArchived(listOf(document.id), archived = false)
                }
            } else {
                action(R.string.archive, R.drawable.archive_24px, onDismiss) {
                    editor.setArchived(listOf(document.id), archived = true)
                }
                action(R.string.archive_on_date, R.drawable.event_upcoming_24px, onDismiss) {
                    open(DocumentDialog.Kind.ARCHIVE_DATE)
                }
            }
            action(R.string.delete, R.drawable.delete_24, onDismiss) { open(DocumentDialog.Kind.DELETE) }
        }
        extraGroups()
    }
}

/**
 * Shows [dialog]. The document is looked up in [documents], so the dialog reflects changes while it's open,
 * and closes once the document is deleted.
 */
@Composable
fun DocumentDialogHost(
    dialog: DocumentDialog?,
    documents: List<Document>,
    tags: List<Tag>,
    editor: DocumentEditor,
    onDismiss: () -> Unit,
) {
    // outside the dialog, which closes before the permission is granted
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    dialog ?: return
    val document = documents.firstOrNull { it.id == dialog.documentId } ?: return
    when (dialog.kind) {
        DocumentDialog.Kind.RENAME -> TextInputDialog(
            title = stringResource(R.string.dialog_document_name_title),
            label = stringResource(R.string.document_name),
            initialValue = document.name,
            onConfirm = { editor.rename(document.id, it) },
            onDismiss = onDismiss,
        )
        DocumentDialog.Kind.NOTE -> TextInputDialog(
            title = stringResource(R.string.note),
            label = stringResource(R.string.note_hint),
            initialValue = document.note,
            onConfirm = { editor.setNote(document.id, it) },
            onDismiss = onDismiss,
            singleLine = false,
            required = false,
        )
        DocumentDialog.Kind.ASSIGN_TAGS -> AssignTagsDialog(
            tags = tags,
            assignedTagIds = document.tags.map { it.id }.toSet(),
            onAssignedTagsChange = { editor.setTags(document.id, it) },
            onCreateTag = { name, icon -> editor.createTag(name, icon, assignTo = document) },
            onDismiss = onDismiss,
        )
        DocumentDialog.Kind.ARCHIVE_DATE -> ArchiveDateDialog(
            archiveAt = document.archiveAt,
            onConfirm = {
                editor.setArchiveAt(document.id, it)
                // for the reminder the day before
                if (it != null) requestNotificationPermission()
            },
            onDismiss = onDismiss,
        )
        DocumentDialog.Kind.DELETE -> ConfirmDialog(
            text = stringResource(R.string.dialog_delete_confirmation_message),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { editor.delete(listOf(document.id)) },
            onDismiss = onDismiss,
        )
    }
}
