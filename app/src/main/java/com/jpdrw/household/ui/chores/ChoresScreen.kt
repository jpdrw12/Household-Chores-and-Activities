package com.jpdrw.household.ui.chores

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checklist
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
import com.jpdrw.household.data.SubtaskWithChecks
import com.jpdrw.household.data.entity.Assignee
import com.jpdrw.household.data.entity.Chore
import com.jpdrw.household.data.entity.ChorePhoto
import com.jpdrw.household.data.entity.Frequency
import com.jpdrw.household.data.entity.Priority
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChoresScreen(repository: Repository) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val dateIso = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val chores by repository.observeChoresForDate(dateIso).collectAsState(initial = emptyList())
    val completedChores by repository.observeCompletedChoresForDate(dateIso).collectAsState(initial = emptyList())
    val assignees by repository.observeAssignees().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingChore by remember { mutableStateOf<Chore?>(null) }
    var deletingChore by remember { mutableStateOf<Chore?>(null) }
    var completedExpanded by remember { mutableStateOf(false) }
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
            if (chores.isEmpty() && completedChores.isEmpty()) {
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
                            assignees = assignees,
                            onToggle = { checked, completedBy ->
                                scope.launch { repository.setChoreCompleted(item.chore.id, item.effectiveDueDate, checked, null, completedBy) }
                            },
                            onEdit = { editingChore = item.chore },
                            onDelete = { deletingChore = item.chore },
                        )
                    }
                    if (completedChores.isNotEmpty()) {
                        item {
                            TextButton(onClick = { completedExpanded = !completedExpanded }) {
                                Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" Completed (${completedChores.size})")
                                Icon(if (completedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                            }
                        }
                        if (completedExpanded) {
                            items(completedChores, key = { "completed_${it.chore.id}" }) { item ->
                                ChoreCard(
                                    item = item,
                                    repository = repository,
                                    assignees = assignees,
                                    onToggle = { checked, completedBy ->
                                        scope.launch { repository.setChoreCompleted(item.chore.id, item.effectiveDueDate, checked, null, completedBy) }
                                    },
                                    onEdit = { editingChore = item.chore },
                                    onDelete = { deletingChore = item.chore },
                                )
                            }
                        }
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
            onConfirm = { chTitle, frequency, interval, assigneeId, priority, startTime, endTime, notes, dueDay, dueDay2 ->
                scope.launch { repository.addChore(chTitle, frequency, interval, assigneeId, priority, startTime, endTime, notes, dueDay, dueDay2) }
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
            onConfirm = { chTitle, frequency, interval, assigneeId, priority, startTime, endTime, notes, dueDay, dueDay2 ->
                scope.launch { repository.updateChore(chore.id, chTitle, frequency, interval, assigneeId, priority, startTime, endTime, notes, dueDay, dueDay2) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoreCard(
    item: ChoreWithOccurrence,
    repository: Repository,
    assignees: List<Assignee>,
    onToggle: (Boolean, String?) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var photoExpanded by remember { mutableStateOf(false) }
    var subtasksExpanded by remember { mutableStateOf(false) }
    var fullScreenPhoto by remember { mutableStateOf<ChorePhoto?>(null) }
    var showWhoDialog by remember { mutableStateOf(false) }
    val completed = item.occurrence?.completed == true
    val photos by repository.observeChorePhotos(item.chore.id).collectAsState(initial = emptyList())
    val subtasks by repository.observeSubtasks(item.chore.id, item.effectiveDueDate).collectAsState(initial = emptyList())
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

    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch { repository.addChorePhoto(item.chore.id, uri.toString()) }
        }
        pendingCameraUri = null
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createChorePhotoUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }
    val launchCamera = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createChorePhotoUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    val borderColor = when {
        item.isOverdue -> MaterialTheme.colorScheme.error
        item.chore.priority == Priority.CRITICAL -> MaterialTheme.colorScheme.error
        item.chore.priority == Priority.HIGH -> MaterialTheme.colorScheme.tertiary
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(1.5.dp, it) },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = completed,
                    onCheckedChange = { checked ->
                        if (checked) showWhoDialog = true else onToggle(false, null)
                    },
                )
                Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
                    Text(
                        item.chore.title,
                        textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                append(item.chore.frequencyLabel())
                                append(" · ")
                                append(item.assigneeName)
                                if (item.chore.startTime != null || item.chore.estimatedEndTime != null) {
                                    append(" · ")
                                    append(item.chore.startTime ?: "?")
                                    append("–")
                                    append(item.chore.estimatedEndTime ?: "?")
                                }
                                if (completed && item.completedByName != null) {
                                    append(" · Done by ")
                                    append(item.completedByName)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (item.isOverdue) {
                            Text("OVERDUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (item.chore.priority != Priority.NORMAL) {
                            Text(
                                item.chore.priority.label(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.chore.priority == Priority.LOW) MaterialTheme.colorScheme.onSurfaceVariant else borderColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit chore") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete chore") }
            }
            com.jpdrw.household.ui.common.NotesField(
                notes = item.chore.notes,
                onSave = { newNotes -> scope.launch { repository.updateChoreNotes(item.chore, newNotes) } },
                modifier = Modifier.padding(start = 40.dp, bottom = 4.dp),
            )
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
                                PhotoThumbnail(
                                    photo = photo,
                                    onClick = { fullScreenPhoto = photo },
                                    onDelete = { scope.launch { repository.deleteChorePhoto(photo.id) } },
                                )
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = { launchCamera() }) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Take photo")
                        }
                        TextButton(onClick = { photoPicker.launch("image/*") }) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Choose photo")
                        }
                    }
                }
            }

            TextButton(onClick = { subtasksExpanded = !subtasksExpanded }) {
                Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Subtasks (${subtasks.count { it.checkedByAssigneeIds.isNotEmpty() }}/${subtasks.size})")
                Icon(if (subtasksExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(visible = subtasksExpanded) {
                Column {
                    subtasks.forEach { swc ->
                        SubtaskRow(
                            subtask = swc,
                            assignees = assignees,
                            onToggleAssignee = { assigneeId, checked ->
                                scope.launch { repository.setSubtaskChecked(swc.subtask.id, assigneeId, item.effectiveDueDate, checked) }
                            },
                            onDelete = { scope.launch { repository.deleteSubtask(swc.subtask.id) } },
                        )
                    }
                    AddSubtaskRow(onAdd = { title -> scope.launch { repository.addSubtask(item.chore.id, title) } })
                }
            }
        }
    }

    if (showWhoDialog) {
        AlertDialog(
            onDismissRequest = { showWhoDialog = false },
            title = { Text("Who did this?") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    assignees.forEach { assignee ->
                        AssistChip(
                            onClick = {
                                onToggle(true, assignee.id)
                                showWhoDialog = false
                            },
                            label = { Text(assignee.name) },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showWhoDialog = false }) { Text("Cancel") } },
        )
    }

    fullScreenPhoto?.let { photo ->
        FullScreenPhotoDialog(photo = photo, onDismiss = { fullScreenPhoto = null })
    }
}

@Composable
private fun FullScreenPhotoDialog(photo: ChorePhoto, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = androidx.compose.ui.graphics.Color.Black)
                .clickable(onClick = onDismiss),
        ) {
            AsyncImage(
                model = photo.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

/** Creates a new file under files/task_photos/ (matches file_paths.xml) and returns its FileProvider uri. */
private fun createChorePhotoUri(context: android.content.Context): android.net.Uri {
    val dir = java.io.File(context.filesDir, "task_photos").apply { mkdirs() }
    val file = java.io.File(dir, "chore_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun PhotoThumbnail(photo: ChorePhoto, onClick: () -> Unit, onDelete: () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.size(72.dp)) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtaskRow(
    subtask: SubtaskWithChecks,
    assignees: List<Assignee>,
    onToggleAssignee: (assigneeId: String, checked: Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(subtask.subtask.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Remove subtask", modifier = Modifier.size(16.dp))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            assignees.forEach { assignee ->
                val checked = assignee.id in subtask.checkedByAssigneeIds
                AssistChip(
                    onClick = { onToggleAssignee(assignee.id, !checked) },
                    label = { Text(if (checked) "✓ ${assignee.name}" else assignee.name) },
                    colors = if (checked) {
                        AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        AssistChipDefaults.assistChipColors()
                    },
                )
            }
        }
    }
}

@Composable
private fun AddSubtaskRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("New subtask") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {
            if (text.isNotBlank()) {
                onAdd(text.trim())
                text = ""
            }
        }) { Icon(Icons.Filled.Add, contentDescription = "Add subtask") }
    }
}

private fun dayAbbrev(isoDay: Int): String = when (isoDay) {
    1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"; 5 -> "Fri"; 6 -> "Sat"; else -> "Sun"
}

private fun Chore.frequencyLabel(): String = when (frequency) {
    Frequency.DAILY -> "Daily"
    Frequency.WEEKDAYS -> "Weekdays"
    Frequency.WEEKLY -> "Weekly · ${dayAbbrev(dueDayOfWeek ?: 1)}"
    Frequency.TWICE_WEEKLY -> "2x / week · ${dayAbbrev(dueDayOfWeek ?: 1)}/${dayAbbrev(dueDayOfWeek2 ?: 4)}"
    Frequency.CUSTOM -> "Every ${customIntervalDays ?: 1} day(s)"
}

private fun Priority.label(): String = when (this) {
    Priority.LOW -> "Low"
    Priority.NORMAL -> "Normal"
    Priority.HIGH -> "High"
    Priority.CRITICAL -> "Critical"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChoreDialog(
    title: String,
    assignees: List<Assignee>,
    initial: Chore?,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        frequency: Frequency,
        customIntervalDays: Int?,
        assigneeId: String,
        priority: Priority,
        startTime: String?,
        estimatedEndTime: String?,
        notes: String?,
        dueDayOfWeek: Int?,
        dueDayOfWeek2: Int?,
    ) -> Unit,
) {
    var choreTitle by remember { mutableStateOf(initial?.title ?: "") }
    var frequency by remember { mutableStateOf(initial?.frequency ?: Frequency.WEEKLY) }
    var intervalText by remember { mutableStateOf(initial?.customIntervalDays?.toString() ?: "") }
    var priority by remember { mutableStateOf(initial?.priority ?: Priority.NORMAL) }
    var startTime by remember { mutableStateOf(initial?.startTime) }
    var estimatedEndTime by remember { mutableStateOf(initial?.estimatedEndTime) }
    var assigneeId by remember {
        mutableStateOf(initial?.assigneeId ?: assignees.firstOrNull { it.isDefault }?.id ?: assignees.firstOrNull()?.id ?: "")
    }
    // New chores default the due day(s) to today/Monday+Thursday so a freshly added chore shows
    // up right away instead of waiting for an arbitrary day the user never picked.
    var dueDayOfWeek by remember { mutableStateOf(initial?.dueDayOfWeek ?: LocalDate.now().dayOfWeek.value) }
    var dueDayOfWeek2 by remember { mutableStateOf(initial?.dueDayOfWeek2 ?: java.time.DayOfWeek.THURSDAY.value) }
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

                if (frequency == Frequency.WEEKLY) {
                    Text("Due day", style = MaterialTheme.typography.labelLarge)
                    DayOfWeekPicker(selected = dueDayOfWeek, onSelect = { dueDayOfWeek = it })
                }
                if (frequency == Frequency.TWICE_WEEKLY) {
                    Text("First due day", style = MaterialTheme.typography.labelLarge)
                    DayOfWeekPicker(selected = dueDayOfWeek, onSelect = { dueDayOfWeek = it })
                    Text("Second due day", style = MaterialTheme.typography.labelLarge)
                    DayOfWeekPicker(selected = dueDayOfWeek2, onSelect = { dueDayOfWeek2 = it })
                }

                Text("Priority", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { p ->
                        AssistChip(
                            onClick = { priority = p },
                            label = { Text(p.label()) },
                            colors = if (priority == p) {
                                AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                AssistChipDefaults.assistChipColors()
                            },
                        )
                    }
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

                Text("Time window (optional)", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Past the end time and not done, it's marked overdue the same day.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField(label = "Start", time = startTime, onTimeChange = { startTime = it }, modifier = Modifier.weight(1f))
                    TimeField(label = "Est. end", time = estimatedEndTime, onTimeChange = { estimatedEndTime = it }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            val interval = intervalText.toIntOrNull()
            val valid = choreTitle.isNotBlank() && assigneeId.isNotBlank() && (frequency != Frequency.CUSTOM || (interval != null && interval > 0))
            Button(
                enabled = valid,
                onClick = {
                    onConfirm(
                        choreTitle.trim(),
                        frequency,
                        if (frequency == Frequency.CUSTOM) interval else null,
                        assigneeId,
                        priority,
                        startTime,
                        estimatedEndTime,
                        initial?.notes,
                        if (frequency == Frequency.WEEKLY || frequency == Frequency.TWICE_WEEKLY) dueDayOfWeek else null,
                        if (frequency == Frequency.TWICE_WEEKLY) dueDayOfWeek2 else null,
                    )
                },
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfWeekPicker(selected: Int, onSelect: (Int) -> Unit) {
    val days = listOf(
        1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { (value, label) ->
            AssistChip(
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = if (value == selected) {
                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}

private fun Frequency.label(): String = when (this) {
    Frequency.DAILY -> "Daily"
    Frequency.WEEKDAYS -> "Weekdays (Mon–Fri)"
    Frequency.WEEKLY -> "Weekly"
    Frequency.TWICE_WEEKLY -> "2x / week"
    Frequency.CUSTOM -> "Custom (every N days)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(label: String, time: String?, onTimeChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    val parsed = time?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
    val pickerState = androidx.compose.material3.rememberTimePickerState(
        initialHour = parsed?.hour ?: 8,
        initialMinute = parsed?.minute ?: 0,
        is24Hour = false,
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = time ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                placeholder = { Text("Not set") },
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.matchParentSize().clickable { showPicker = true },
            )
        }
        if (time != null) {
            IconButton(onClick = { onTimeChange(null) }) { Icon(Icons.Filled.Close, contentDescription = "Clear $label") }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            text = { androidx.compose.material3.TimePicker(state = pickerState) },
            confirmButton = {
                Button(onClick = {
                    onTimeChange("%02d:%02d".format(pickerState.hour, pickerState.minute))
                    showPicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        )
    }
}
