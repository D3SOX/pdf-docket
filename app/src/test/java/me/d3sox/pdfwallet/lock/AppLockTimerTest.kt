package me.d3sox.pdfwallet.lock

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.d3sox.pdfwallet.settings.LockTimeout
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppLockTimerTest {

    private val timeout = MutableStateFlow(LockTimeout.FIVE_MINUTES)
    private var locked = false

    private fun TestScope.timer() = AppLockTimer(backgroundScope, timeout) { locked = true }.also { runCurrent() }

    /** Like the device sleeping, which the coroutine timer doesn't count. */
    private fun sleep(duration: Duration) = ShadowSystemClock.advanceBy(duration.toJavaDuration())

    @Test
    fun locksAfterTheTimeoutWithoutInteraction() = runTest {
        val timer = timer()
        advanceTimeBy(4.minutes)
        timer.onUserInteraction()
        advanceTimeBy(4.minutes)
        locked shouldBe false

        advanceTimeBy(2.minutes)
        locked shouldBe true
    }

    @Test
    fun locksWhenShownAgainAfterTheDeviceSleptLongerThanTheTimeout() = runTest {
        val timer = timer()
        timer.onAppVisibilityChanged(false)
        sleep(6.minutes)

        timer.onAppVisibilityChanged(true)
        runCurrent()

        locked shouldBe true
    }

    @Test
    fun staysUnlockedWhenShownAgainWithinTheTimeout() = runTest {
        val timer = timer()
        timer.onAppVisibilityChanged(false)
        sleep(4.minutes)

        timer.onAppVisibilityChanged(true)
        runCurrent()

        locked shouldBe false
    }

    @Test
    fun shownDocumentStaysUnlocked() = runTest {
        val timer = timer()
        timer.onDocumentShown(true)
        advanceTimeBy(1.hours)
        locked shouldBe false

        // the timeout counts from leaving the document
        timer.onDocumentShown(false)
        advanceTimeBy(5.minutes + 1.seconds)
        locked shouldBe true
    }

    @Test
    fun shownDocumentStaysUnlockedWhenTheScreenRotates() = runTest {
        val timer = timer()
        timer.onDocumentShown(true)
        runCurrent()
        sleep(1.hours)

        // a configuration change only starts the app again
        timer.onAppVisibilityChanged(true)
        runCurrent()

        locked shouldBe false
    }

    @Test
    fun shownDocumentLocksAfterTheTimeoutOnceTheAppWasLeft() = runTest {
        val timer = timer()
        timer.onDocumentShown(true)
        sleep(1.hours)
        timer.onAppVisibilityChanged(false)
        sleep(6.minutes)

        timer.onAppVisibilityChanged(true)
        runCurrent()

        locked shouldBe true
    }

    @Test
    fun immediatelyLocksOnlyWhenTheAppIsLeft() = runTest {
        timeout.value = LockTimeout.IMMEDIATELY
        val timer = timer()
        advanceTimeBy(1.hours)
        locked shouldBe false

        timer.onAppVisibilityChanged(false)
        locked shouldBe true

        // e.g. unlocked by authenticating while the app was left
        locked = false
        timer.onUserInteraction()
        timer.onAppVisibilityChanged(true)
        runCurrent()
        locked shouldBe false
    }

    @Test
    fun shorterTimeoutAppliesRightAway() = runTest {
        timer()
        advanceTimeBy(2.minutes)
        sleep(2.minutes)

        timeout.value = LockTimeout.ONE_MINUTE
        runCurrent()

        locked shouldBe true
    }
}
