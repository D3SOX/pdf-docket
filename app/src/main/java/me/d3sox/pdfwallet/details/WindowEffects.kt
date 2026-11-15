package me.d3sox.pdfwallet.details

import android.app.Activity
import android.app.ActivityManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
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

// Android 16 ignores the orientation that apps targeting it request on these screens, e.g. tablets
private const val MIN_LARGE_SCREEN_WIDTH_DP = 600

/**
 * Turns the screen to the other orientation, regardless of the auto-rotate setting, until the details are left.
 * Returns null where the system doesn't allow it.
 */
@Composable
internal fun rememberScreenRotation(): (() -> Unit)? {
    val activity = LocalActivity.current
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
        configuration.smallestScreenWidthDp >= MIN_LARGE_SCREEN_WIDTH_DP
    ) {
        return null
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
