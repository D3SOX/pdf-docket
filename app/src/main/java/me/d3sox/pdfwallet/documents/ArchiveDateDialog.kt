package me.d3sox.pdfwallet.documents

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.d3sox.pdfwallet.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Picks the day a document gets archived automatically, at its start in the current time zone.
 * [onConfirm] gets null if the date is removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveDateDialog(archiveAt: Long?, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = archiveAt?.let {
            localDate(it, ZoneId.systemDefault()).startMillis(ZoneOffset.UTC)
        },
        selectableDates = FutureDates,
    )
    val selected = state.selectedDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    selected?.let { onConfirm(localDate(it, ZoneOffset.UTC).startMillis(ZoneId.systemDefault())) }
                    onDismiss()
                },
                enabled = selected != null,
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row {
                if (archiveAt != null) {
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
                    stringResource(R.string.dialog_archive_date_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

/** Archiving today is the same as archiving right away. */
@OptIn(ExperimentalMaterial3Api::class)
private object FutureDates : SelectableDates {
    // the date picker passes the start of the day in UTC
    override fun isSelectableDate(utcTimeMillis: Long) = localDate(utcTimeMillis, ZoneOffset.UTC) > LocalDate.now()

    override fun isSelectableYear(year: Int) = year >= LocalDate.now().year
}

private fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

private fun LocalDate.startMillis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()
