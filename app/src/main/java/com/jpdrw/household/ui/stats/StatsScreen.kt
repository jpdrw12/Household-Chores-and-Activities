package com.jpdrw.household.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jpdrw.household.data.MonthlyStats
import com.jpdrw.household.data.Repository

/** Admin-only view of tracked data. Reached via the bottom nav's "Admin" tab, out of the way of daily use. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(repository: Repository) {
    var stats by remember { mutableStateOf<MonthlyStats?>(null) }

    LaunchedEffect(Unit) {
        stats = repository.monthlyStats()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Admin · Stats") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            }
            Text(
                "Data is stored locally on this device only. No account or cloud sync.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
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
