package me.d3sox.pdfwallet.documents

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.settings.AppSettings
import me.d3sox.pdfwallet.settings.SortOrder

/**
 * Explains how to change the order, the first time the manual sort order is used.
 */
@Composable
fun ManualSortExplanation(settings: AppSettings, onDismiss: () -> Unit) {
    if (settings.sortOrder != SortOrder.MANUAL || settings.manualSortExplained) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.drag_indicator_24px), contentDescription = null) },
        title = { Text(stringResource(R.string.sort_manual)) },
        text = { Text(stringResource(R.string.manual_sort_explanation)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

/** A copy with the element at [from] moved to [to]. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }

/**
 * The order while a document is dragged, until the new one is saved.
 * A long press selects the document, which is undone when it's dragged.
 */
@Stable
class DocumentDrag {
    var order by mutableStateOf<List<Document>?>(null)
        private set
    private var longPress: Pair<String, Boolean>? = null

    fun onLongPress(id: String, wasSelected: Boolean) {
        longPress = id to wasSelected
    }

    /** Returns whether a document moved. */
    fun move(visible: List<Document>, fromId: Any, toId: Any): Boolean {
        val current = order ?: visible
        val from = current.indexOfFirst { it.id == fromId }
        val to = current.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) return false
        order = current.moved(from, to)
        return true
    }

    fun stop(
        visible: List<Document>,
        selectedIds: Set<String>,
        onToggleSelection: (String) -> Unit,
        onReorder: (List<String>) -> Unit,
    ) {
        order?.let { order ->
            longPress?.let { (id, wasSelected) -> if ((id in selectedIds) != wasSelected) onToggleSelection(id) }
            // dragged back to its place, so no new list would replace the order
            if (order == visible) this.order = null else onReorder(order.map { it.id })
        }
        longPress = null
    }

    /** The saved order is shown now. */
    fun onSaved() {
        order = null
    }
}
