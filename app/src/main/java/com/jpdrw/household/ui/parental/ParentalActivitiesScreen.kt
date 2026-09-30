package com.jpdrw.household.ui.parental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.jpdrw.household.data.DateUtils
import com.jpdrw.household.data.Repository
import com.jpdrw.household.data.entity.BudgetTier
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.data.entity.ParentalAudience
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalActivitiesScreen(repository: Repository) {
    val week = DateUtils.isoWeek()
    val activities by repository.observeParentalActivities().collectAsState(initial = emptyList())
    val logs by repository.observeParentalActivityLogs(week).collectAsState(initial = emptyList())
    val doneIds = logs.filter { it.done }.map { it.activityId }.toSet()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<ParentalActivity?>(null) }
    var deletingActivity by remember { mutableStateOf<ParentalActivity?>(null) }
    var budgetFilter by remember { mutableStateOf<BudgetTier?>(null) }

    val filtered = activities.filter { budgetFilter == null || it.budget == budgetFilter }
    val grouped = ParentalAudience.entries.associateWith { audience -> filtered.filter { it.audience == audience } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("For the Parents — this week") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "Add activity") }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
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
                                onToggle = { checked -> scope.launch { repository.setParentalActivityDone(activity.id, week, checked) } },
                                onEdit = { editingActivity = activity },
                                onDelete = { deletingActivity = activity },
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
            onDismiss = { showAddDialog = false },
            onConfirm = { actTitle, audience, budget ->
                scope.launch { repository.addParentalActivity(actTitle, audience, budget) }
                showAddDialog = false
            },
        )
    }

    editingActivity?.let { activity ->
        ParentalActivityDialog(
            title = "Edit activity",
            initial = activity,
            onDismiss = { editingActivity = null },
            onConfirm = { actTitle, audience, budget ->
                scope.launch { repository.updateParentalActivity(activity.id, actTitle, audience, budget) }
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

@Composable
private fun FilterChipRow(selected: BudgetTier?, onSelect: (BudgetTier?) -> Unit) {
    AssistChip(onClick = { onSelect(null) }, label = { Text("All") })
    BudgetTier.entries.forEach { tier ->
        AssistChip(onClick = { onSelect(tier) }, label = { Text(tier.label()) })
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
}

@Composable
private fun ParentalRow(activity: ParentalActivity, done: Boolean, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = done, onCheckedChange = onToggle)
            Column(modifier = Modifier.weight(1f)) {
                Text(activity.title)
                Text(activity.budget.label(), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit activity") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete activity") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentalActivityDialog(
    title: String,
    initial: ParentalActivity?,
    onDismiss: () -> Unit,
    onConfirm: (String, ParentalAudience, BudgetTier) -> Unit,
) {
    var actTitle by remember { mutableStateOf(initial?.title ?: "") }
    var audience by remember { mutableStateOf(initial?.audience ?: ParentalAudience.TOGETHER) }
    var budget by remember { mutableStateOf(initial?.budget ?: BudgetTier.LOW) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = actTitle, onValueChange = { actTitle = it }, label = { Text("Activity name") })
                Text("Audience")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParentalAudience.entries.forEach {
                        SuggestionChip(onClick = { audience = it }, label = { Text(it.label()) })
                    }
                }
                Text("Budget")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetTier.entries.forEach {
                        SuggestionChip(onClick = { budget = it }, label = { Text(it.label()) })
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = actTitle.isNotBlank(), onClick = { onConfirm(actTitle.trim(), audience, budget) }) {
                Text(if (initial == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
