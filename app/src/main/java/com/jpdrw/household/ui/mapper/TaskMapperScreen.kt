package com.jpdrw.household.ui.mapper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jpdrw.household.data.ChoreWithOccurrence
import com.jpdrw.household.data.DateUtils
import com.jpdrw.household.data.Repository
import kotlinx.coroutines.launch

/**
 * Lets the day's available chores be tapped into a roadmap, then dragged (long-press the handle)
 * into whatever order the day should actually run in. Independent of completion state — the point
 * is sequencing, not tracking what's done (that's the Chores tab).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskMapperScreen(repository: Repository) {
    val today = DateUtils.today()
    val available by repository.observeAvailableForPlan(today).collectAsState(initial = emptyList())
    val planned by repository.observeDayPlan(today).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Day Roadmap") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Available tasks", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a task to add it to today's roadmap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
            if (available.isEmpty()) {
                Text("All of today's chores are already in the roadmap.", style = MaterialTheme.typography.bodySmall)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { item ->
                        AvailableTaskCard(item = item, onClick = { scope.launch { repository.addToPlan(today, item.chore.id) } })
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 24.dp))
            Text("Today's roadmap", style = MaterialTheme.typography.titleMedium)
            if (planned.isEmpty()) {
                Text(
                    "Add tasks above, then long-press the handle to drag them into order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                ReorderablePlanList(
                    items = planned,
                    onReorder = { newOrder -> scope.launch { repository.reorderPlan(today, newOrder.map { it.chore.id }) } },
                    onRemove = { choreId -> scope.launch { repository.removeFromPlan(today, choreId) } },
                )
            }
        }
    }
}

@Composable
private fun AvailableTaskCard(item: ChoreWithOccurrence, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.chore.title, style = MaterialTheme.typography.bodyMedium)
            Text(item.assigneeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReorderablePlanList(
    items: List<ChoreWithOccurrence>,
    onReorder: (List<ChoreWithOccurrence>) -> Unit,
    onRemove: (Long) -> Unit,
) {
    var list by remember(items) { mutableStateOf(items) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 76.dp.toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        list.forEach { item ->
            val index = list.indexOf(item)
            val isDragging = index == draggingIndex
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .zIndex(if (isDragging) 1f else 0f),
            ) {
                Box {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.chore.title, style = MaterialTheme.typography.bodyLarge)
                            Text(item.assigneeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemove(item.chore.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove from roadmap")
                        }
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            modifier = Modifier
                                .size(28.dp)
                                .pointerInput(item.chore.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = list.indexOf(item)
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            draggingIndex = -1
                                            dragOffset = 0f
                                            onReorder(list)
                                        },
                                        onDragCancel = {
                                            draggingIndex = -1
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount.y
                                            val moveBy = (dragOffset / itemHeightPx).toInt()
                                            if (moveBy != 0 && draggingIndex != -1) {
                                                val newIndex = (draggingIndex + moveBy).coerceIn(0, list.size - 1)
                                                if (newIndex != draggingIndex) {
                                                    list = list.toMutableList().apply { add(newIndex, removeAt(draggingIndex)) }
                                                    draggingIndex = newIndex
                                                    dragOffset -= moveBy * itemHeightPx
                                                }
                                            }
                                        },
                                    )
                                },
                        )
                    }
                }
            }
        }
    }
}
