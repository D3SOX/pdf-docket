package com.michaeltroger.gruenerpass.documents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.michaeltroger.gruenerpass.R
import com.michaeltroger.gruenerpass.documents.tags.ManageTagsDialog
import com.michaeltroger.gruenerpass.settings.AppSettings
import com.michaeltroger.gruenerpass.settings.SortOrder
import com.michaeltroger.gruenerpass.ui.LocalSnackbarHostState
import com.michaeltroger.gruenerpass.ui.components.ConfirmDialog

private enum class ListDialog { MANAGE_TAGS, DELETE_VISIBLE, LOCK_SCREEN_WARNING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    settings: AppSettings,
    onAddDocument: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: DocumentsViewModel = hiltViewModel(),
) {
    val documentList by vm.documentList.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var isSearching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    var documentDialog by remember { mutableStateOf<DocumentDialog?>(null) }
    var listDialog by rememberSaveable { mutableStateOf<ListDialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val list = documentList
    val context = LocalContext.current

    val closeSearch = {
        isSearching = false
        vm.onQueryChange("")
    }
    BackHandler(enabled = isSearching, onBack = closeSearch)

    Scaffold(
        topBar = {
            DocumentsTopBar(
                isSearching = isSearching,
                query = query,
                hasDocuments = !list?.all.isNullOrEmpty(),
                settings = settings,
                isFiltered = list?.isFiltered == true,
                onQueryChange = vm::onQueryChange,
                onOpenSearch = { isSearching = true },
                onCloseSearch = closeSearch,
                onSortOrderSelected = vm::onSortOrderSelected,
                onWarning = { listDialog = ListDialog.LOCK_SCREEN_WARNING },
                onManageTags = { listDialog = ListDialog.MANAGE_TAGS },
                onExport = { list?.visible?.takeIf { it.isNotEmpty() }?.let(context::sharePdfs) },
                onDelete = { listDialog = ListDialog.DELETE_VISIBLE },
                onLock = vm::onLock,
                onOpenSettings = onOpenSettings,
                onOpenAbout = onOpenAbout,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDocument) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        when {
            list == null -> Unit
            list.all.isEmpty() -> EmptyState(onAddDocument, Modifier.padding(innerPadding))
            else -> DocumentList(
                list = list,
                contentPadding = innerPadding,
                loadThumbnail = vm::thumbnail,
                onToggleTag = vm::onToggleTag,
                onClearFilters = {
                    vm.onClearFilters()
                    isSearching = false
                },
                onOpenDocument = onOpenDocument,
                onDialog = { documentDialog = it },
            )
        }
    }

    DocumentDialogHost(
        dialog = documentDialog,
        documents = list?.all.orEmpty(),
        tags = list?.tags.orEmpty(),
        editor = vm.editor,
        onDismiss = { documentDialog = null },
    )
    ListDialogHost(
        dialog = listDialog,
        list = list,
        vm = vm,
        onDismiss = { listDialog = null },
    )
}

@Composable
private fun ListDialogHost(
    dialog: ListDialog?,
    list: DocumentList?,
    vm: DocumentsViewModel,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> Unit
        ListDialog.MANAGE_TAGS -> ManageTagsDialog(
            tags = list?.tags.orEmpty(),
            onCreateTag = { vm.editor.createTag(it) },
            onRenameTag = vm.editor::renameTag,
            onDeleteTag = vm.editor::deleteTag,
            onDismiss = onDismiss,
        )
        ListDialog.DELETE_VISIBLE -> {
            val documents = list?.visible.orEmpty()
            ConfirmDialog(
                text = if (list?.isFiltered == true) {
                    pluralStringResource(
                        R.plurals.dialog_delete_filtered_confirmation_text,
                        documents.size,
                        documents.size,
                    )
                } else {
                    stringResource(R.string.dialog_delete_all_confirmation_message)
                },
                confirmLabel = stringResource(R.string.delete),
                onConfirm = { vm.editor.delete(documents.map { it.id }) },
                onDismiss = onDismiss,
            )
        }
        ListDialog.LOCK_SCREEN_WARNING -> ConfirmDialog(
            title = stringResource(R.string.dialog_warning_title),
            text = stringResource(R.string.dialog_warning_description),
            confirmLabel = stringResource(R.string.ok),
            onConfirm = {},
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun DocumentsTopBar(
    isSearching: Boolean,
    query: String,
    hasDocuments: Boolean,
    settings: AppSettings,
    isFiltered: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSortOrderSelected: (SortOrder) -> Unit,
    onWarning: () -> Unit,
    onManageTags: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            if (isSearching) {
                SearchField(query, onQueryChange)
            } else {
                Text(stringResource(R.string.app_name))
            }
        },
        navigationIcon = {
            if (isSearching) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_search))
                }
            }
        },
        actions = {
            if (settings.showOnLockScreen) {
                IconButton(onClick = onWarning) {
                    Icon(
                        painterResource(R.drawable.warning_24px),
                        contentDescription = stringResource(R.string.warning),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (hasDocuments && !isSearching) {
                IconButton(onClick = onOpenSearch) {
                    Icon(painterResource(R.drawable.search_24), contentDescription = stringResource(R.string.search))
                }
            }
            if (hasDocuments) {
                SortMenu(settings.sortOrder, onSortOrderSelected)
            }
            OverflowMenu(
                hasDocuments = hasDocuments,
                isFiltered = isFiltered,
                showLock = settings.requireAuthentication,
                onManageTags = onManageTags,
                onExport = onExport,
                onDelete = onDelete,
                onLock = onLock,
                onOpenSettings = onOpenSettings,
                onOpenAbout = onOpenAbout,
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_query_hint)) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
    LaunchedEffect(Unit) {
        if (query.isEmpty()) focusRequester.requestFocus()
    }
}

private val SortOrder.label: Int
    get() = when (this) {
        SortOrder.IMPORT_DATE_DESC -> R.string.sort_import_date_desc
        SortOrder.IMPORT_DATE_ASC -> R.string.sort_import_date_asc
        SortOrder.NAME_ASC -> R.string.sort_name_asc
        SortOrder.NAME_DESC -> R.string.sort_name_desc
        SortOrder.SIZE_DESC -> R.string.sort_size_desc
        SortOrder.SIZE_ASC -> R.string.sort_size_asc
    }

@Composable
private fun SortMenu(selected: SortOrder, onSelect: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.sort_24px), contentDescription = stringResource(R.string.sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.label)) },
                    leadingIcon = if (order == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(order)
                    },
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun OverflowMenu(
    hasDocuments: Boolean,
    isFiltered: Boolean,
    showLock: Boolean,
    onManageTags: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    @Composable
    fun Item(label: Int, onClick: () -> Unit) = DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            expanded = false
            onClick()
        },
    )
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Item(R.string.menu_item_manage_tags, onManageTags)
            if (hasDocuments) {
                Item(if (isFiltered) R.string.export_filtered else R.string.export_all, onExport)
                Item(if (isFiltered) R.string.delete_filtered else R.string.delete_all, onDelete)
            }
            if (showLock) {
                Item(R.string.lock, onLock)
            }
            HorizontalDivider()
            Item(R.string.settings, onOpenSettings)
            Item(R.string.about, onOpenAbout)
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun DocumentList(
    list: DocumentList,
    contentPadding: PaddingValues,
    loadThumbnail: suspend (String) -> android.graphics.Bitmap?,
    onToggleTag: (Long) -> Unit,
    onClearFilters: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onDialog: (DocumentDialog) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            // keep the last card clear of the floating action button
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (list.tags.isNotEmpty()) {
            item(key = "tags", contentType = "tags") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list.tags, key = { it.id }) { tag ->
                        val isSelected = tag.id in list.selectedTagIds
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleTag(tag.id) },
                            label = { Text(tag.name) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
        if (list.isFiltered) {
            item(key = "count", contentType = "count") {
                Text(
                    text = stringResource(R.string.showing_count, list.visible.size, list.all.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (list.visible.isEmpty()) {
            item(key = "no_results", contentType = "no_results") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                ) {
                    Text(
                        stringResource(R.string.empty_filter_result),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearFilters) {
                        Text(stringResource(R.string.search_results_reset_filters_cta))
                    }
                }
            }
        }
        items(list.visible, key = { it.id }, contentType = { "document" }) { document ->
            DocumentCard(
                document = document,
                loadThumbnail = loadThumbnail,
                onClick = { onOpenDocument(document.id) },
                onDialog = onDialog,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .animateItem(),
            )
        }
    }
}

@Composable
private fun EmptyState(onAddDocument: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Icon(
            painterResource(R.drawable.picture_as_pdf_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.empty_state_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_state_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddDocument) {
            Text(stringResource(R.string.import_pdf))
        }
    }
}
