package me.d3sox.pdfwallet.details

import android.app.Activity
import android.app.ActivityManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration

@Composable
internal fun FullBrightness(enabled: Boolean) {
    val window = LocalActivity.current?.window
    DisposableEffect(window, enabled) {
        if (window == null || !enabled) return@DisposableEffect onDispose {}
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        onDispose {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}

/**
 * Turns the screen to the other orientation, regardless of the auto-rotate setting, until the details are left.
 */
@Composable
internal fun rememberScreenRotation(): () -> Unit {
    val activity = LocalActivity.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // null follows the system
    var orientation by rememberSaveable { mutableStateOf<Int?>(null) }
    val requested = orientation
    DisposableEffect(activity, requested) {
        if (activity == null || requested == null) return@DisposableEffect onDispose {}
        activity.requestedOrientation = requested
        onDispose {
            // the details are shown again in the new orientation
            if (!activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    return {
        orientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
    }
}

/** Whether the app is pinned to the screen, e.g. with [Activity.startLockTask]. */
internal val Activity.isPinned: Boolean
    get() = getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
