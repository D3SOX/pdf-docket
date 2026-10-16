package com.michaeltroger.gruenerpass.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.michaeltroger.gruenerpass.MainEvent
import com.michaeltroger.gruenerpass.MainUiState
import com.michaeltroger.gruenerpass.MainViewModel
import com.michaeltroger.gruenerpass.PasswordPrompt
import com.michaeltroger.gruenerpass.R
import com.michaeltroger.gruenerpass.about.AboutScreen
import com.michaeltroger.gruenerpass.details.DocumentDetailsScreen
import com.michaeltroger.gruenerpass.details.DocumentDetailsViewModel
import com.michaeltroger.gruenerpass.documents.DocumentsScreen
import com.michaeltroger.gruenerpass.lock.LockScreen
import com.michaeltroger.gruenerpass.settings.SettingsScreen
import com.michaeltroger.gruenerpass.ui.components.TextInputDialog

private const val PDF_MIME_TYPE = "application/pdf"

val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun AppRoot(
    vm: MainViewModel,
    state: MainUiState,
    isDarkTheme: Boolean,
) {
    val backStack = rememberNavBackStack(DocumentsRoute)
    val snackbarHostState = remember { SnackbarHostState() }
    val importError = stringResource(R.string.error_reading_pdf)
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::onFileSelected)
    }
    val passwordPrompt by vm.passwordPrompt.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MainEvent.OpenDocument -> {
                    backStack.retainAll { it == DocumentsRoute }
                    backStack.add(DetailsRoute(event.id))
                }
                MainEvent.ImportFailed -> snackbarHostState.showSnackbar(importError)
            }
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        if (state.isLocked) {
            LockScreen(onAuthenticated = vm::onAuthenticated)
            return@CompositionLocalProvider
        }
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<DocumentsRoute> {
                    DocumentsScreen(
                        settings = state.settings,
                        onAddDocument = { documentPicker.launch(arrayOf(PDF_MIME_TYPE)) },
                        onOpenDocument = { backStack.add(DetailsRoute(it)) },
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        onOpenAbout = { backStack.add(AboutRoute) },
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
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<SettingsRoute> {
                    SettingsScreen(onBack = { backStack.removeLastOrNull() })
                }
                entry<AboutRoute> {
                    AboutScreen(onBack = { backStack.removeLastOrNull() })
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
