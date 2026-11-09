package me.d3sox.pdfwallet.ui

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import me.d3sox.pdfwallet.MainEvent
import me.d3sox.pdfwallet.MainUiState
import me.d3sox.pdfwallet.MainViewModel
import me.d3sox.pdfwallet.PasswordPrompt
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
        backStack.add(ArchiveRoute)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.onFilesSelected(uris)
    }
    val passwordPrompt by vm.passwordPrompt.collectAsStateWithLifecycle()
    val isShowingDocument = backStack.lastOrNull() is DetailsRoute
    LaunchedEffect(isShowingDocument) {
        vm.lockTimer.onDocumentShown(isShowingDocument)
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MainEvent.OpenDocument -> {
                    backStack.retainAll { it == DocumentsRoute }
                    if (event.archived) backStack.add(ArchiveRoute)
                    backStack.add(DetailsRoute(event.id))
                }
                is MainEvent.DocumentsImported -> {
                    backStack.retainAll { it == DocumentsRoute }
                    snackbarHostState.showSnackbar(event.message(resources))
                }
                else -> snackbarHostState.showSnackbar(event.message(resources))
            }
        }
    }

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
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                remember { roundedWhileLeaving() },
            ),
            transitionSpec = { forwardTransition() },
            popTransitionSpec = { backTransition() },
            predictivePopTransitionSpec = { predictiveBackTransition(it) },
            entryProvider = entryProvider {
                entry<DocumentsRoute> {
                    DocumentsScreen(
                        isArchive = false,
                        settings = state.settings,
                        onAddDocument = { documentPicker.launch(arrayOf(PDF_MIME_TYPE)) },
                        onOpenDocument = { backStack.add(DetailsRoute(it)) },
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        onOpenAbout = { backStack.add(AboutRoute) },
                        onOpenArchive = openArchive,
                        onBack = goBack,
                    )
                }
                entry<ArchiveRoute> {
                    DocumentsScreen(
                        isArchive = true,
                        settings = state.settings,
                        onAddDocument = { documentPicker.launch(arrayOf(PDF_MIME_TYPE)) },
                        onOpenDocument = { backStack.add(DetailsRoute(it)) },
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        onOpenAbout = { backStack.add(AboutRoute) },
                        onOpenArchive = openArchive,
                        onBack = goBack,
                    )
                }
                entry<DetailsRoute> { route ->
                    DocumentDetailsScreen(
                        vm = hiltViewModel<DocumentDetailsViewModel, DocumentDetailsViewModel.Factory>(
                            key = route.documentId,
                            creationCallback = { it.create(route.documentId) },
                        ),
                        settings = state.settings,
                        isDarkTheme = isDarkTheme,
                        onBack = goBack,
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

    if (passwordPrompt != PasswordPrompt.NONE && !state.isLocked) {
        TextInputDialog(
            title = stringResource(R.string.dialog_password_protection_title),
            label = stringResource(R.string.dialog_password_protection_hint),
            isPassword = true,
            // closes once the import succeeds, stays open on a wrong password
            dismissOnConfirm = false,
            errorText = if (passwordPrompt == PasswordPrompt.WRONG) stringResource(R.string.wrong_password) else null,
            onConfirm = vm::onPasswordEntered,
            onDismiss = vm::onPasswordDialogDismissed,
        )
    }
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
