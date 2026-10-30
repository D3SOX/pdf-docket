package me.d3sox.pdfwallet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.d3sox.pdfwallet.documents.ACTION_OPEN_DOCUMENT
import me.d3sox.pdfwallet.documents.EXTRA_DOCUMENT_ID
import me.d3sox.pdfwallet.settings.AppSettings
import me.d3sox.pdfwallet.settings.ThemeMode
import me.d3sox.pdfwallet.ui.AppRoot
import me.d3sox.pdfwallet.ui.theme.PdfWalletTheme
import dagger.hilt.android.AndroidEntryPoint

// same as the androidx.activity defaults
private val LIGHT_NAVIGATION_BAR_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_NAVIGATION_BAR_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val vm by viewModels<MainViewModel>()
    private var hasAppliedWindowSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { vm.uiState.value == null }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }

        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            val state by vm.uiState.collectAsStateWithLifecycle()
            val uiState = state ?: return@setContent
            val darkTheme = when (uiState.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = android.graphics.Color.TRANSPARENT,
                        darkScrim = android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    // the scrim keeps the navigation bar icons readable before Android 8,
                    // which can't draw dark navigation bar icons
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = LIGHT_NAVIGATION_BAR_SCRIM,
                        darkScrim = DARK_NAVIGATION_BAR_SCRIM,
                    ) { darkTheme },
                )
                onDispose {}
            }
            LaunchedEffect(uiState.settings) {
                applyWindowSettings(uiState.settings)
            }

            PdfWalletTheme(darkTheme = darkTheme, dynamicColor = uiState.settings.dynamicColor) {
                AppRoot(
                    vm = vm,
                    state = uiState,
                    isDarkTheme = darkTheme,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        vm.onUserInteraction()
    }

    private fun applyWindowSettings(settings: AppSettings) {
        val isSecure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (settings.blockScreenshots != isSecure) {
            if (settings.blockScreenshots) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            // an existing surface doesn't reliably pick up the changed flag, a new window does
            if (hasAppliedWindowSettings) recreate()
        }
        hasAppliedWindowSettings = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(settings.showOnLockScreen)
        }
    }

    private fun handleIntent(intent: Intent) {
        // reopening the app from recents after leaving it with back delivers the intent it was started with again
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val documentId = intent.getStringExtra(EXTRA_DOCUMENT_ID)
        if (intent.action == ACTION_OPEN_DOCUMENT && documentId != null) {
            vm.onShortcutOpened(documentId)
        } else {
            intent.pdfUris().takeIf { it.isNotEmpty() }?.let(vm::onFilesSelected)
        }
    }

    private fun Intent.pdfUris(): List<Uri> = when (action) {
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        Intent.ACTION_SEND ->
            listOfNotNull(data ?: IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java))
        else -> listOfNotNull(data)
    }
}
