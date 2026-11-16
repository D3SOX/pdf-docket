package me.d3sox.pdfwallet.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.documents.Document
import me.d3sox.pdfwallet.documents.DocumentRepository
import me.d3sox.pdfwallet.lock.AppLockedRepo
import me.d3sox.pdfwallet.lock.LockScreen
import me.d3sox.pdfwallet.render.DocumentRenderer
import me.d3sox.pdfwallet.settings.SettingsRepository
import me.d3sox.pdfwallet.ui.theme.PdfWalletTheme
import me.d3sox.pdfwallet.ui.theme.isDark
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Picks the document of a widget when it's added to the home screen, or later to pick another one.
 */
@AndroidEntryPoint
class WidgetConfigActivity : FragmentActivity() {

    @Inject
    lateinit var widgets: DocumentWidgets

    @Inject
    lateinit var documentRepository: DocumentRepository

    @Inject
    lateinit var renderer: DocumentRenderer

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var lockedRepo: AppLockedRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // the launcher doesn't add the widget unless a document is picked
        setResult(RESULT_CANCELED, result)
        // other apps can start this activity
        if (!widgets.isDocumentWidget(widgetId)) {
            finish()
            return
        }

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(null)
            val isAppLocked by lockedRepo.isAppLocked.collectAsStateWithLifecycle(true)
            // unlocks only the picker, the app's lock timer doesn't know about it.
            // Not saved, so a picker restored after its process was stopped asks again
            var unlocked by remember { mutableStateOf(false) }
            // the picker locks again once it's left, its content would be shown without asking otherwise
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { unlocked = false }
            val documents by remember {
                documentRepository.documents.map { documents -> documents.filterNot(Document::archived) }
            }.collectAsStateWithLifecycle(null)
            val appSettings = settings ?: return@setContent
            LaunchedEffect(appSettings.blockScreenshots) {
                if (appSettings.blockScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            PdfWalletTheme(darkTheme = appSettings.themeMode.isDark(), dynamicColor = appSettings.dynamicColor) {
                if (isAppLocked && !unlocked) {
                    LockScreen(
                        onAuthenticated = { unlocked = true },
                        onAuthenticationDisabled = {
                            lifecycleScope.launch {
                                settingsRepository.update { it.copy(requireAuthentication = false) }
                            }
                        },
                    )
                } else {
                    DocumentPicker(
                        documents = documents,
                        loadThumbnail = renderer::thumbnail,
                        onPick = { documentId ->
                            lifecycleScope.launch {
                                widgets.setDocument(widgetId, documentId)
                                setResult(RESULT_OK, result)
                                finish()
                            }
                        },
                        onClose = ::finish,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DocumentPicker(
    /** null while loading */
    documents: List<Document>?,
    loadThumbnail: suspend (String) -> Bitmap?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_document)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            documents == null -> Unit
            documents.isEmpty() -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
            ) {
                Text(
                    stringResource(R.string.empty_state_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.padding(innerPadding),
            ) {
                items(documents, key = { it.id }) { document ->
                    ListItem(
                        onClick = { onPick(document.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        leadingContent = { Thumbnail(document.id, loadThumbnail) },
                    ) {
                        Text(document.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(documentId: String, load: suspend (String) -> Bitmap?) {
    val thumbnail by produceState<Bitmap?>(null, documentId) { value = load(documentId) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(width = 48.dp, height = 64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        thumbnail?.let {
            Image(
                bitmap = remember(it) { it.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
