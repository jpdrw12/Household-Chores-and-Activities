package com.jpdrw.household.ui.scheduled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.jpdrw.household.data.ScheduledActivity
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.ui.common.DateField
import com.jpdrw.household.ui.common.NotesField
import kotlinx.coroutines.launch

/**
 * Surfaces any For Us / Family activity that has a scheduled date, defaulting to just today's —
 * the "what's actually planned for today" view, separate from the full For Us list. The "Show all
 * scheduled" toggle switches to every upcoming/past scheduled item, soonest first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledActivitiesScreen(repository: Repository, appPrefs: AppPrefs) {
    val today = DateUtils.today()
    val allScheduled by repository.observeScheduledActivities().collectAsState(initial = emptyList())
    var showAll by remember { mutableStateOf(false) }
    var deletingActivity by remember { mutableStateOf<ParentalActivity?>(null) }
    val scope = rememberCoroutineScope()

    val visible = allScheduled
        .filter { showAll || it.activity.scheduledDate == today }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Scheduled") }) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (showAll) "All scheduled" else "Scheduled for today", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show all", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 4.dp))
                    Switch(checked = showAll, onCheckedChange = { showAll = it })
                }
            }
            if (visible.isEmpty()) {
                Text(
                    if (showAll) "Nothing scheduled yet. Set a date on a For Us activity to see it here." else "Nothing scheduled for today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(visible, key = { it.activity.id }) { scheduled ->
                        ScheduledRow(
                            scheduled = scheduled,
                            today = today,
                            onToggle = { checked -> scope.launch { repository.setScheduledActivityDone(scheduled, checked) } },
                            onSaveNotes = { notes -> scope.launch { repository.updateParentalActivityNotes(scheduled.activity, notes) } },
                            onSaveSchedule = { date -> scope.launch { repository.updateParentalActivitySchedule(scheduled.activity, date) } },
                            onDelete = { deletingActivity = scheduled.activity },
                        )
                    }
                }
            }
        }
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
private fun ScheduledRow(
    scheduled: ScheduledActivity,
    today: String,
    onToggle: (Boolean) -> Unit,
    onSaveNotes: (String?) -> Unit,
    onSaveSchedule: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    val activity = scheduled.activity
    val date = activity.scheduledDate
    val isOverdue = date != null && date < today && !scheduled.done
    val isToday = date == today
    val borderColor = when {
        isOverdue -> MaterialTheme.colorScheme.error
        isToday -> MaterialTheme.colorScheme.tertiary
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(1.5.dp, it) },
    ) {
        Column {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = scheduled.done, onCheckedChange = onToggle)
                Column(modifier = Modifier.weight(1f)) {
                    Text(activity.title)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            buildString {
                                append(audienceShortLabel(activity))
                                append(" · ")
                                append(date ?: "")
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (isOverdue) {
                            Text("OVERDUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        } else if (isToday) {
                            Text("TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
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

private fun audienceShortLabel(activity: ParentalActivity): String = when (activity.audience) {
    com.jpdrw.household.data.entity.ParentalAudience.PERSONAL -> "Personal"
    com.jpdrw.household.data.entity.ParentalAudience.TOGETHER -> "Together"
    com.jpdrw.household.data.entity.ParentalAudience.ADULT_ONLY -> "Adult only"
    com.jpdrw.household.data.entity.ParentalAudience.FAMILY -> "Family & Kids"
}
