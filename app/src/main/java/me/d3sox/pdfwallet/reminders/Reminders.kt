package me.d3sox.pdfwallet.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.coroutines.dispatcher.di.IoDispatcher
import me.d3sox.pdfwallet.documents.Document
import me.d3sox.pdfwallet.documents.DocumentRepository
import me.d3sox.pdfwallet.documents.Reminder
import me.d3sox.pdfwallet.documents.openDocumentIntent
import me.d3sox.pdfwallet.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val ARCHIVE_CHANNEL_ID = "archive_reminders"
private const val CHANNEL_ID = "reminders"
private const val REMINDED_UNTIL = "remindedUntil"

/** A reminder of [document], shown at [at], unless it's [lateAt] by then, both in epoch millis. */
internal data class ScheduledReminder(val document: Document, val kind: Kind, val at: Long, val lateAt: Long) {
    enum class Kind {
        /** in the morning before the day it gets archived automatically, its last day */
        ARCHIVE,

        /** the [Document.reminder] the user picked, e.g. on the day of an event */
        USER,
    }
}

/**
 * Reminds of each document in the morning before the day it gets archived automatically, its last day,
 * and on the day and at the time of its [Document.reminder], with notifications that open it.
 */
@Singleton
class Reminders @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val documentRepository: DocumentRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = NotificationManagerCompat.from(context)
    private val mutex = Mutex()

    // reminders up to this time were shown or skipped, the name is from when there were only archive reminders
    private val preferences by lazy { context.getSharedPreferences("archive_reminders", Context.MODE_PRIVATE) }

    private val alarm: PendingIntent
        get() = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ArchiveReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Shows the due reminders and schedules the next one when the app starts, which may have been stopped
     * before a reminder, and whenever an archive date or a reminder changes, until cancelled.
     * A date that is set after the time of its reminder is too late for it.
     */
    suspend fun scheduleOnChanges() {
        var previous: Map<String, Pair<Long?, Reminder?>>? = null
        documentRepository.documents
            .map { documents ->
                documents.associate { it.id to if (it.archived) null to null else it.archiveAt to it.reminder }
            }
            .distinctUntilChanged()
            .collect { dates ->
                val changed = previous?.let { changedReminders(it, dates) }
                previous = dates
                remindDue(skip = changed.orEmpty())
            }
    }

    /** Removes the reminders, which show document names, once the app lock is turned on, until cancelled. */
    suspend fun hideOnLock() = settingsRepository.settings
        .map { it.requireAuthentication }
        .distinctUntilChanged()
        // only when it's turned on, reminders shown while it was already on don't show names
        .drop(1)
        .filter { it }
        // after reminders that are being shown with the names
        .collect { mutex.withLock { notificationManager.cancelAll() } }

    /**
     * Shows the reminders that are due, except for the [skip] kinds of the documents with those ids,
     * removes the user's reminders that have passed, and schedules the next one.
     */
    internal suspend fun remindDue(skip: Set<Pair<String, ScheduledReminder.Kind>> = emptySet()) = mutex.withLock {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val documents = documentRepository.documents.first()
        val due = dueReminders(documents, remindedUntil(), now, zone).filterNot { it.document.id to it.kind in skip }
        if (due.isNotEmpty() && canNotify()) {
            val hideNames = settingsRepository.settings.first().requireAuthentication
            createChannels()
            due.forEach { notify(it, hideNames) }
        }
        setRemindedUntil(now)
        documents.forEach { document ->
            // a reminder set after the documents were read stays
            document.reminder?.takeIf { it.at(zone) <= now }?.let { documentRepository.removeReminder(document.id, it) }
        }
        schedule(documents)
    }

    private fun createChannels() {
        notificationManager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(ARCHIVE_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.archive_reminder_channel))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.reminder_channel))
                    .build(),
            )
        )
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
    private fun notify(reminder: ScheduledReminder, hideName: Boolean) {
        val document = reminder.document
        val open = PendingIntent.getActivity(
            context,
            document.id.hashCode(),
            context.openDocumentIntent(document.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (channel, hiddenTitle, text) = when (reminder.kind) {
            ScheduledReminder.Kind.ARCHIVE -> Triple(
                ARCHIVE_CHANNEL_ID,
                context.getString(R.string.archive_reminder_hidden),
                context.getString(R.string.archive_reminder_text),
            )
            ScheduledReminder.Kind.USER -> Triple(
                CHANNEL_ID,
                context.getString(R.string.reminder_hidden),
                if (document.reminder?.time != null) {
                    val time = DateUtils.formatDateTime(context, reminder.at, DateUtils.FORMAT_SHOW_TIME)
                    context.getString(R.string.reminder_text_at, time)
                } else {
                    context.getString(R.string.reminder_text)
                },
            )
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(
                when (reminder.kind) {
                    ScheduledReminder.Kind.ARCHIVE -> R.drawable.event_upcoming_24px
                    ScheduledReminder.Kind.USER -> R.drawable.notifications_24px
                }
            )
            // the lock screen shows notifications without unlocking the app
            .setContentTitle(if (hideName) hiddenTitle else document.name)
            .setContentText(if (hideName) null else text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // each document can have one reminder of each kind
        notificationManager.notify(document.id, reminder.kind.ordinal, notification)
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

/**
 * The ids and kinds of the reminders whose dates changed from [old] to [new], which map the document ids
 * to their archive dates and reminders. Only the changed kind, the other reminder of a document may still be due.
 */
internal fun changedReminders(
    old: Map<String, Pair<Long?, Reminder?>>,
    new: Map<String, Pair<Long?, Reminder?>>,
): Set<Pair<String, ScheduledReminder.Kind>> = new.flatMap { (id, dates) ->
    listOfNotNull(
        (id to ScheduledReminder.Kind.ARCHIVE).takeIf { old[id]?.first != dates.first },
        (id to ScheduledReminder.Kind.USER).takeIf { old[id]?.second != dates.second },
    )
}.toSet()

/** In the morning of the day before [archiveAt], the last day of the document. */
internal fun reminderAt(archiveAt: Long, zone: ZoneId): Long = Reminder(
    Instant.ofEpochMilli(archiveAt).atZone(zone).toLocalDate().minusDays(1),
).at(zone)

/**
 * The reminders of the documents that aren't archived. An archive reminder is late once the document is archived,
 * and the user's reminder once its day is over or the document is archived.
 */
internal fun scheduledReminders(documents: List<Document>, zone: ZoneId): List<ScheduledReminder> = documents
    .filterNot { it.archived }
    .flatMap { document ->
        listOfNotNull(
            document.archiveAt?.let {
                ScheduledReminder(document, ScheduledReminder.Kind.ARCHIVE, reminderAt(it, zone), lateAt = it)
            },
            document.reminder?.let {
                val nextDay = it.date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                // the app archives documents only while it's open
                val lateAt = minOf(nextDay, document.archiveAt ?: Long.MAX_VALUE)
                ScheduledReminder(document, ScheduledReminder.Kind.USER, it.at(zone), lateAt)
            },
        )
    }

/** The reminders that are after [remindedUntil] and not after [now], and not late yet */
internal fun dueReminders(documents: List<Document>, remindedUntil: Long, now: Long, zone: ZoneId) =
    scheduledReminders(documents, zone).filter { it.at in (remindedUntil + 1)..now && now < it.lateAt }

/** When the first reminder after [remindedUntil] is due, or null if there is none. It may be due already. */
internal fun nextReminder(documents: List<Document>, remindedUntil: Long, zone: ZoneId): Long? =
    scheduledReminders(documents, zone).map { it.at }.filter { it > remindedUntil }.minOrNull()
