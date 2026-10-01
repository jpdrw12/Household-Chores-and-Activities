package com.jpdrw.household.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Compact, card-friendly date field: a button when unset, else the date with a pencil to change it
 * and an X to clear — no full-width text field taking up a row. Matches NotesField's pattern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(date: String?, onDateChange: (String?) -> Unit, modifier: Modifier = Modifier, label: String = "Scheduled") {
    var showPicker by remember { mutableStateOf(false) }
    val parsed = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = (parsed ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )

    if (date != null) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$label: $date", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { showPicker = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Change date", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { onDateChange(null) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Clear date", modifier = Modifier.size(16.dp))
            }
        }
    } else {
        TextButton(onClick = { showPicker = true }, modifier = modifier) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(" Schedule for a date")
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            text = { DatePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateChange(picked.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    }
                    showPicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        )
    }
}
