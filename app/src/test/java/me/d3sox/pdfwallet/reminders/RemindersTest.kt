package me.d3sox.pdfwallet.reminders

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.d3sox.pdfwallet.documents.Document
import me.d3sox.pdfwallet.documents.Reminder
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val ZONE = ZoneId.of("Europe/Berlin")

private fun at(text: String) = LocalDateTime.parse(text).atZone(ZONE).toInstant().toEpochMilli()

private fun document(
    id: String,
    archiveAt: String? = null,
    archived: Boolean = false,
    reminder: Reminder? = null,
) = Document(
    id = id,
    name = id,
    importedAt = 0,
    position = 0,
    sizeBytes = 0,
    tags = emptyList(),
    archived = archived,
    archiveAt = archiveAt?.let(::at),
    reminder = reminder,
)

private fun reminder(date: String, time: String? = null) = Reminder(LocalDate.parse(date), time?.let(LocalTime::parse))

private fun List<ScheduledReminder>.ids() = map { "${it.document.id}/${it.kind}" }

class RemindersTest {

    @Test
    fun reminderAt_isTheMorningBeforeTheArchiveDate() {
        reminderAt(at("2026-11-21T00:00"), ZONE) shouldBe at("2026-11-20T09:00")
        // across the switch to winter time
        reminderAt(at("2026-10-26T00:00"), ZONE) shouldBe at("2026-10-25T09:00")
    }

    @Test
    fun reminder_isAtItsTimeOrInTheMorning() {
        reminder("2026-11-21", "19:30").at(ZONE) shouldBe at("2026-11-21T19:30")
        reminder("2026-11-21").at(ZONE) shouldBe at("2026-11-21T09:00")
    }

    @Test
    fun dueReminders_areTheOnesSinceTheLastCheck() {
        val documents = listOf(
            document("reminded", "2026-11-20T00:00"),
            document("due", "2026-11-21T00:00", reminder = reminder("2026-11-20")),
            document("later", "2026-11-22T00:00", reminder = reminder("2026-11-20", "18:00")),
            document("archived", "2026-11-21T00:00", archived = true, reminder = reminder("2026-11-20")),
            document("kept"),
        )

        dueReminders(documents, remindedUntil = at("2026-11-19T12:00"), now = at("2026-11-20T09:05"), ZONE)
            .ids() shouldContainExactly listOf("due/ARCHIVE", "due/USER")
    }

    @Test
    fun dueReminders_skipDocumentsThatAreDueToBeArchived() {
        // e.g. the device was off since the day before
        val documents = listOf(document("passed", "2026-11-21T00:00"))

        dueReminders(documents, remindedUntil = at("2026-11-19T12:00"), now = at("2026-11-21T08:00"), ZONE)
            .shouldContainExactly(emptyList())
    }

    @Test
    fun dueReminders_showTheUserReminderLaterOnItsDayButNotAfter() {
        // e.g. the device was off at the time
        val documents = listOf(
            document("today", reminder = reminder("2026-11-21", "10:00")),
            document("yesterday", reminder = reminder("2026-11-20", "10:00")),
        )

        dueReminders(documents, remindedUntil = at("2026-11-19T12:00"), now = at("2026-11-21T20:00"), ZONE)
            .ids() shouldContainExactly listOf("today/USER")
    }

    @Test
    fun nextReminder_isTheEarliestOneAfterTheLastCheck() {
        val documents = listOf(
            document("reminded", "2026-11-20T00:00"),
            document("later", "2026-11-23T00:00"),
            document("next", "2026-11-22T00:00"),
            document("archived", "2026-11-21T00:00", archived = true),
            document("event", reminder = reminder("2026-11-22", "19:30")),
        )

        nextReminder(documents, remindedUntil = at("2026-11-19T12:00"), ZONE) shouldBe at("2026-11-21T09:00")
        nextReminder(documents, remindedUntil = at("2026-11-21T09:00"), ZONE) shouldBe at("2026-11-22T09:00")
        nextReminder(documents, remindedUntil = at("2026-11-22T09:00"), ZONE) shouldBe at("2026-11-22T19:30")
        nextReminder(documents, remindedUntil = at("2026-11-22T19:30"), ZONE) shouldBe null
    }
}
