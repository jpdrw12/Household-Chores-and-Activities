package com.jpdrw.household.ui.chores

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.jpdrw.household.data.ChoreWithOccurrence
import com.jpdrw.household.data.Repository
import com.jpdrw.household.data.entity.Assignee
import com.jpdrw.household.data.entity.Frequency
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChoresScreen(repository: Repository) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val dateIso = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val chores by repository.observeChoresForDate(dateIso).collectAsState(initial = emptyList())
    val assignees by repository.observeAssignees().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chores") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add chore")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DateSelector(selectedDate = selectedDate, onDateChange = { selectedDate = it })
            if (chores.isEmpty()) {
                Text(
                    "No chores due on this date.",
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(chores, key = { it.chore.id }) { item ->
                        ChoreCard(
                            item = item,
                            onToggle = { checked ->
                                scope.launch { repository.setChoreCompleted(item.chore.id, dateIso, checked, null) }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddChoreDialog(
            assignees = assignees,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, frequency, assigneeId ->
                scope.launch { repository.addChore(title, frequency, assigneeId, null) }
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun DateSelector(selectedDate: LocalDate, onDateChange: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = { onDateChange(selectedDate.minusDays(1)) }) { Text("< Prev") }
        Text(
            if (selectedDate == LocalDate.now()) "Today · ${selectedDate}" else selectedDate.toString(),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = { onDateChange(selectedDate.plusDays(1)) }) { Text("Next >") }
    }
}

@Composable
private fun ChoreCard(item: ChoreWithOccurrence, onToggle: (Boolean) -> Unit) {
    var photoExpanded by remember { mutableStateOf(false) }
    val completed = item.occurrence?.completed == true

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = completed, onCheckedChange = onToggle)
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        item.chore.title,
                        textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "${item.chore.frequency.label()} · ${item.assigneeName}",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TextButton(onClick = { photoExpanded = !photoExpanded }) {
                Icon(Icons.Filled.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Reference photos")
                Icon(if (photoExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(visible = photoExpanded) {
                Text(
                    "No reference photos yet. Tap to add one showing what \"done\" looks like for this chore.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
            }
        }
    }
}

private fun Frequency.label(): String = when (this) {
    Frequency.DAILY -> "Daily"
    Frequency.WEEKLY -> "Weekly"
    Frequency.TWICE_WEEKLY -> "2x / week"
    Frequency.CUSTOM -> "Custom"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddChoreDialog(
    assignees: List<Assignee>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, frequency: Frequency, assigneeId: Long) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(Frequency.WEEKLY) }
    var assigneeId by remember { mutableStateOf(assignees.firstOrNull { it.isDefault }?.id ?: assignees.firstOrNull()?.id ?: 0L) }
    var frequencyMenuExpanded by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New chore") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Chore name") })

                ExposedDropdownMenuBox(expanded = frequencyMenuExpanded, onExpandedChange = { frequencyMenuExpanded = it }) {
                    OutlinedTextField(
                        value = frequency.label(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(expanded = frequencyMenuExpanded, onDismissRequest = { frequencyMenuExpanded = false }) {
                        Frequency.entries.forEach { freq ->
                            DropdownMenuItem(text = { Text(freq.label()) }, onClick = { frequency = freq; frequencyMenuExpanded = false })
                        }
                    }
                }

                Text("Assignee", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    assignees.forEach { assignee ->
                        AssistChip(
                            onClick = { assigneeId = assignee.id },
                            label = { Text(assignee.name) },
                            colors = if (assignee.id == assigneeId) {
                                androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                )
                            } else {
                                androidx.compose.material3.AssistChipDefaults.assistChipColors()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && assigneeId != 0L,
                onClick = { onConfirm(title.trim(), frequency, assigneeId) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
