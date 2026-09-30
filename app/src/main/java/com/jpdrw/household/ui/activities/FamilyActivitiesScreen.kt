package com.jpdrw.household.ui.activities

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
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
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
import com.jpdrw.household.data.entity.ActivitySlot
import com.jpdrw.household.data.entity.FamilyActivity
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyActivitiesScreen(repository: Repository) {
    val today = DateUtils.today()
    val activities by repository.observeFamilyActivities().collectAsState(initial = emptyList())
    val logs by repository.observeFamilyActivityLogs(today).collectAsState(initial = emptyList())
    val doneIds = logs.filter { it.done }.map { it.activityId }.toSet()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    val grouped = ActivitySlot.entries.associateWith { slot -> activities.filter { it.slot == slot } }
    val currentSlot = currentTimeSlot()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Family Activities") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "Add activity") }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ActivitySlot.entries.forEach { slot ->
                val slotActivities = grouped[slot].orEmpty()
                if (slotActivities.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(slot.label(), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            if (slot == currentSlot) {
                                SuggestionChip(onClick = {}, label = { Text("Suggested now") })
                            }
                        }
                    }
                    items(slotActivities, key = { it.id }) { activity ->
                        ActivityRow(
                            activity = activity,
                            done = activity.id in doneIds,
                            onToggle = { checked -> scope.launch { repository.setFamilyActivityDone(activity.id, today, checked) } },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddFamilyActivityDialog(onDismiss = { showAddDialog = false }, onConfirm = { title, category, slot ->
            scope.launch { repository.addFamilyActivity(title, category, slot) }
            showAddDialog = false
        })
    }
}

private fun currentTimeSlot(): ActivitySlot {
    val hour = LocalTime.now().hour
    return when {
        hour < 9 -> ActivitySlot.START_UP
        hour < 16 -> ActivitySlot.MID_PLAY
        hour < 19 -> ActivitySlot.WIND_DOWN
        else -> ActivitySlot.BEDTIME
    }
}

private fun ActivitySlot.label(): String = when (this) {
    ActivitySlot.START_UP -> "Start-up"
    ActivitySlot.MID_PLAY -> "Mid-play"
    ActivitySlot.WIND_DOWN -> "Wind-down"
    ActivitySlot.BEDTIME -> "Bedtime"
}

@Composable
private fun ActivityRow(activity: FamilyActivity, done: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = done, onCheckedChange = onToggle)
            Column {
                Text(activity.title)
                Text(
                    if (activity.category == com.jpdrw.household.data.entity.ActivityCategory.INDOOR) "Indoor" else "Outdoor",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFamilyActivityDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, com.jpdrw.household.data.entity.ActivityCategory, ActivitySlot) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(com.jpdrw.household.data.entity.ActivityCategory.INDOOR) }
    var slot by remember { mutableStateOf(ActivitySlot.MID_PLAY) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Activity name") })
                Text("Category")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.jpdrw.household.data.entity.ActivityCategory.entries.forEach {
                        SuggestionChip(onClick = { category = it }, label = { Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }) })
                    }
                }
                Text("Suggest during")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivitySlot.entries.forEach {
                        SuggestionChip(onClick = { slot = it }, label = { Text(it.label()) })
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(enabled = title.isNotBlank(), onClick = { onConfirm(title.trim(), category, slot) }) { Text("Add") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
