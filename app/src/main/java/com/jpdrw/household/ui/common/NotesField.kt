package com.jpdrw.household.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp

/**
 * Inline notes, editable directly on the card instead of behind the edit dialog: tap the pencil
 * (or "Add note") to reveal a text field, which saves on blur/done and collapses back to display.
 */
@Composable
fun NotesField(notes: String?, onSave: (String?) -> Unit, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(editing) { mutableStateOf(notes ?: "") }

    if (editing) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Notes") },
            minLines = 2,
            modifier = modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = {
                    onSave(text.trim().ifBlank { null })
                    editing = false
                }) { Text("Save") }
            },
        )
    } else if (!notes.isNullOrBlank()) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(notes, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f))
            IconButton(onClick = { editing = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.EditNote, contentDescription = "Edit note", modifier = Modifier.size(18.dp))
            }
        }
    } else {
        TextButton(onClick = { editing = true }, modifier = modifier) {
            Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" Add note")
        }
    }
}
