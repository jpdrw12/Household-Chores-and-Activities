package com.jpdrw.household.ui.mapper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jpdrw.household.data.AppPrefs
import com.jpdrw.household.data.PlanTask
import com.jpdrw.household.data.Repository
import com.jpdrw.household.data.entity.DayPeriod
import com.jpdrw.household.data.entity.PlanItemType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.TextButton

/**
 * Lets the day's available chores, family activities, and "For Us" activities be tapped into a
 * roadmap, then dragged (long-press the handle) into whatever order the day should actually run
 * in, with an optional Morning/Afternoon/Night label per task. Each task's checkbox here reads
 * from and writes back to the same completion state as its own tab (Chores/Activities/For Us) —
 * there's no separate "done in the roadmap" flag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskMapperScreen(repository: Repository, appPrefs: AppPrefs) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val today = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val availableRaw by repository.observeAvailableForPlan(today).collectAsState(initial = emptyList())
    val plannedRaw by repository.observeDayPlan(today).collectAsState(initial = emptyList())
    val planned = plannedRaw
    var typeFilter by remember { mutableStateOf<PlanItemType?>(null) }
    val available = availableRaw
        .filter { typeFilter == null || it.itemType == typeFilter }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Day Roadmap") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            MapperDateSelector(selectedDate = selectedDate, onDateChange = { selectedDate = it })

            Text("Available tasks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("All") })
                PlanItemType.entries.forEach { type ->
                    FilterChip(selected = typeFilter == type, onClick = { typeFilter = type }, label = { Text(type.label()) })
                }
            }

            Spacer(modifier = Modifier.padding(top = 8.dp))
            if (available.isEmpty()) {
                Text(
                    "Nothing available in this filter — already in the roadmap, or try a different tab above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(available, key = { it.itemType.name + it.itemId }) { task ->
                        AvailableTaskCard(task = task, onClick = { scope.launch { repository.addToPlan(today, task.itemType, task.itemId) } })
                    }
                }
            }

            Spacer(modifier = Modifier.padding(top = 24.dp))
            Text(
                if (selectedDate == LocalDate.now()) "Today's roadmap" else "Roadmap for $today",
                style = MaterialTheme.typography.titleMedium,
            )
            if (planned.isEmpty()) {
                Text(
                    "Tap a task above, then long-press the ≡ handle to drag it into order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                ReorderablePlanList(
                    items = planned,
                    onReorder = { newOrder -> scope.launch { repository.reorderPlan(today, newOrder) } },
                    onRemove = { task -> scope.launch { repository.removeFromPlan(today, task.itemType, task.itemId) } },
                    onToggleCompleted = { task, checked -> scope.launch { repository.setPlanItemCompleted(task, checked) } },
                    onSetPeriod = { task, period -> scope.launch { repository.setPlanPeriod(today, task.itemType, task.itemId, period) } },
                )
            }
        }
    }
}

@Composable
private fun MapperDateSelector(selectedDate: LocalDate, onDateChange: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = { onDateChange(selectedDate.minusDays(1)) }) { Text("< Prev") }
        Text(
            if (selectedDate == LocalDate.now()) "Today · $selectedDate" else selectedDate.toString(),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = { onDateChange(selectedDate.plusDays(1)) }) { Text("Next >") }
    }
}

private fun PlanItemType.label(): String = when (this) {
    PlanItemType.CHORE -> "Chores"
    PlanItemType.FAMILY_ACTIVITY -> "Activities"
    PlanItemType.PARENTAL_ACTIVITY -> "For Us"
}

@Composable
private fun PlanItemType.color(): Color = when (this) {
    PlanItemType.CHORE -> MaterialTheme.colorScheme.primary
    PlanItemType.FAMILY_ACTIVITY -> MaterialTheme.colorScheme.secondary
    PlanItemType.PARENTAL_ACTIVITY -> MaterialTheme.colorScheme.tertiary
}

/** A thin colored bar is the only type indicator — keeps the card to two lines of text. */
@Composable
private fun TypeStripe(type: PlanItemType, modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(4.dp).fillMaxHeight().background(type.color()))
}

@Composable
private fun AvailableTaskCard(task: PlanTask, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(150.dp).clickable(onClick = onClick),
        border = if (task.isScheduledToday) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
        } else null,
    ) {
        Row {
            TypeStripe(task.itemType)
            Column(modifier = Modifier.padding(8.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text(task.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (task.isScheduledToday) {
                    Text("SCHEDULED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

private fun DayPeriod.label(): String = when (this) {
    DayPeriod.MORNING -> "Morning"
    DayPeriod.AFTERNOON -> "Afternoon"
    DayPeriod.NIGHT -> "Night"
}

@Composable
private fun ReorderablePlanList(
    items: List<PlanTask>,
    onReorder: (List<PlanTask>) -> Unit,
    onRemove: (PlanTask) -> Unit,
    onToggleCompleted: (PlanTask, Boolean) -> Unit,
    onSetPeriod: (PlanTask, DayPeriod?) -> Unit,
) {
    var list by remember(items) { mutableStateOf(items) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    // Approximate — each card is now two rows tall with the period chips, so drag-to-reorder
    // thresholds are a rough estimate rather than an exact pixel match; same tradeoff as before.
    val itemHeightPx = with(LocalDensity.current) { 92.dp.toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        list.forEach { task ->
            val index = list.indexOf(task)
            val isDragging = index == draggingIndex
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .zIndex(if (isDragging) 1f else 0f),
                border = if (task.isScheduledToday) {
                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
                } else null,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeStripe(task.itemType)
                    Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = task.completed, onCheckedChange = { onToggleCompleted(task, it) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    textDecoration = if (task.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                )
                                Text(task.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            IconButton(onClick = { onRemove(task) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove from roadmap")
                            }
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Drag to reorder",
                                modifier = Modifier
                                    .size(24.dp)
                                    .pointerInput(task.itemType, task.itemId) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingIndex = list.indexOf(task)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 40.dp)) {
                            DayPeriod.entries.forEach { period ->
                                FilterChip(
                                    selected = task.period == period,
                                    onClick = { onSetPeriod(task, if (task.period == period) null else period) },
                                    label = { Text(period.label(), style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
