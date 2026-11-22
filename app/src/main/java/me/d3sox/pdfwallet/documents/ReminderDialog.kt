package me.d3sox.pdfwallet.documents

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Picks the day of a reminder, and optionally its time, which can't have passed.
 * [onConfirm] gets null if the reminder is removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(reminder: Reminder?, onConfirm: (Reminder?) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = reminder?.date?.startMillis(ZoneOffset.UTC),
        selectableDates = TodayAndLater,
    )
    // seconds of the day, LocalTime can't be saved
    var timeSeconds by rememberSaveable { mutableStateOf(reminder?.time?.toSecondOfDay()) }
    val time = timeSeconds?.let { LocalTime.ofSecondOfDay(it.toLong()) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val picked = state.selectedDateMillis?.let { Reminder(localDate(it, ZoneOffset.UTC), time) }
    // e.g. today in the evening without a time
    val passed = picked != null && picked.at(ZoneId.systemDefault()) <= System.currentTimeMillis()
    // without the year, which the calendar shows, so the time fits next to the date
    val dateFormatter = remember { DatePickerDefaults.dateFormatter(selectedDateSkeleton = "MMMd") }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(picked)
                    onDismiss()
                },
                enabled = picked != null && !passed,
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row {
                if (reminder != null) {
                    TextButton(onClick = {
                        onConfirm(null)
                        onDismiss()
                    }) { Text(stringResource(R.string.remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                // the padding of the default title
                Text(
                    stringResource(if (passed) R.string.reminder_passed else R.string.dialog_reminder_title),
                    color = if (passed) MaterialTheme.colorScheme.error else Color.Unspecified,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            headline = {
                // the padding of the default headline, the time is next to the date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    DatePickerDefaults.DatePickerHeadline(
                        selectedDateMillis = state.selectedDateMillis,
                        displayMode = state.displayMode,
                        dateFormatter = dateFormatter,
                        modifier = Modifier.weight(1f),
                    )
                    TimeChip(time, onClick = { pickTime = true })
                }
            },
        )
    }
    if (pickTime) {
        ReminderTimeDialog(
            time = time,
            onConfirm = { timeSeconds = it?.toSecondOfDay() },
            onDismiss = { pickTime = false },
        )
    }
}

@Composable
private fun TimeChip(time: LocalTime?, onClick: () -> Unit) {
    val context = LocalContext.current
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                time?.let {
                    DateUtils.formatDateTime(
                        context,
                        LocalDate.now().atTime(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        DateUtils.FORMAT_SHOW_TIME,
                    )
                } ?: stringResource(R.string.add_time)
            )
        },
        leadingIcon = {
            Icon(
                painterResource(R.drawable.schedule_24px),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

/** [onConfirm] gets null if the time is removed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(time: LocalTime?, onConfirm: (LocalTime?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val initial = time ?: MORNING
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_reminder_time_title)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row {
                if (time != null) {
                    TextButton(onClick = {
                        onConfirm(null)
                        onDismiss()
                    }) { Text(stringResource(R.string.remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    ) {
        TimePicker(state = state)
    }
}

/** Reminders can be today, if their time hasn't passed. */
@OptIn(ExperimentalMaterial3Api::class)
private object TodayAndLater : SelectableDates {
    // the date picker passes the start of the day in UTC
    override fun isSelectableDate(utcTimeMillis: Long) = localDate(utcTimeMillis, ZoneOffset.UTC) >= LocalDate.now()

    override fun isSelectableYear(year: Int) = year >= LocalDate.now().year
}
