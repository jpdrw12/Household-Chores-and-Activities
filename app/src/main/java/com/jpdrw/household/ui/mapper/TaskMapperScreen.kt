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
import com.jpdrw.household.data.DateUtils
import com.jpdrw.household.data.PlanTask
import com.jpdrw.household.data.Repository
import com.jpdrw.household.data.entity.PlanItemType
import kotlinx.coroutines.launch

/**
 * Lets the day's available chores, family activities, and "For Us" activities be tapped into a
 * roadmap, then dragged (long-press the handle) into whatever order the day should actually run
 * in. Independent of completion state — the point is sequencing, not tracking what's done (that's
 * the Chores/Activities/For Us tabs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskMapperScreen(repository: Repository, appPrefs: AppPrefs) {
    val today = DateUtils.today()
    val availableRaw by repository.observeAvailableForPlan(today).collectAsState(initial = emptyList())
    val plannedRaw by repository.observeDayPlan(today).collectAsState(initial = emptyList())
    val planned = plannedRaw
    var typeFilter by remember { mutableStateOf<PlanItemType?>(null) }
    val available = availableRaw
        .filter { typeFilter == null || it.itemType == typeFilter }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Day Roadmap") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Available tasks", style = MaterialTheme.typography.titleMedium)

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
            Text("Today's roadmap", style = MaterialTheme.typography.titleMedium)
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
                    onReorder = { newOrder -> scope.launch { repository.reorderPlan(today, newOrder.map { it.itemType to it.itemId }) } },
                    onRemove = { task -> scope.launch { repository.removeFromPlan(today, task.itemType, task.itemId) } },
                )
            }
        }
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
                    Text("SCHEDULED TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun ReorderablePlanList(
    items: List<PlanTask>,
    onReorder: (List<PlanTask>) -> Unit,
    onRemove: (PlanTask) -> Unit,
) {
    var list by remember(items) { mutableStateOf(items) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 60.dp.toPx() }

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
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
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
                }
            }
        }
    }
}
