package me.d3sox.pdfwallet.details

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.keepScreenOn
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
import me.d3sox.pdfwallet.documents.DocumentMenuItems
import me.d3sox.pdfwallet.render.BarcodeScan
import me.d3sox.pdfwallet.render.PageBarcode
import me.d3sox.pdfwallet.settings.AppSettings
import me.d3sox.pdfwallet.ui.LocalSnackbarHostState
import kotlinx.coroutines.delay

private const val PILL_VISIBILITY_MS = 2_000L
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
    var dialog by remember { mutableStateOf<DocumentDialog?>(null) }
    val currentHasBarcode = scans[current.id]?.barcodes?.isNotEmpty() == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (currentHasBarcode && !settings.splitScreen) {
                        IconButton(onClick = { showPdf = !showPdf }) {
                            if (showPdf) {
                                Icon(painterResource(R.drawable.qr_code_24), stringResource(R.string.show_barcode))
                            } else {
                                Icon(painterResource(R.drawable.picture_as_pdf_24), stringResource(R.string.show_pdf))
                            }
                        }
                    }
                    DetailsOverflowMenu(
                        document = current,
                        settings = settings,
                        onToggleHalfSizeBarcodes = vm::onToggleHalfSizeBarcodes,
                        onToggleSplitScreen = vm::onToggleSplitScreen,
                        onDialog = { dialog = it },
                    )
                },
            )
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
            HorizontalPager(
                state = pagerState,
                key = { documents[it].id },
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val document = documents[index]
                DocumentPage(
                    document = document,
                    isActive = index == pagerState.currentPage,
                    showPdf = showPdf,
                    settings = settings,
                    invertColors = isDarkTheme && settings.invertPdfColors,
                    vm = vm,
                    onScanned = { scans[document.id] = it },
                )
            }
            if (documents.size > 1) {
                PositionPill(
                    text = stringResource(R.string.document_position, pagerState.currentPage + 1, documents.size),
                    key = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
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
    onToggleSplitScreen: () -> Unit,
    onDialog: (DocumentDialog) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DocumentMenuItems(document = document, onDialog = onDialog, onDismissMenu = { expanded = false })
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.preference_half_size_barcodes)) },
                trailingIcon = { Checkbox(checked = settings.halfSizeBarcodes, onCheckedChange = null) },
                onClick = onToggleHalfSizeBarcodes,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.preference_split_screen)) },
                trailingIcon = { Checkbox(checked = settings.splitScreen, onCheckedChange = null) },
                onClick = onToggleSplitScreen,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun DocumentPage(
    document: Document,
    isActive: Boolean,
    showPdf: Boolean,
    settings: AppSettings,
    invertColors: Boolean,
    vm: DocumentDetailsViewModel,
    onScanned: (BarcodeScan) -> Unit,
) {
    val scan by produceState<BarcodeScan?>(null, document.id, settings.barcodeSearchMode, settings.generateNewBarcode) {
        value = vm.scanBarcodes(document.id, settings).also(onScanned)
    }
    val result = scan
    if (result == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val barcodes = @Composable { BarcodesPager(result, isActive, settings.halfSizeBarcodes) }
    val pdf = @Composable { PdfPager(document.id, result.pageCount, isActive, invertColors, vm) }
    when {
        result.barcodes.isEmpty() -> pdf()
        settings.splitScreen -> SplitLayout(first = barcodes, second = pdf)
        showPdf -> pdf()
        else -> barcodes()
    }
}

/**
 * Shows [first] above [second], or next to each other in landscape.
 */
@Composable
private fun SplitLayout(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { first() }
                VerticalDivider()
                Box(Modifier.weight(1f)) { second() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { first() }
                HorizontalDivider()
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
private fun BarcodesPager(scan: BarcodeScan, isActive: Boolean, halfSize: Boolean) {
    PagesPager(
        pageCount = scan.barcodes.size,
        isActive = isActive,
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
    pageCount: Int,
    isActive: Boolean,
    invertColors: Boolean,
    vm: DocumentDetailsViewModel,
) {
    PagesPager(
        pageCount = pageCount,
        isActive = isActive,
        pageLabel = { stringResource(R.string.page_position, it + 1, pageCount) },
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
    pageCount: Int,
    isActive: Boolean,
    pageLabel: @Composable (Int) -> String,
    page: @Composable (Int) -> Unit,
) {
    val pagerState = rememberPagerState { pageCount }
    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                page(it)
            }
        }
        if (isActive) {
            PositionPill(
                text = pageLabel(pagerState.currentPage),
                key = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun BarcodeView(barcode: PageBarcode, halfSize: Boolean) {
    val bitmap = remember(barcode) { barcode.bitmap.asImageBitmap() }
    val aspectRatio = bitmap.width.toFloat() / bitmap.height
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = 24.dp
        val verticalPadding = (maxHeight / BARCODE_PADDING_DIVISOR).coerceAtMost(48.dp)
        // as large as possible while keeping the aspect ratio
        val availableWidth = (maxWidth - horizontalPadding * 2) * (if (halfSize) HALF_SIZE else 1f)
        val availableHeight = maxHeight - verticalPadding * 2
        val width = minOf(availableWidth, availableHeight * aspectRatio)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
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
        CircularProgressIndicator()
    } else {
        ZoomImage(
            painter = remember(image) { BitmapPainter(image) },
            contentDescription = null,
            colorFilter = if (invertColors) invertColorFilter else null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PositionPill(text: String, key: Any, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        visible = true
        delay(PILL_VISIBILITY_MS)
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
