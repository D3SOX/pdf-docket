package me.d3sox.pdfwallet.documents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.documents.tags.BulkTagsDialog
import me.d3sox.pdfwallet.documents.tags.ManageTagsDialog
import me.d3sox.pdfwallet.documents.tags.iconDrawable
import me.d3sox.pdfwallet.settings.AppSettings
import me.d3sox.pdfwallet.settings.DocumentLayout
import me.d3sox.pdfwallet.settings.SortOrder
import me.d3sox.pdfwallet.ui.LocalSnackbarHostState
import me.d3sox.pdfwallet.ui.components.ConfirmDialog
import me.d3sox.pdfwallet.ui.components.ExpressiveMenu
import me.d3sox.pdfwallet.ui.components.action
import me.d3sox.pdfwallet.ui.components.checkable
import me.d3sox.pdfwallet.ui.components.selectable

private enum class ListDialog { MANAGE_TAGS, DELETE_VISIBLE, LOCK_SCREEN_WARNING }
private enum class SelectionDialog { TAGS, DELETE }

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
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectionDialog by rememberSaveable { mutableStateOf<SelectionDialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val list = documentList
    val context = LocalContext.current
    val selectedDocuments = list?.visible.orEmpty().filter { it.id in selectedIds }
    val isSelecting = selectedDocuments.isNotEmpty()
    val clearSelection = {
        selectedIds = emptyList()
        selectionDialog = null
    }
    val selectAll = { selectedIds = list?.visible.orEmpty().map { it.id } }
    LaunchedEffect(list?.visible) {
        list?.let { documents ->
            val visibleIds = documents.visible.map { it.id }.toSet()
            selectedIds = selectedIds.filter { it in visibleIds }
            if (selectedIds.isEmpty()) selectionDialog = null
        }
    }

    val closeSearch = {
        isSearching = false
        vm.onQueryChange("")
    }
    BackHandler(enabled = isSearching, onBack = closeSearch)
    BackHandler(enabled = isSelecting, onBack = clearSelection)

    Scaffold(
        topBar = {
            if (isSelecting) {
                SelectionTopBar(
                    count = selectedDocuments.size,
                    onClose = clearSelection,
                    onSelectAll = selectAll,
                    onTags = { selectionDialog = SelectionDialog.TAGS },
                    onShare = { context.sharePdfs(selectedDocuments) },
                    onDelete = { selectionDialog = SelectionDialog.DELETE },
                )
            } else DocumentsTopBar(
                isSearching = isSearching,
                query = query,
                hasDocuments = !list?.all.isNullOrEmpty(),
                settings = settings,
                isFiltered = list?.isFiltered == true,
                tags = list?.tags.orEmpty(),
                selectedTagIds = list?.selectedTagIds.orEmpty(),
                onToggleTag = vm::onToggleTag,
                onQueryChange = vm::onQueryChange,
                onOpenSearch = { isSearching = true },
                onCloseSearch = closeSearch,
                onSortOrderSelected = vm::onSortOrderSelected,
                onToggleLayout = vm::onToggleLayout,
                onWarning = { listDialog = ListDialog.LOCK_SCREEN_WARNING },
                onManageTags = { listDialog = ListDialog.MANAGE_TAGS },
                onExport = { list?.visible?.takeIf { it.isNotEmpty() }?.let(context::sharePdfs) },
                onDelete = { listDialog = ListDialog.DELETE_VISIBLE },
                onSelectAll = selectAll,
                onLock = vm::onLock,
                onOpenSettings = onOpenSettings,
                onOpenAbout = onOpenAbout,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!isSelecting) {
                val label = stringResource(R.string.import_pdf)
                ExtendedFloatingActionButton(
                    onClick = onAddDocument,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(label) },
                    // the label text isn't exposed to accessibility services on its own
                    modifier = Modifier.semantics { contentDescription = label },
                )
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
                onClearFilters = {
                    vm.onClearFilters()
                    isSearching = false
                },
                asList = settings.documentLayout == DocumentLayout.LIST,
                onOpenDocument = onOpenDocument,
                onDialog = { documentDialog = it },
                selectedIds = selectedIds.toSet(),
                onToggleSelection = { id ->
                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                },
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
    if (isSelecting) {
        when (selectionDialog) {
            null -> Unit
            SelectionDialog.TAGS -> BulkTagsDialog(
                tags = list?.tags.orEmpty(),
                documents = selectedDocuments,
                onConfirm = { added, removed ->
                    vm.editor.updateTags(selectedDocuments.map { it.id }, added, removed)
                    clearSelection()
                },
                onCreateTag = vm.editor::createTag,
                onDismiss = { selectionDialog = null },
            )
            SelectionDialog.DELETE -> ConfirmDialog(
                text = pluralStringResource(
                    R.plurals.dialog_delete_filtered_confirmation_text,
                    selectedDocuments.size,
                    selectedDocuments.size,
                ),
                confirmLabel = stringResource(R.string.delete),
                onConfirm = {
                    vm.editor.delete(selectedDocuments.map { it.id })
                    clearSelection()
                },
                onDismiss = { selectionDialog = null },
            )
        }
    }
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
            onCreateTag = vm.editor::createTag,
            onEditTag = vm.editor::updateTag,
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
    tags: List<Tag>,
    selectedTagIds: Set<Long>,
    onToggleTag: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSortOrderSelected: (SortOrder) -> Unit,
    onToggleLayout: () -> Unit,
    onWarning: () -> Unit,
    onManageTags: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painterResource(R.drawable.app_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(32.dp),
                    )
                    Text(stringResource(R.string.app_name))
                }
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
            if (hasDocuments && tags.isNotEmpty()) {
                TagFilterMenu(tags, selectedTagIds, onToggleTag)
            }
            if (hasDocuments) {
                SortMenu(settings.sortOrder, onSortOrderSelected)
            }
            OverflowMenu(
                hasDocuments = hasDocuments,
                layout = settings.documentLayout,
                onToggleLayout = onToggleLayout,
                isFiltered = isFiltered,
                showLock = settings.requireAuthentication,
                onManageTags = onManageTags,
                onExport = onExport,
                onDelete = onDelete,
                onSelectAll = onSelectAll,
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

private val SortOrder.icon: Int
    get() = when (this) {
        SortOrder.IMPORT_DATE_DESC -> R.drawable.mdi_sort_calendar_descending
        SortOrder.IMPORT_DATE_ASC -> R.drawable.mdi_sort_calendar_ascending
        SortOrder.NAME_ASC -> R.drawable.mdi_sort_alphabetical_ascending
        SortOrder.NAME_DESC -> R.drawable.mdi_sort_alphabetical_descending
        SortOrder.SIZE_DESC -> R.drawable.mdi_sort_numeric_descending
        SortOrder.SIZE_ASC -> R.drawable.mdi_sort_numeric_ascending
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
private fun TagFilterMenu(tags: List<Tag>, selectedIds: Set<Long>, onToggle: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            BadgedBox(badge = { if (selectedIds.isNotEmpty()) Badge { Text(selectedIds.size.toString()) } }) {
                Icon(painterResource(R.drawable.tag_24), contentDescription = stringResource(R.string.filter_by_tag))
            }
        }
        ExpressiveMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            group {
                tags.forEach { tag ->
                    checkable(
                        text = { tag.name },
                        checked = tag.id in selectedIds,
                        onCheckedChange = { onToggle(tag.id) },
                        icon = { painterResource(tag.iconDrawable) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(selected: SortOrder, onSelect: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.sort_24px), contentDescription = stringResource(R.string.sort))
        }
        ExpressiveMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            group {
                SortOrder.entries.forEach { order ->
                    selectable(
                        text = { stringResource(order.label) },
                        icon = { Icon(painterResource(order.icon), contentDescription = null) },
                        selected = order == selected,
                        onDismiss = { expanded = false },
                        onClick = { onSelect(order) },
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun OverflowMenu(
    hasDocuments: Boolean,
    layout: DocumentLayout,
    onToggleLayout: () -> Unit,
    isFiltered: Boolean,
    showLock: Boolean,
    onManageTags: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val dismiss = { expanded = false }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        ExpressiveMenu(expanded = expanded, onDismissRequest = dismiss) {
            if (hasDocuments) {
                group {
                    if (layout == DocumentLayout.GRID) {
                        action(R.string.list_view, R.drawable.view_list_24px, dismiss, onToggleLayout)
                    } else {
                        action(R.string.grid_view, R.drawable.grid_view_24px, dismiss, onToggleLayout)
                    }
                }
            }
            group {
                action(R.string.menu_item_manage_tags, R.drawable.tag_24, dismiss, onManageTags)
                if (hasDocuments) {
                    action(R.string.select_all, R.drawable.select_all_24, dismiss, onSelectAll)
                    action(
                        if (isFiltered) R.string.export_filtered else R.string.export_all,
                        R.drawable.share_24px,
                        dismiss,
                        onExport,
                    )
                    action(
                        if (isFiltered) R.string.delete_filtered else R.string.delete_all,
                        R.drawable.delete_24,
                        dismiss,
                        onDelete,
                    )
                }
                if (showLock) {
                    action(
                        { stringResource(R.string.lock) },
                        { rememberVectorPainter(Icons.Outlined.Lock) },
                        dismiss,
                        onLock,
                    )
                }
            }
            group {
                action(
                    { stringResource(R.string.settings) },
                    { rememberVectorPainter(Icons.Outlined.Settings) },
                    dismiss,
                    onOpenSettings,
                )
                action(
                    { stringResource(R.string.about) },
                    { rememberVectorPainter(Icons.Outlined.Info) },
                    dismiss,
                    onOpenAbout,
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun DocumentList(
    list: DocumentList,
    contentPadding: PaddingValues,
    asList: Boolean,
    loadThumbnail: suspend (String) -> android.graphics.Bitmap?,
    onClearFilters: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onDialog: (DocumentDialog) -> Unit,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = if (asList) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            // keep the last tiles clear of the floating action button
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (asList) 8.dp else 12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (list.isFiltered) {
            fullWidthItem(key = "count") {
                Text(
                    text = stringResource(R.string.showing_count, list.visible.size, list.all.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (list.visible.isEmpty()) {
            fullWidthItem(key = "no_results") {
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
            DocumentTile(
                document = document,
                loadThumbnail = loadThumbnail,
                onClick = {
                    if (selectedIds.isEmpty()) onOpenDocument(document.id) else onToggleSelection(document.id)
                },
                onDialog = onDialog,
                selectionMode = selectedIds.isNotEmpty(),
                isSelected = document.id in selectedIds,
                onLongClick = { onToggleSelection(document.id) },
                asListItem = asList,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

private fun LazyGridScope.fullWidthItem(key: String, content: @Composable () -> Unit) =
    item(key = key, contentType = key, span = { GridItemSpan(maxLineSpan) }) { content() }

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
