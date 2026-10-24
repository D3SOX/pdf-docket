package me.d3sox.pdfwallet.documents

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onTags: () -> Unit,
    onShare: () -> Unit,
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
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(
                        R.string.select_all to onSelectAll,
                        R.string.tags to onTags,
                        R.string.share to onShare,
                        R.string.delete to onDelete,
                    ).forEach { (label, action) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                expanded = false
                                action()
                            },
                        )
                    }
                }
            }
        },
    )
}
