package me.d3sox.pdfwallet.reminders

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.d3sox.pdfwallet.documents.Document
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

private val ZONE = ZoneId.of("Europe/Berlin")

private fun at(text: String) = LocalDateTime.parse(text).atZone(ZONE).toInstant().toEpochMilli()

private fun document(id: String, archiveAt: String?, archived: Boolean = false) = Document(
    id = id,
    name = id,
    importedAt = 0,
    position = 0,
    sizeBytes = 0,
    tags = emptyList(),
    archived = archived,
    archiveAt = archiveAt?.let(::at),
)

class ArchiveRemindersTest {

    @Test
    fun reminderAt_isTheMorningBeforeTheArchiveDate() {
        reminderAt(at("2026-11-21T00:00"), ZONE) shouldBe at("2026-11-20T09:00")
        // across the switch to winter time
        reminderAt(at("2026-10-26T00:00"), ZONE) shouldBe at("2026-10-25T09:00")
    }

    @Test
    fun dueReminders_areTheOnesSinceTheLastCheck() {
        val documents = listOf(
            document("reminded", "2026-11-20T00:00"),
            document("due", "2026-11-21T00:00"),
            document("later", "2026-11-22T00:00"),
            document("archived", "2026-11-21T00:00", archived = true),
            document("kept", archiveAt = null),
        )

        dueReminders(documents, remindedUntil = at("2026-11-19T12:00"), now = at("2026-11-20T09:05"), ZONE)
            .map { it.id } shouldContainExactly listOf("due")
    }

    @Test
    fun dueReminders_skipDocumentsThatAreDueToBeArchived() {
        // e.g. the device was off since the day before
        val documents = listOf(document("passed", "2026-11-21T00:00"))

        dueReminders(documents, remindedUntil = at("2026-11-19T12:00"), now = at("2026-11-21T08:00"), ZONE)
            .shouldContainExactly(emptyList())
    }

    @Test
    fun nextReminder_isTheEarliestOneAfterTheLastCheck() {
        val documents = listOf(
            document("reminded", "2026-11-20T00:00"),
            document("later", "2026-11-23T00:00"),
            document("next", "2026-11-22T00:00"),
            document("archived", "2026-11-21T00:00", archived = true),
        )

        nextReminder(documents, remindedUntil = at("2026-11-19T12:00"), ZONE) shouldBe at("2026-11-21T09:00")
        nextReminder(documents, remindedUntil = at("2026-11-22T09:00"), ZONE) shouldBe null
    }
}
