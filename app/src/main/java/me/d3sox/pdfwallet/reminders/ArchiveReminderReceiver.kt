package me.d3sox.pdfwallet.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.d3sox.pdfwallet.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** the action of the scheduled reminder */
internal const val ACTION_REMIND = "me.d3sox.pdfwallet.action.REMIND"

/**
 * Shows the reminders when they're due, and after the device starts, which clears the scheduled one,
 * or the time zone changes, which moves the next one.
 * It keeps the name from when there were only archive reminders, alarms scheduled before an update target it.
 */
@AndroidEntryPoint
class ArchiveReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminders: Reminders

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, ACTION_REMIND)) return
        val result = goAsync()
        scope.launch {
            try {
                reminders.remindDue()
            } finally {
                result.finish()
            }
        }
    }
}
