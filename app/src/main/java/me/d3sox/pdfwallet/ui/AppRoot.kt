package me.d3sox.pdfwallet.ui

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import me.d3sox.pdfwallet.MainEvent
import me.d3sox.pdfwallet.MainUiState
import me.d3sox.pdfwallet.MainViewModel
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.about.AboutScreen
import me.d3sox.pdfwallet.details.DocumentDetailsScreen
import me.d3sox.pdfwallet.details.DocumentDetailsViewModel
import me.d3sox.pdfwallet.documents.DocumentsScreen
import me.d3sox.pdfwallet.lock.LockScreen
import me.d3sox.pdfwallet.settings.SettingsScreen
import me.d3sox.pdfwallet.ui.components.TextInputDialog

private const val PDF_MIME_TYPE = "application/pdf"

val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppRoot(
    vm: MainViewModel,
    state: MainUiState,
    isDarkTheme: Boolean,
) {
    val backStack = rememberNavBackStack(DocumentsRoute)
    // e.g. a double tap on a back arrow would otherwise also remove the document list, which crashes
    val goBack: () -> Unit = {
        if (backStack.size > 1 && backStack.removeAt(backStack.lastIndex) == ArchiveRoute) {
            vm.documentFilter.show(archive = false)
        }
    }
    val openArchive: () -> Unit = {
        vm.documentFilter.show(archive = true)
        // a document shown next to the list would turn to the archived documents
        backStack.removeAll { it is DetailsRoute }
        backStack.add(ArchiveRoute)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.onFilesSelected(uris)
    }
    val isShowingDocument = backStack.lastOrNull() is DetailsRoute
    // where the details are next to the list, opening another document turns the details to it
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(
        // the document list stays when the last document is closed
        backNavigationBehavior = BackNavigationBehavior.PopLatest,
        directive = directive,
        paneExpansionDragHandle = { PaneDragHandle(it) },
    )
    val listPaneMetadata = remember {
        ListDetailSceneStrategy.listPane(detailPlaceholder = { NoDocumentOpen() })
    }
    // the document the details show, which changes when swiping to another one
    var shownDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    // picked in the list next to the details, until they turned to it
    var requestedDocumentId by remember { mutableStateOf<String?>(null) }
    val openDocumentId = shownDocumentId.takeIf { isShowingDocument && directive.maxHorizontalPartitions > 1 }
    val openDocument: (String) -> Unit = { id ->
        if (isShowingDocument) {
            requestedDocumentId = id
        } else {
            shownDocumentId = id
            // a pick the closed details never got would turn the new ones away from this document
            requestedDocumentId = null
            backStack.add(DetailsRoute(id))
        }
    }
    LaunchedEffect(isShowingDocument) {
        vm.lockTimer.onDocumentShown(isShowingDocument)
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MainEvent.OpenDocument -> {
                    backStack.retainAll { it == DocumentsRoute }
                    if (event.archived) backStack.add(ArchiveRoute)
                    shownDocumentId = event.id
                    // the details stay if they were opened at the same document, but may show another one by now
                    requestedDocumentId = event.id
                    backStack.add(DetailsRoute(event.id))
                }
                is MainEvent.DocumentsImported -> {
                    backStack.retainAll { it == DocumentsRoute }
                    launch { snackbarHostState.showSnackbar(event.message(resources)) }
                }
                // queued by the host state, while later events, e.g. opening a document, are handled right away
                else -> launch { snackbarHostState.showSnackbar(event.message(resources)) }
            }
        }
    }

    // created outside of the locked branch, so the screens keep their state, e.g. the scroll position, while locked
    val entryDecorators: List<NavEntryDecorator<NavKey>> = listOf(
        rememberSaveableStateHolderNavEntryDecorator(rememberSaveableStateHolder()),
        rememberViewModelStoreNavEntryDecorator(),
        remember { roundedWhileLeaving() },
    )

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        if (state.isLocked) {
            LockScreen(
                onAuthenticated = vm::onAuthenticated,
                onAuthenticationDisabled = vm::onAuthenticationDisabled,
            )
            return@CompositionLocalProvider
        }
        NavDisplay(
            backStack = backStack,
            onBack = goBack,
            entryDecorators = entryDecorators,
            sceneStrategies = listOf(listDetailStrategy),
            transitionSpec = { forwardTransition() },
            popTransitionSpec = { backTransition() },
            predictivePopTransitionSpec = { predictiveBackTransition() },
            entryProvider = entryProvider {
                entry<DocumentsRoute>(metadata = listPaneMetadata) {
                    DocumentsScreen(
                        isArchive = false,
                        settings = state.settings,
                        openDocumentId = openDocumentId,
                        onAddDocument = { documentPicker.launch(arrayOf(PDF_MIME_TYPE)) },
                        onOpenDocument = openDocument,
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        onOpenAbout = { backStack.add(AboutRoute) },
                        onOpenArchive = openArchive,
                        onBack = goBack,
                    )
                }
                entry<ArchiveRoute>(metadata = listPaneMetadata) {
                    DocumentsScreen(
                        isArchive = true,
                        settings = state.settings,
                        openDocumentId = openDocumentId,
                        onAddDocument = { documentPicker.launch(arrayOf(PDF_MIME_TYPE)) },
                        onOpenDocument = openDocument,
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        onOpenAbout = { backStack.add(AboutRoute) },
                        onOpenArchive = openArchive,
                        onBack = goBack,
                    )
                }
                entry<DetailsRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                    DocumentDetailsScreen(
                        vm = hiltViewModel<DocumentDetailsViewModel, DocumentDetailsViewModel.Factory>(
                            key = route.documentId,
                            creationCallback = { it.create(route.documentId) },
                        ),
                        settings = state.settings,
                        isDarkTheme = isDarkTheme,
                        requestedDocumentId = requestedDocumentId,
                        onRequestHandled = { requestedDocumentId = null },
                        onShownDocumentChange = { shownDocumentId = it },
                        // e.g. while the details leave next to the archive, whose documents they would see
                        onBack = { if (backStack.lastOrNull() == route) goBack() },
                    )
                }
                entry<SettingsRoute> {
                    SettingsScreen(settings = state.settings, onBack = goBack)
                }
                entry<AboutRoute> {
                    AboutScreen(onBack = goBack)
                }
            },
        )
    }

    if (!state.isLocked) PasswordDialog(vm)
}

/** Asks for the password of a PDF that is being imported. */
@Composable
private fun PasswordDialog(vm: MainViewModel) {
    val prompt = vm.passwordPrompt.collectAsStateWithLifecycle().value ?: return
    TextInputDialog(
        title = stringResource(R.string.dialog_password_protection_title),
        text = stringResource(
            R.string.dialog_password_protection_text,
            prompt.documentName.ifBlank { stringResource(R.string.untitled_document) },
        ),
        label = stringResource(R.string.dialog_password_protection_hint),
        isPassword = true,
        // closes once the import succeeds, stays open on a wrong password
        dismissOnConfirm = false,
        errorText = if (prompt.isWrongPassword) stringResource(R.string.wrong_password) else null,
        onConfirm = vm::onPasswordEntered,
        onDismiss = vm::onPasswordDialogDismissed,
    )
}

/** The message about an import, which is shown in a snackbar. */
private fun MainEvent.message(resources: Resources): String = when (this) {
    is MainEvent.DocumentsImported -> resources.getQuantityString(R.plurals.documents_imported, count, count)
    is MainEvent.DocumentsAlreadyImported ->
        resources.getQuantityString(R.plurals.documents_already_imported, count, count)
    is MainEvent.SourceFilesDeleted -> resources.getQuantityString(R.plurals.source_files_deleted, count, count)
    is MainEvent.SourceFilesNotDeleted -> resources.getQuantityString(R.plurals.source_files_not_deleted, count, count)
    MainEvent.ImportFailed -> resources.getString(R.string.error_reading_pdf)
    is MainEvent.OpenDocument -> error("Opening a document has no message")
}
