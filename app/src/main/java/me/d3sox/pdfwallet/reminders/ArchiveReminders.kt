package me.d3sox.pdfwallet.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.coroutines.dispatcher.di.IoDispatcher
import me.d3sox.pdfwallet.documents.Document
import me.d3sox.pdfwallet.documents.DocumentRepository
import me.d3sox.pdfwallet.documents.openDocumentIntent
import me.d3sox.pdfwallet.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "archive_reminders"
private const val REMINDER_HOUR = 9
private const val REMINDED_UNTIL = "remindedUntil"

/**
 * Reminds of each document in the morning before the day it gets archived automatically, its last day,
 * with a notification that opens it.
 */
@Singleton
class ArchiveReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val documentRepository: DocumentRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = NotificationManagerCompat.from(context)
    private val mutex = Mutex()

    // reminders up to this time were shown or skipped
    private val preferences by lazy { context.getSharedPreferences("archive_reminders", Context.MODE_PRIVATE) }

    private val alarm: PendingIntent
        get() = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ArchiveReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Schedules the next reminder whenever an archive date changes, until cancelled.
     * Reminders that were due before a change are skipped, as the app is open then.
     * When the app starts, it may have been stopped before a reminder, which is shown then.
     */
    suspend fun scheduleOnChanges() {
        var started = false
        documentRepository.documents
            .map { documents -> documents.associate { it.id to if (it.archived) null else it.archiveAt } }
            .distinctUntilChanged()
            .collect {
                mutex.withLock {
                    if (started) setRemindedUntil(System.currentTimeMillis())
                    started = true
                    schedule(documentRepository.documents.first())
                }
            }
    }

    /** Shows the reminders that are due, and schedules the next one. */
    suspend fun remindDue() = mutex.withLock {
        val now = System.currentTimeMillis()
        val documents = documentRepository.documents.first()
        val due = dueReminders(documents, remindedUntil(), now, ZoneId.systemDefault())
        if (due.isNotEmpty() && canNotify()) {
            val hideNames = settingsRepository.settings.first().requireAuthentication
            notificationManager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.archive_reminder_channel))
                    .build()
            )
            due.forEach { notify(it, hideNames) }
        }
        setRemindedUntil(now)
        schedule(documents)
    }

    private suspend fun schedule(documents: List<Document>) {
        val next = nextReminder(documents, remindedUntil(), ZoneId.systemDefault())
        if (next != null) {
            // not exact, Android may postpone it a bit to save battery
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm)
        } else {
            alarmManager.cancel(alarm)
        }
    }

    private fun canNotify() = notificationManager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)

    // canNotify checks the permission
    @SuppressLint("MissingPermission")
    private fun notify(document: Document, hideName: Boolean) {
        val open = PendingIntent.getActivity(
            context,
            document.id.hashCode(),
            context.openDocumentIntent(document.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.event_upcoming_24px)
            // the lock screen shows notifications without unlocking the app
            .setContentTitle(if (hideName) context.getString(R.string.archive_reminder_hidden) else document.name)
            .setContentText(if (hideName) null else context.getString(R.string.archive_reminder_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(document.id, 0, notification)
    }

    /** The first time, there are no reminders before now. */
    private suspend fun remindedUntil(): Long = withContext(dispatcher) {
        if (!preferences.contains(REMINDED_UNTIL)) {
            preferences.edit(commit = true) { putLong(REMINDED_UNTIL, System.currentTimeMillis()) }
        }
        preferences.getLong(REMINDED_UNTIL, 0)
    }

    private suspend fun setRemindedUntil(time: Long) = withContext(dispatcher) {
        // written right away, the process may stop once the reminders are shown
        preferences.edit(commit = true) { putLong(REMINDED_UNTIL, time) }
    }
}

/** In the morning of the day before [archiveAt], the last day of the document. */
internal fun reminderAt(archiveAt: Long, zone: ZoneId): Long = Instant.ofEpochMilli(archiveAt).atZone(zone)
    .toLocalDate()
    .minusDays(1)
    .atTime(REMINDER_HOUR, 0)
    .atZone(zone)
    .toInstant()
    .toEpochMilli()

/** The documents to remind of, whose reminder is after [remindedUntil] and not after [now], before they're archived */
internal fun dueReminders(documents: List<Document>, remindedUntil: Long, now: Long, zone: ZoneId): List<Document> =
    documents.filter { document ->
        val archiveAt = document.archiveAt ?: return@filter false
        !document.archived && archiveAt > now && reminderAt(archiveAt, zone) in (remindedUntil + 1)..now
    }

/** When the first reminder after [remindedUntil] is due, or null if there is none. It may be due already. */
internal fun nextReminder(documents: List<Document>, remindedUntil: Long, zone: ZoneId): Long? = documents
    .filterNot { it.archived }
    .mapNotNull { document -> document.archiveAt?.let { reminderAt(it, zone) } }
    .filter { it > remindedUntil }
    .minOrNull()
