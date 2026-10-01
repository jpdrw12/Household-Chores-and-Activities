package com.jpdrw.household.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.jpdrw.household.data.MonthlyStats
import com.jpdrw.household.data.Repository
import com.jpdrw.household.data.ThemeMode
import com.jpdrw.household.data.entity.Assignee
import kotlinx.coroutines.launch

/** Admin-only view of tracked data and app settings. Reached via the bottom nav's "Admin" tab, out of the way of daily use. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(repository: Repository, appPrefs: AppPrefs) {
    var stats by remember { mutableStateOf<MonthlyStats?>(null) }
    val themeMode by appPrefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val assignees by repository.observeAssignees().collectAsState(initial = emptyList())
    var renamingAssignee by remember { mutableStateOf<Assignee?>(null) }
    var deletingAssignee by remember { mutableStateOf<Assignee?>(null) }
    var newAssigneeName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        stats = repository.monthlyStats()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Admin · Stats") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("This month", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            val current = stats
            if (current == null) {
                Text("Loading…")
            } else {
                StatCard(
                    title = "Chores completed",
                    value = "${current.choresCompleted} / ${current.choresTotal}",
                    progress = if (current.choresTotal > 0) current.choresCompleted.toFloat() / current.choresTotal else 0f,
                )
                StatCard(title = "Family activities logged", value = current.familyActivitiesDone.toString(), progress = null)
                StatCard(title = "Parental activities this week", value = current.parentalActivitiesDoneThisWeek.toString(), progress = null)

                if (current.byAssignee.isNotEmpty()) {
                    Text("By assignee this month", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    current.byAssignee.forEach { stat ->
                        StatCard(title = stat.assigneeName, value = "${stat.completed} completed", progress = null)
                    }
                }
            }

            Text("Appearance", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { scope.launch { appPrefs.setThemeMode(mode) } },
                        label = { Text(mode.label()) },
                    )
                }
            }

            Text("Assignees", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    assignees.forEach { assignee ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(assignee.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { renamingAssignee = assignee }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename ${assignee.name}")
                            }
                            if (!assignee.isDefault) {
                                IconButton(onClick = { deletingAssignee = assignee }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${assignee.name}")
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newAssigneeName,
                            onValueChange = { newAssigneeName = it },
                            label = { Text("New assignee") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                val name = newAssigneeName.trim()
                                if (name.isNotBlank()) {
                                    scope.launch { repository.addAssignee(name) }
                                    newAssigneeName = ""
                                }
                            },
                        ) { Icon(Icons.Filled.Add, contentDescription = "Add assignee") }
                    }
                }
            }

            Text(
                "Data is stored locally on this device. Assignees sync across devices (proof of concept); other data doesn't sync yet.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }

    renamingAssignee?.let { assignee ->
        var text by remember(assignee.id) { mutableStateOf(assignee.name) }
        AlertDialog(
            onDismissRequest = { renamingAssignee = null },
            title = { Text("Rename assignee") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                Button(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val name = text.trim()
                        scope.launch { repository.renameAssignee(assignee, name) }
                        renamingAssignee = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renamingAssignee = null }) { Text("Cancel") } },
        )
    }

    deletingAssignee?.let { assignee ->
        AlertDialog(
            onDismissRequest = { deletingAssignee = null },
            title = { Text("Delete \"${assignee.name}\"?") },
            text = { Text("Chores assigned to them will show as \"Family\" instead.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteAssignee(assignee.id) }
                    deletingAssignee = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingAssignee = null }) { Text("Cancel") } },
        )
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun StatCard(title: String, value: String, progress: Float?) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Text(value, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
