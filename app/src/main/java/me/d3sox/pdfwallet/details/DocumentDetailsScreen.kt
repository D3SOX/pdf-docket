@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package me.d3sox.pdfwallet.details

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.panpf.zoomimage.ZoomImage
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.documents.Document
import me.d3sox.pdfwallet.documents.DocumentDialog
import me.d3sox.pdfwallet.documents.DocumentDialogHost
import me.d3sox.pdfwallet.documents.DocumentEditor
import me.d3sox.pdfwallet.documents.DocumentMenu
import me.d3sox.pdfwallet.documents.addRecentShortcut
import me.d3sox.pdfwallet.render.BarcodeScan
import me.d3sox.pdfwallet.settings.AppSettings
import me.d3sox.pdfwallet.ui.LocalSnackbarHostState
import me.d3sox.pdfwallet.ui.components.action
import me.d3sox.pdfwallet.ui.components.checkable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("MagicNumber")
private val invertColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailsScreen(
    vm: DocumentDetailsViewModel,
    settings: AppSettings,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
) {
    FullBrightness(settings.fullBrightness)

    val list by vm.documentList.collectAsStateWithLifecycle()
    val documents = list?.visible ?: return
    if (documents.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val initialPage = remember { documents.indexOfFirst { it.id == vm.initialDocumentId }.coerceAtLeast(0) }
    // the pager reads the page count before the page keys are updated, so both read the same list
    val pagerDocuments by rememberUpdatedState(documents)
    val pagerState = rememberPagerState(initialPage = initialPage) { pagerDocuments.size }
    val current = documents[pagerState.currentPage.coerceIn(documents.indices)]
    val shown = documents[pagerState.settledPage.coerceIn(documents.indices)]
    val context = LocalContext.current
    // the app icon's menu shows document names without unlocking the app
    LaunchedEffect(shown.id, settings.requireAuthentication) {
        if (settings.requireAuthentication) return@LaunchedEffect
        // a blocking call to the system
        withContext(Dispatchers.IO) { context.addRecentShortcut(shown.id, shown.name) }
    }
    // only whether barcodes were found, the scans themselves are cached by the renderer
    val hasBarcodes = remember { mutableStateMapOf<String, Boolean>() }
    var showPdf by rememberSaveable { mutableStateOf(false) }
    val viewMode = when {
        settings.splitScreen -> ViewMode.BOTH
        showPdf -> ViewMode.PDF
        else -> ViewMode.BARCODE
    }
    var dialog by remember { mutableStateOf<DocumentDialog?>(null) }
    // documents without barcodes only show the PDF
    val barcodeViewMode = viewMode.takeIf { hasBarcodes[current.id] == true }
    val onViewModeSelect: (ViewMode) -> Unit = { mode ->
        showPdf = mode == ViewMode.PDF
        vm.onSplitScreenChange(mode == ViewMode.BOTH)
    }
    val onRotateScreen = rememberScreenRotation()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (documents.size > 1) {
                            Text(
                                text = stringResource(
                                    R.string.document_position,
                                    pagerState.currentPage + 1,
                                    documents.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // replaces the bar when it's hidden
                    if (!settings.showViewModeBar) {
                        barcodeViewMode?.let { ViewModeMenuButton(selected = it, onSelect = onViewModeSelect) }
                    }
                    DetailsOverflowMenu(
                        document = current,
                        editor = vm.editor,
                        settings = settings,
                        actions = DetailsMenuActions(
                            onToggleHalfSizeBarcodes = vm::onToggleHalfSizeBarcodes,
                            onRotateScreen = onRotateScreen,
                            onDialog = { dialog = it },
                        ),
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(
            Modifier
                // each document reaches below the navigation bar, where its view mode bar ends
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
                .fillMaxSize()
                // e.g. while queuing at a gate
                .keepScreenOn()
        ) {
            // documents are swiped vertically, their pages and barcodes horizontally
            VerticalPager(
                state = pagerState,
                key = { pagerDocuments[it].id },
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val document = pagerDocuments[index]
                DocumentPage(
                    document = document,
                    viewMode = viewMode,
                    settings = settings,
                    invertColors = isDarkTheme && settings.invertPdfColors,
                    vm = vm,
                    onScanned = { hasBarcodes[document.id] = it.barcodes.isNotEmpty() },
                    onViewModeSelect = onViewModeSelect.takeIf { settings.showViewModeBar },
                )
            }
        }
    }

    DocumentDialogHost(
        dialog = dialog,
        documents = list?.all.orEmpty(),
        tags = list?.tags.orEmpty(),
        editor = vm.editor,
        onDismiss = { dialog = null },
    )
}

private class DetailsMenuActions(
    val onToggleHalfSizeBarcodes: () -> Unit,
    val onRotateScreen: () -> Unit,
    val onDialog: (DocumentDialog) -> Unit,
)

@Composable
private fun DetailsOverflowMenu(
    document: Document,
    editor: DocumentEditor,
    settings: AppSettings,
    actions: DetailsMenuActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val onDismiss = { expanded = false }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DocumentMenu(
            document = document,
            editor = editor,
            expanded = expanded,
            onDismiss = onDismiss,
            onDialog = actions.onDialog,
        ) {
            group {
                checkable(
                    text = { stringResource(R.string.preference_half_size_barcodes) },
                    checked = settings.halfSizeBarcodes,
                    onCheckedChange = { actions.onToggleHalfSizeBarcodes() },
                    icon = { painterResource(R.drawable.barcode_reader_24px) },
                )
            }
            group {
                action(R.string.rotate_screen, R.drawable.screen_rotation_24px, onDismiss, actions.onRotateScreen)
                if (activity?.isPinned == true) {
                    action(R.string.unpin_app, R.drawable.keep_off_24px, onDismiss, activity::stopLockTask)
                } else if (activity != null) {
                    // the system asks to confirm and explains how to unpin
                    action(R.string.pin_app, R.drawable.keep_24px, onDismiss, activity::startLockTask)
                }
            }
        }
    }
}

/**
 * A document with its view mode bar, which is part of each document, so swiping between documents with and without
 * barcodes doesn't resize the pager. Without [onViewModeSelect], the bar is hidden.
 */
@Composable
@Suppress("LongParameterList")
private fun DocumentPage(
    document: Document,
    viewMode: ViewMode,
    settings: AppSettings,
    invertColors: Boolean,
    vm: DocumentDetailsViewModel,
    onScanned: (BarcodeScan) -> Unit,
    onViewModeSelect: ((ViewMode) -> Unit)?,
) {
    val scan by produceState<BarcodeScan?>(null, document.id, settings.barcodeSearchMode, settings.generateNewBarcode) {
        vm.scanBarcodes(document.id, settings).collect {
            value = it
            onScanned(it)
        }
    }
    // positions are always saved, so turning the setting on later reopens where documents were actually left
    val start by produceState<StartPosition?>(null, document.id, settings.reopenAtLastPosition) {
        value = vm.position(document.id)
            ?.takeIf { settings.reopenAtLastPosition }
            ?.let { StartPosition(it.barcode, it.page, restored = true) }
            ?: StartPosition(barcode = 0, page = 0, restored = false)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            val result = scan
            val from = start
            // Documents without barcodes only show the PDF once all pages are searched,
            // and reopened documents once the barcode they were left at is found
            if (result == null || from == null || !result.hasBarcodeFor(from)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else {
                DocumentPages(document, { scan ?: result }, from, viewMode, settings, invertColors, vm)
            }
        }
        if (onViewModeSelect != null && scan?.barcodes?.isNotEmpty() == true) {
            ViewModeBar(selected = viewMode, onSelect = onViewModeSelect)
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

private data class StartPosition(val barcode: Int, val page: Int, val restored: Boolean)

private fun BarcodeScan.hasBarcodeFor(start: StartPosition) = complete || barcodes.size > start.barcode

@Composable
@Suppress("LongParameterList")
private fun DocumentPages(
    document: Document,
    scan: () -> BarcodeScan,
    start: StartPosition,
    viewMode: ViewMode,
    settings: AppSettings,
    invertColors: Boolean,
    vm: DocumentDetailsViewModel,
) {
    // Kept here, so both pagers keep their position when switching the view mode
    // documents without barcodes have an empty barcode pager, which still starts at 0
    val barcodePager = rememberPagerState(
        initialPage = start.barcode.coerceIn(0, maxOf(0, scan().barcodes.lastIndex)),
    ) {
        scan().barcodes.size
    }
    val pdfPager = rememberPagerState(initialPage = start.page.coerceIn(0, maxOf(0, scan().pageCount - 1))) {
        scan().pageCount
    }
    if (settings.syncPages) {
        SyncPagers(barcodePager, pdfPager, alignAtStart = !start.restored) { scan().barcodes.map { it.pageIndex } }
    }
    LaunchedEffect(barcodePager, pdfPager) {
        snapshotFlow { barcodePager.settledPage to pdfPager.settledPage }
            // a restored position is already saved, otherwise the start is the latest position
            .drop(if (start.restored) 1 else 0)
            .collect { (barcode, page) -> vm.savePosition(document.id, barcode, page) }
    }

    val result = scan()
    val indicator = when {
        settings.showPageNumbers -> PageIndicatorStyle.NUMBERED_DOTS
        else -> PageIndicatorStyle.DOTS
    }
    // the indices of the barcodes that are turned by 90°
    val rotated = remember { mutableStateMapOf<Int, Boolean>() }
    val barcodes = @Composable {
        BarcodesPager(result, barcodePager, indicator, settings.halfSizeBarcodes, rotated)
    }
    val pdf = @Composable { PdfPager(document.id, pdfPager, indicator, invertColors, settings.fillPdfWidth, vm) }
    val indicatorHeight = if (result.barcodes.size > 1) pageIndicatorHeight() else 0.dp
    val halfSize = settings.halfSizeBarcodes
    val aspectRatios = {
        scan().barcodes.mapIndexed { index, barcode -> barcode.aspectRatio(rotated[index] == true) }
    }
    when {
        // the PDF couldn't be opened at all
        result.pageCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RenderError(R.string.error_showing_document)
        }
        result.barcodes.isEmpty() -> pdf()
        viewMode == ViewMode.BOTH -> SplitLayout(
            fraction = settings.splitFraction,
            fitHeight = { width ->
                barcodePaneLength(aspectRatios(), width, sideBySide = false, halfSize, indicatorHeight)
            },
            minLength = { crossLength, sideBySide ->
                barcodePaneMinLength(aspectRatios(), crossLength, sideBySide, halfSize, indicatorHeight)
            },
            onFractionChange = vm::onSplitFractionChange,
            first = barcodes,
            second = pdf,
        )
        viewMode == ViewMode.PDF -> pdf()
        else -> barcodes()
    }
}

@Composable
private fun BarcodesPager(
    scan: BarcodeScan,
    state: PagerState,
    indicator: PageIndicatorStyle,
    halfSize: Boolean,
    rotated: MutableMap<Int, Boolean>,
) {
    PagesPager(
        state = state,
        indicator = indicator,
        pageLabel = {
            if (scan.hasSeveralBarcodesPerPage) {
                stringResource(R.string.barcode_position, it + 1, scan.barcodes.size)
            } else {
                stringResource(R.string.page_position, scan.barcodes[it].pageIndex + 1, scan.pageCount)
            }
        },
    ) {
        BarcodeView(scan.barcodes[it], halfSize, rotated = rotated[it] == true) {
            rotated[it] = rotated[it] != true
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun PdfPager(
    documentId: String,
    state: PagerState,
    indicator: PageIndicatorStyle,
    invertColors: Boolean,
    fillWidth: Boolean,
    vm: DocumentDetailsViewModel,
) {
    PagesPager(
        state = state,
        indicator = indicator,
        pageLabel = { stringResource(R.string.page_position, it + 1, state.pageCount) },
    ) { pageIndex ->
        PdfPageView(
            load = { vm.page(documentId, pageIndex) },
            key = "$documentId-$pageIndex",
            invertColors = invertColors,
            fillWidth = fillWidth,
        )
    }
}

@Composable
private fun PagesPager(
    state: PagerState,
    indicator: PageIndicatorStyle,
    pageLabel: @Composable (Int) -> String,
    page: @Composable (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // the indicator sits below the pages, so it never covers a part of them
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(state = state, modifier = Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                page(it)
            }
        }
        if (state.pageCount > 1) {
            PageIndicator(
                current = state.currentPage,
                count = state.pageCount,
                label = pageLabel(state.currentPage),
                numbered = indicator == PageIndicatorStyle.NUMBERED_DOTS,
                onSelect = { scope.launch { state.animateScrollToPage(it) } },
            )
        }
    }
}

@Composable
private fun PdfPageView(
    load: suspend () -> android.graphics.Bitmap?,
    key: String,
    invertColors: Boolean,
    fillWidth: Boolean,
) {
    val page by produceState<PageImage>(PageImage.Loading, key) {
        value = load()?.let { PageImage.Loaded(it.asImageBitmap()) } ?: PageImage.Failed
    }
    when (val image = page) {
        PageImage.Loading -> LoadingIndicator()
        PageImage.Failed -> RenderError(R.string.error_showing_page)
        is PageImage.Loaded -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val bitmap = image.bitmap
            // a page that is taller than its view starts at its top, the rest is scrolled to
            val overflows = fillWidth && maxWidth * bitmap.height / bitmap.width > maxHeight
            ZoomImage(
                painter = remember(image) { BitmapPainter(bitmap) },
                contentDescription = null,
                colorFilter = if (invertColors) invertColorFilter else null,
                contentScale = if (fillWidth) ContentScale.FillWidth else ContentScale.Fit,
                alignment = if (overflows) Alignment.TopCenter else Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private sealed interface PageImage {
    data object Loading : PageImage
    data object Failed : PageImage
    data class Loaded(val bitmap: ImageBitmap) : PageImage
}
