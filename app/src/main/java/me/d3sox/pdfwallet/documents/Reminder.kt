package me.d3sox.pdfwallet.documents

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Reminders without a time are shown in the morning. */
internal val MORNING: LocalTime = LocalTime.of(9, 0)

/**
 * A reminder of a document, e.g. on the day of an event, on [date] at [time], or in the morning without one,
 * in the current time zone.
 */
data class Reminder(val date: LocalDate, val time: LocalTime? = null) {
    /** in epoch millis */
    fun at(zone: ZoneId): Long = date.atTime(time ?: MORNING).atZone(zone).toInstant().toEpochMilli()
}
