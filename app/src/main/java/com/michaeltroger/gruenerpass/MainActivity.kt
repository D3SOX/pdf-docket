package com.michaeltroger.gruenerpass

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
import com.michaeltroger.gruenerpass.settings.AppSettings
import com.michaeltroger.gruenerpass.settings.ThemeMode
import com.michaeltroger.gruenerpass.ui.AppRoot
import com.michaeltroger.gruenerpass.ui.theme.PdfWalletTheme
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
            intent.pdfUri()?.let(vm::onFileSelected)
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

            PdfWalletTheme(darkTheme = darkTheme) {
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
        intent.pdfUri()?.let(vm::onFileSelected)
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
        window.attributes = window.attributes.apply {
            screenBrightness = if (settings.fullBrightness) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    private fun Intent.pdfUri(): Uri? = data ?: if (action == Intent.ACTION_SEND) {
        IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        null
    }
}
