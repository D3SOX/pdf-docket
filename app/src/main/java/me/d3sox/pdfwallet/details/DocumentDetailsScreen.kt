@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package me.d3sox.pdfwallet.details

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
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
import me.d3sox.pdfwallet.documents.DocumentMenu
import me.d3sox.pdfwallet.render.BarcodeScan
import me.d3sox.pdfwallet.render.PageBarcode
import me.d3sox.pdfwallet.settings.AppSettings
import me.d3sox.pdfwallet.ui.LocalSnackbarHostState
import me.d3sox.pdfwallet.ui.components.checkable
import kotlinx.coroutines.flow.drop

private const val HALF_SIZE = 0.5f
private const val BARCODE_PADDING_DIVISOR = 8

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
    val window = LocalActivity.current?.window
    DisposableEffect(window, settings.fullBrightness) {
        if (window == null || !settings.fullBrightness) return@DisposableEffect onDispose {}
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        onDispose {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    val list by vm.documentList.collectAsStateWithLifecycle()
    val documents = list?.visible ?: return
    if (documents.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val initialPage = remember { documents.indexOfFirst { it.id == vm.initialDocumentId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage) { documents.size }
    val current = documents[pagerState.currentPage.coerceIn(documents.indices)]
    val scans = remember { mutableStateMapOf<String, BarcodeScan>() }
    var showPdf by rememberSaveable { mutableStateOf(false) }
    val viewMode = when {
        settings.splitScreen -> ViewMode.BOTH
        showPdf -> ViewMode.PDF
        else -> ViewMode.BARCODE
    }
    var dialog by remember { mutableStateOf<DocumentDialog?>(null) }
    val currentHasBarcode = scans[current.id]?.barcodes?.isNotEmpty() == true

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
                    DetailsOverflowMenu(
                        document = current,
                        settings = settings,
                        onToggleHalfSizeBarcodes = vm::onToggleHalfSizeBarcodes,
                        onDialog = { dialog = it },
                    )
                },
            )
        },
        bottomBar = {
            if (currentHasBarcode) {
                ViewModeBar(selected = viewMode) { mode ->
                    showPdf = mode == ViewMode.PDF
                    vm.onSplitScreenChange(mode == ViewMode.BOTH)
                }
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                // e.g. while queuing at a gate
                .keepScreenOn()
        ) {
            // documents are swiped vertically, their pages and barcodes horizontally
            VerticalPager(
                state = pagerState,
                key = { documents[it].id },
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val document = documents[index]
                DocumentPage(
                    document = document,
                    viewMode = viewMode,
                    settings = settings,
                    invertColors = isDarkTheme && settings.invertPdfColors,
                    vm = vm,
                    onScanned = { scans[document.id] = it },
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

@Composable
private fun DetailsOverflowMenu(
    document: Document,
    settings: AppSettings,
    onToggleHalfSizeBarcodes: () -> Unit,
    onDialog: (DocumentDialog) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DocumentMenu(
            document = document,
            expanded = expanded,
            onDismiss = { expanded = false },
            onDialog = onDialog,
        ) {
            group {
                checkable(
                    text = { stringResource(R.string.preference_half_size_barcodes) },
                    checked = settings.halfSizeBarcodes,
                    onCheckedChange = { onToggleHalfSizeBarcodes() },
                    icon = { painterResource(R.drawable.barcode_reader_24px) },
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun DocumentPage(
    document: Document,
    viewMode: ViewMode,
    settings: AppSettings,
    invertColors: Boolean,
    vm: DocumentDetailsViewModel,
    onScanned: (BarcodeScan) -> Unit,
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

    val result = scan
    val from = start
    // Documents without barcodes only show the PDF once all pages are searched,
    // and reopened documents once the barcode they were left at is found
    if (result == null || from == null || !result.hasBarcodeFor(from)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }
    DocumentPages(document, { scan ?: result }, from, viewMode, settings, invertColors, vm)
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
            .drop(1)
            .collect { (barcode, page) -> vm.savePosition(document.id, barcode, page) }
    }

    val result = scan()
    val indicator = when {
        settings.showPageNumbers -> PageIndicatorStyle.NUMBERED_DOTS
        else -> PageIndicatorStyle.DOTS
    }
    val barcodes = @Composable { BarcodesPager(result, barcodePager, indicator, settings.halfSizeBarcodes) }
    val pdf = @Composable { PdfPager(document.id, pdfPager, indicator, invertColors, vm) }
    when {
        result.barcodes.isEmpty() -> pdf()
        viewMode == ViewMode.BOTH -> SplitLayout(first = barcodes, second = pdf)
        viewMode == ViewMode.PDF -> pdf()
        else -> barcodes()
    }
}

@Composable
private fun BarcodesPager(scan: BarcodeScan, state: PagerState, indicator: PageIndicatorStyle, halfSize: Boolean) {
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
        BarcodeView(scan.barcodes[it], halfSize)
    }
}

@Composable
private fun PdfPager(
    documentId: String,
    state: PagerState,
    indicator: PageIndicatorStyle,
    invertColors: Boolean,
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
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun BarcodeView(barcode: PageBarcode, halfSize: Boolean) {
    val bitmap = remember(barcode) { barcode.bitmap.asImageBitmap() }
    val aspectRatio = bitmap.width.toFloat() / bitmap.height
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        val margin = 24.dp
        // the white quiet zone that scanners need around the code
        val quietZone = (maxHeight / BARCODE_PADDING_DIVISOR).coerceIn(16.dp, 32.dp)
        // as large as possible while keeping the aspect ratio
        val availableWidth = (maxWidth - (margin + quietZone) * 2) * (if (halfSize) HALF_SIZE else 1f)
        val availableHeight = maxHeight - (margin + quietZone) * 2
        val width = minOf(availableWidth, availableHeight * aspectRatio)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(quietZone),
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.show_barcode),
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(width = width, height = width / aspectRatio),
            )
        }
    }
}

@Composable
private fun PdfPageView(
    load: suspend () -> android.graphics.Bitmap?,
    key: String,
    invertColors: Boolean,
) {
    val bitmap by produceState<ImageBitmap?>(null, key) {
        value = load()?.asImageBitmap()
    }
    val image = bitmap
    if (image == null) {
        LoadingIndicator()
    } else {
        ZoomImage(
            painter = remember(image) { BitmapPainter(image) },
            contentDescription = null,
            colorFilter = if (invertColors) invertColorFilter else null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
