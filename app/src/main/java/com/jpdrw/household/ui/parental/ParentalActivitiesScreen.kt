package com.jpdrw.household.ui.parental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.unit.dp
import com.jpdrw.household.data.AppPrefs
import com.jpdrw.household.data.DateUtils
import com.jpdrw.household.data.Repository
import com.jpdrw.household.data.entity.BudgetTier
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.data.entity.ParentalAudience
import com.jpdrw.household.ui.common.DateField
import com.jpdrw.household.ui.common.NotesField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalActivitiesScreen(repository: Repository, appPrefs: AppPrefs) {
    val today = DateUtils.today()
    val week = DateUtils.isoWeek()
    val activities by repository.observeParentalActivities().collectAsState(initial = emptyList())
    val logs by repository.observeParentalActivityLogs(week).collectAsState(initial = emptyList())
    val doneIds = logs.filter { it.done }.map { it.activityId }.toSet()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<ParentalActivity?>(null) }
    var deletingActivity by remember { mutableStateOf<ParentalActivity?>(null) }
    var budgetFilter by remember { mutableStateOf<BudgetTier?>(null) }
    var mode by remember { mutableStateOf(AudienceMode.PARENTS) }

    val modeAudiences = when (mode) {
        AudienceMode.PARENTS -> setOf(ParentalAudience.PERSONAL, ParentalAudience.TOGETHER, ParentalAudience.ADULT_ONLY)
        AudienceMode.FAMILY -> setOf(ParentalAudience.FAMILY)
        AudienceMode.INTIMATE -> ParentalAudience.entries.toSet()
    }
    val filtered = activities.filter {
        it.audience in modeAudiences &&
            (budgetFilter == null || it.budget == budgetFilter) &&
            (it.isSpicy == (mode == AudienceMode.INTIMATE))
    }
    val grouped = ParentalAudience.entries.associateWith { audience ->
        filtered.filter { it.audience == audience }.sortedWith(
            compareByDescending<ParentalActivity> { scheduleStatus(it, today, it.id in doneIds) != ScheduleStatus.NONE }
                .thenBy { it.title },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("For Us — this week") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "Add activity") }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == AudienceMode.PARENTS, onClick = { mode = AudienceMode.PARENTS }, label = { Text("Parents") })
                FilterChip(selected = mode == AudienceMode.FAMILY, onClick = { mode = AudienceMode.FAMILY }, label = { Text("Family & Kids") })
                FilterChip(selected = mode == AudienceMode.INTIMATE, onClick = { mode = AudienceMode.INTIMATE }, label = { Text("💞 Intimate") })
            }
            Row(modifier = Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipRow(selected = budgetFilter, onSelect = { budgetFilter = it })
            }
            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ParentalAudience.entries.forEach { audience ->
                    val group = grouped[audience].orEmpty()
                    if (group.isNotEmpty()) {
                        item { Text(audience.label(), style = MaterialTheme.typography.titleMedium) }
                        items(group, key = { it.id }) { activity ->
                            ParentalRow(
                                activity = activity,
                                done = activity.id in doneIds,
                                status = scheduleStatus(activity, today, activity.id in doneIds),
                                onToggle = { checked -> scope.launch { repository.setParentalActivityDone(activity.id, week, checked) } },
                                onEdit = { editingActivity = activity },
                                onDelete = { deletingActivity = activity },
                                onSaveNotes = { notes -> scope.launch { repository.updateParentalActivityNotes(activity, notes) } },
                                onSaveSchedule = { date -> scope.launch { repository.updateParentalActivitySchedule(activity, date) } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ParentalActivityDialog(
            title = "New activity",
            initial = null,
            defaultAudience = if (mode == AudienceMode.FAMILY) ParentalAudience.FAMILY else ParentalAudience.TOGETHER,
            defaultIsSpicy = mode == AudienceMode.INTIMATE,
            onDismiss = { showAddDialog = false },
            onConfirm = { actTitle, audience, budget, isSpicy ->
                scope.launch { repository.addParentalActivity(actTitle, audience, budget, isSpicy) }
                showAddDialog = false
            },
        )
    }

    editingActivity?.let { activity ->
        ParentalActivityDialog(
            title = "Edit activity",
            initial = activity,
            onDismiss = { editingActivity = null },
            onConfirm = { actTitle, audience, budget, isSpicy ->
                scope.launch {
                    repository.updateParentalActivity(activity.id, actTitle, audience, budget, isSpicy, activity.notes, activity.scheduledDate)
                }
                editingActivity = null
            },
        )
    }

    deletingActivity?.let { activity ->
        AlertDialog(
            onDismissRequest = { deletingActivity = null },
            title = { Text("Delete \"${activity.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteParentalActivity(activity.id) }
                    deletingActivity = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingActivity = null }) { Text("Cancel") } },
        )
    }
}

private enum class AudienceMode { PARENTS, FAMILY, INTIMATE }

private enum class ScheduleStatus { NONE, TODAY, OVERDUE }

private fun scheduleStatus(activity: ParentalActivity, today: String, done: Boolean): ScheduleStatus {
    val scheduled = activity.scheduledDate ?: return ScheduleStatus.NONE
    if (done) return ScheduleStatus.NONE
    return when {
        scheduled == today -> ScheduleStatus.TODAY
        scheduled < today -> ScheduleStatus.OVERDUE
        else -> ScheduleStatus.NONE
    }
}

@Composable
private fun FilterChipRow(selected: BudgetTier?, onSelect: (BudgetTier?) -> Unit) {
    AssistChip(onClick = { onSelect(null) }, label = { Text(if (selected == null) "✓ All" else "All") })
    BudgetTier.entries.forEach { tier ->
        AssistChip(onClick = { onSelect(tier) }, label = { Text(if (selected == tier) "✓ ${tier.label()}" else tier.label()) })
    }
}

private fun BudgetTier.label(): String = when (this) {
    BudgetTier.LOW -> "$ Low"
    BudgetTier.MEDIUM -> "$$ Medium"
    BudgetTier.HIGH -> "$$$ High"
}

private fun ParentalAudience.label(): String = when (this) {
    ParentalAudience.PERSONAL -> "Personal time"
    ParentalAudience.TOGETHER -> "Together"
    ParentalAudience.ADULT_ONLY -> "Adult only"
    ParentalAudience.FAMILY -> "Family & Kids"
}

@Composable
private fun ParentalRow(
    activity: ParentalActivity,
    done: Boolean,
    status: ScheduleStatus,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSaveNotes: (String?) -> Unit,
    onSaveSchedule: (String?) -> Unit,
) {
    val borderColor = when (status) {
        ScheduleStatus.OVERDUE -> MaterialTheme.colorScheme.error
        ScheduleStatus.TODAY -> MaterialTheme.colorScheme.tertiary
        ScheduleStatus.NONE -> null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(1.5.dp, it) },
    ) {
        Column {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = done, onCheckedChange = onToggle)
                Column(modifier = Modifier.weight(1f)) {
                    Text(activity.title)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (activity.isSpicy) "${activity.budget.label()} · 💞 Intimate" else activity.budget.label(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (status == ScheduleStatus.OVERDUE) {
                            Text("OVERDUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        } else if (status == ScheduleStatus.TODAY) {
                            Text("TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit activity") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete activity") }
            }
            DateField(
                date = activity.scheduledDate,
                onDateChange = onSaveSchedule,
                modifier = Modifier.padding(start = 40.dp, end = 8.dp),
            )
            NotesField(
                notes = activity.notes,
                onSave = onSaveNotes,
                modifier = Modifier.padding(start = 40.dp, end = 8.dp, bottom = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentalActivityDialog(
    title: String,
    initial: ParentalActivity?,
    defaultAudience: ParentalAudience = ParentalAudience.TOGETHER,
    defaultIsSpicy: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, ParentalAudience, BudgetTier, Boolean) -> Unit,
) {
    var actTitle by remember { mutableStateOf(initial?.title ?: "") }
    var audience by remember { mutableStateOf(initial?.audience ?: defaultAudience) }
    var budget by remember { mutableStateOf(initial?.budget ?: BudgetTier.LOW) }
    var isSpicy by remember { mutableStateOf(initial?.isSpicy ?: defaultIsSpicy) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = actTitle, onValueChange = { actTitle = it }, label = { Text("Activity name") })
                Text("Audience")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParentalAudience.entries.forEach {
                        FilterChip(selected = audience == it, onClick = { audience = it }, label = { Text(it.label()) })
                    }
                }
                Text("Budget")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetTier.entries.forEach {
                        FilterChip(selected = budget == it, onClick = { budget = it }, label = { Text(it.label()) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSpicy, onCheckedChange = { isSpicy = it })
                    Text("💞 Intimate (shows under the Intimate tab instead of Parents/Family)")
                }
            }
        },
        confirmButton = {
            Button(enabled = actTitle.isNotBlank(), onClick = { onConfirm(actTitle.trim(), audience, budget, isSpicy) }) {
                Text(if (initial == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

