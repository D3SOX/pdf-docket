package me.d3sox.pdfwallet.lock

import android.os.SystemClock
import me.d3sox.pdfwallet.settings.LockTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Locks the app after the chosen time without interaction, or when it's left. Only accessed on the main thread.
 * Lives as long as the process, like the unlocked state, so leaving the app with back doesn't reset the time.
 */
class AppLockTimer(
    private val scope: CoroutineScope,
    private val lockTimeout: Flow<LockTimeout>,
    private val lock: () -> Unit,
) {
    private var job: Job? = null
    private var lastInteraction = SystemClock.elapsedRealtime()
    private var currentTimeout: LockTimeout? = null
    private var isAppVisible = true
    private var isShowingDocument = false

    init {
        schedule()
    }

    fun onUserInteraction() {
        lastInteraction = SystemClock.elapsedRealtime()
        schedule()
    }

    /** A shown document keeps the app unlocked, e.g. while queuing at a gate. */
    fun onDocumentShown(shown: Boolean) {
        isShowingDocument = shown
        onUserInteraction()
    }

    fun onAppVisibilityChanged(visible: Boolean) {
        // e.g. rotating the screen starts the app again without stopping it, a shown document would then lock
        if (visible == isAppVisible) return
        isAppVisible = visible
        if (!visible) {
            // the document was looked at until now
            if (isShowingDocument) lastInteraction = SystemClock.elapsedRealtime()
            if (currentTimeout == LockTimeout.IMMEDIATELY) lock()
        }
        // delay() doesn't count the time the device sleeps, e.g. with the screen off
        schedule()
    }

    private fun schedule() {
        job?.cancel()
        job = scope.launch {
            lockTimeout.distinctUntilChanged().collectLatest { timeout ->
                currentTimeout = timeout
                val remaining = timeout.duration - (SystemClock.elapsedRealtime() - lastInteraction).milliseconds
                when {
                    // locked when the app is left instead
                    timeout == LockTimeout.IMMEDIATELY -> Unit
                    !remaining.isPositive() -> lock()
                    isShowingDocument && isAppVisible -> Unit
                    else -> {
                        delay(remaining)
                        lock()
                    }
                }
            }
        }
    }
}
