package me.d3sox.pdfwallet.documents

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.ui.components.ExpressiveMenu
import me.d3sox.pdfwallet.ui.components.action

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun SelectionTopBar(
    count: Int,
    isArchive: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onTags: () -> Unit,
    onShare: () -> Unit,
    /** or unarchive, in the archive */
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.documents_selected, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_selection))
            }
        },
        actions = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                val dismiss = { expanded = false }
                ExpressiveMenu(expanded = expanded, onDismissRequest = dismiss) {
                    group {
                        action(R.string.select_all, R.drawable.select_all_24, dismiss, onSelectAll)
                        action(R.string.tags, R.drawable.tag_24, dismiss, onTags)
                        action(R.string.share, R.drawable.share_24px, dismiss, onShare)
                    }
                    group {
                        if (isArchive) {
                            action(R.string.unarchive, R.drawable.unarchive_24px, dismiss, onArchive)
                        } else {
                            action(R.string.archive, R.drawable.archive_24px, dismiss, onArchive)
                        }
                        action(R.string.delete, R.drawable.delete_24, dismiss, onDelete)
                    }
                }
            }
        },
    )
}
