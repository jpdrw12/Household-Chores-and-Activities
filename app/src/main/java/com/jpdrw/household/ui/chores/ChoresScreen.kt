package com.jpdrw.household.ui.chores

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jpdrw.household.data.ChoreWithOccurrence
import com.jpdrw.household.data.Repository
import com.jpdrw.household.data.entity.Assignee
import com.jpdrw.household.data.entity.Chore
import com.jpdrw.household.data.entity.ChorePhoto
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
    var editingChore by remember { mutableStateOf<Chore?>(null) }
    var deletingChore by remember { mutableStateOf<Chore?>(null) }
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
                            repository = repository,
                            onToggle = { checked ->
                                scope.launch { repository.setChoreCompleted(item.chore.id, dateIso, checked, null) }
                            },
                            onEdit = { editingChore = item.chore },
                            onDelete = { deletingChore = item.chore },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ChoreDialog(
            title = "New chore",
            assignees = assignees,
            initial = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { chTitle, frequency, interval, assigneeId ->
                scope.launch { repository.addChore(chTitle, frequency, interval, assigneeId) }
                showAddDialog = false
            },
        )
    }

    editingChore?.let { chore ->
        ChoreDialog(
            title = "Edit chore",
            assignees = assignees,
            initial = chore,
            onDismiss = { editingChore = null },
            onConfirm = { chTitle, frequency, interval, assigneeId ->
                scope.launch { repository.updateChore(chore.id, chTitle, frequency, interval, assigneeId) }
                editingChore = null
            },
        )
    }

    deletingChore?.let { chore ->
        AlertDialog(
            onDismissRequest = { deletingChore = null },
            title = { Text("Delete \"${chore.title}\"?") },
            text = { Text("This removes the chore and its full history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteChore(chore.id) }
                    deletingChore = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingChore = null }) { Text("Cancel") } },
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
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = { onDateChange(selectedDate.plusDays(1)) }) { Text("Next >") }
    }
}

@Composable
private fun ChoreCard(
    item: ChoreWithOccurrence,
    repository: Repository,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var photoExpanded by remember { mutableStateOf(false) }
    val completed = item.occurrence?.completed == true
    val photos by repository.observeChorePhotos(item.chore.id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch { repository.addChorePhoto(item.chore.id, uri.toString()) }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = completed, onCheckedChange = onToggle)
                Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
                    Text(
                        item.chore.title,
                        textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "${item.chore.frequencyLabel()} · ${item.assigneeName}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit chore") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete chore") }
            }
            TextButton(onClick = { photoExpanded = !photoExpanded }) {
                Icon(Icons.Filled.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Reference photos (${photos.size})")
                Icon(if (photoExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(visible = photoExpanded) {
                Column {
                    if (photos.isEmpty()) {
                        Text(
                            "No reference photos yet. Add one showing what \"done\" looks like for this chore.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            items(photos, key = { it.id }) { photo ->
                                PhotoThumbnail(photo = photo, onDelete = { scope.launch { repository.deleteChorePhoto(photo.id) } })
                            }
                        }
                    }
                    TextButton(onClick = { photoPicker.launch("image/*") }) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Add photo")
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(photo: ChorePhoto, onDelete: () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.size(72.dp)) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun Chore.frequencyLabel(): String = when (frequency) {
    Frequency.DAILY -> "Daily"
    Frequency.WEEKLY -> "Weekly"
    Frequency.TWICE_WEEKLY -> "2x / week"
    Frequency.CUSTOM -> "Every ${customIntervalDays ?: 1} day(s)"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChoreDialog(
    title: String,
    assignees: List<Assignee>,
    initial: Chore?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, frequency: Frequency, customIntervalDays: Int?, assigneeId: Long) -> Unit,
) {
    var choreTitle by remember { mutableStateOf(initial?.title ?: "") }
    var frequency by remember { mutableStateOf(initial?.frequency ?: Frequency.WEEKLY) }
    var intervalText by remember { mutableStateOf(initial?.customIntervalDays?.toString() ?: "") }
    var assigneeId by remember {
        mutableStateOf(initial?.assigneeId ?: assignees.firstOrNull { it.isDefault }?.id ?: assignees.firstOrNull()?.id ?: 0L)
    }
    var frequencyMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = choreTitle, onValueChange = { choreTitle = it }, label = { Text("Chore name") })

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

                if (frequency == Frequency.CUSTOM) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter { c -> c.isDigit() } },
                        label = { Text("Every how many days?") },
                    )
                }

                Text("Assignee", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    assignees.forEach { assignee ->
                        AssistChip(
                            onClick = { assigneeId = assignee.id },
                            label = { Text(assignee.name) },
                            colors = if (assignee.id == assigneeId) {
                                AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                AssistChipDefaults.assistChipColors()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            val interval = intervalText.toIntOrNull()
            val valid = choreTitle.isNotBlank() && assigneeId != 0L && (frequency != Frequency.CUSTOM || (interval != null && interval > 0))
            Button(
                enabled = valid,
                onClick = { onConfirm(choreTitle.trim(), frequency, if (frequency == Frequency.CUSTOM) interval else null, assigneeId) },
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun Frequency.label(): String = when (this) {
    Frequency.DAILY -> "Daily"
    Frequency.WEEKLY -> "Weekly"
    Frequency.TWICE_WEEKLY -> "2x / week"
    Frequency.CUSTOM -> "Custom (every N days)"
}
