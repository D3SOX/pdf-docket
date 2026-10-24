package me.d3sox.pdfwallet.documents

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import me.d3sox.pdfwallet.MainActivity
import me.d3sox.pdfwallet.R

// Home screen shortcuts keep these after app updates, so they must never change
const val ACTION_OPEN_DOCUMENT = "me.d3sox.pdfwallet.OPEN_DOCUMENT"
const val EXTRA_DOCUMENT_ID = "documentId"

// launchers can only pin shortcuts since Android 8, which also introduced adaptive icons
private val shortcutIcon =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) R.mipmap.ic_shortcut_document else R.mipmap.ic_launcher

private fun shortcutId(documentId: String) = "document-$documentId"

private fun Context.documentShortcut(id: String, name: String) = ShortcutInfoCompat.Builder(this, shortcutId(id))
    .setShortLabel(name)
    .setIcon(IconCompat.createWithResource(this, shortcutIcon))
    .setIntent(
        Intent(this, MainActivity::class.java)
            .setAction(ACTION_OPEN_DOCUMENT)
            .putExtra(EXTRA_DOCUMENT_ID, id)
    )
    .build()

fun Context.canAddToHomeScreen(): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(this)

fun Context.addToHomeScreen(document: Document) {
    ShortcutManagerCompat.requestPinShortcut(this, documentShortcut(document.id, document.name), null)
}

/** Updates the label of the document's home screen shortcut, if there is one. */
fun Context.renameShortcut(id: String, name: String) {
    ShortcutManagerCompat.updateShortcuts(this, listOf(documentShortcut(id, name)))
}

/** Apps can't remove home screen shortcuts, so the ones of deleted documents are greyed out. */
fun Context.disableShortcuts(ids: List<String>) {
    ShortcutManagerCompat.disableShortcuts(
        this,
        ids.map(::shortcutId),
        getString(R.string.shortcut_document_deleted),
    )
}
