package com.jpdrw.household.data

import com.jpdrw.household.data.entity.ActivityCategory
import com.jpdrw.household.data.entity.ActivitySlot
import com.jpdrw.household.data.entity.Assignee
import com.jpdrw.household.data.entity.BudgetTier
import com.jpdrw.household.data.entity.Chore
import com.jpdrw.household.data.entity.ChoreOccurrence
import com.jpdrw.household.data.entity.ChorePhoto
import com.jpdrw.household.data.entity.ChorePlanEntry
import com.jpdrw.household.data.entity.ChoreSubtask
import com.jpdrw.household.data.entity.ChoreSubtaskCheck
import com.jpdrw.household.data.entity.FamilyActivity
import com.jpdrw.household.data.entity.FamilyActivityLog
import com.jpdrw.household.data.entity.Frequency
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.data.entity.ParentalActivityLog
import com.jpdrw.household.data.entity.ParentalAudience
import com.jpdrw.household.data.entity.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ChoreWithOccurrence(
    val chore: Chore,
    val occurrence: ChoreOccurrence?,
    val assigneeName: String,
    /** The due date this occurrence actually belongs to — may be earlier than the viewed date if overdue. */
    val effectiveDueDate: String,
    val isOverdue: Boolean,
)

data class AssigneeStat(val assigneeName: String, val completed: Int)

data class SubtaskWithChecks(
    val subtask: ChoreSubtask,
    /** Assignee IDs who have checked this subtask off for the date in question. */
    val checkedByAssigneeIds: Set<Long>,
)

data class MonthlyStats(
    val choresCompleted: Int,
    val choresTotal: Int,
    val familyActivitiesDone: Int,
    val parentalActivitiesDoneThisWeek: Int,
    val byAssignee: List<AssigneeStat>,
)

private const val MAX_OVERDUE_LOOKBACK_DAYS = 60

/** Single access point for screens: joins Room tables and fills in "for today" rows on the fly. */
class Repository(private val db: AppDatabase) {

    // --- Assignees ---
    fun observeAssignees(): Flow<List<Assignee>> = db.assigneeDao().observeAll()
    suspend fun addAssignee(name: String) = db.assigneeDao().insert(Assignee(name = name))
    suspend fun renameAssignee(assignee: Assignee, newName: String) = db.assigneeDao().update(assignee.copy(name = newName))
    suspend fun deleteAssignee(id: Long) = db.assigneeDao().deleteById(id)

    // --- Chores ---

    /**
     * A chore appears on [date] if its most recent scheduled due date on or before [date] hasn't
     * been completed yet — so a missed weekly/custom chore keeps showing (marked overdue) every
     * day until it's checked off, instead of disappearing until its next scheduled date.
     */
    fun observeChoresForDate(date: String): Flow<List<ChoreWithOccurrence>> {
        val day = LocalDate.parse(date)
        val rangeStart = day.minusDays(MAX_OVERDUE_LOOKBACK_DAYS.toLong()).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        return combine(
            db.choreDao().observeActive(),
            db.assigneeDao().observeAll(),
            db.choreDao().observeOccurrencesBetween(rangeStart, date),
        ) { chores, assignees, occurrences ->
            val assigneeNames = assignees.associateBy { it.id }
            val occurrenceByKey = occurrences.associateBy { it.choreId to it.dueDate }
            chores
                .mapNotNull { chore ->
                    val lastDue = lastScheduledDateOnOrBefore(chore, day) ?: return@mapNotNull null
                    val occurrence = occurrenceByKey[chore.id to lastDue.toString()]
                    if (occurrence?.completed == true) return@mapNotNull null
                    ChoreWithOccurrence(
                        chore = chore,
                        occurrence = occurrence,
                        assigneeName = assigneeNames[chore.assigneeId]?.name ?: "Family",
                        effectiveDueDate = lastDue.toString(),
                        isOverdue = lastDue.isBefore(day) || isPastEstimatedEndTime(chore, lastDue),
                    )
                }
                .sortedWith(
                    compareByDescending<ChoreWithOccurrence> { it.isOverdue }
                        .thenByDescending { it.chore.priority.ordinal }
                        .thenBy { it.chore.title },
                )
        }
    }

    /** Chores completed with a due date of exactly [date] — the "Completed" section on the Chores screen. */
    fun observeCompletedChoresForDate(date: String): Flow<List<ChoreWithOccurrence>> =
        combine(db.choreDao().observeActive(), db.assigneeDao().observeAll(), db.choreDao().observeOccurrencesForDate(date)) { chores, assignees, occurrences ->
            val assigneeNames = assignees.associateBy { it.id }
            val choresById = chores.associateBy { it.id }
            occurrences
                .filter { it.completed }
                .mapNotNull { occurrence ->
                    val chore = choresById[occurrence.choreId] ?: return@mapNotNull null
                    ChoreWithOccurrence(
                        chore = chore,
                        occurrence = occurrence,
                        assigneeName = assigneeNames[chore.assigneeId]?.name ?: "Family",
                        effectiveDueDate = date,
                        isOverdue = false,
                    )
                }
                .sortedBy { it.chore.title }
        }

    // --- Day roadmap (Mapper tab) ---

    /** Chores placed into [date]'s roadmap, in order, regardless of completion state. */
    fun observeDayPlan(date: String): Flow<List<ChoreWithOccurrence>> =
        combine(
            db.chorePlanDao().observeForDate(date),
            db.choreDao().observeActive(),
            db.assigneeDao().observeAll(),
            db.choreDao().observeOccurrencesForDate(date),
        ) { entries, chores, assignees, occurrences ->
            val choresById = chores.associateBy { it.id }
            val assigneeNames = assignees.associateBy { it.id }
            val occByChore = occurrences.associateBy { it.choreId }
            entries.sortedBy { it.sortOrder }.mapNotNull { entry ->
                val chore = choresById[entry.choreId] ?: return@mapNotNull null
                ChoreWithOccurrence(
                    chore = chore,
                    occurrence = occByChore[chore.id],
                    assigneeName = assigneeNames[chore.assigneeId]?.name ?: "Family",
                    effectiveDueDate = date,
                    isOverdue = false,
                )
            }
        }

    /** Chores due on [date] that haven't been placed into the roadmap yet. */
    fun observeAvailableForPlan(date: String): Flow<List<ChoreWithOccurrence>> =
        combine(observeChoresForDate(date), db.chorePlanDao().observeForDate(date)) { due, planned ->
            val plannedChoreIds = planned.map { it.choreId }.toSet()
            due.filter { it.chore.id !in plannedChoreIds }
        }

    suspend fun addToPlan(date: String, choreId: Long) {
        val dao = db.chorePlanDao()
        val nextOrder = (dao.maxSortOrder(date) ?: -1) + 1
        dao.insert(ChorePlanEntry(date = date, choreId = choreId, sortOrder = nextOrder))
    }

    suspend fun removeFromPlan(date: String, choreId: Long) = db.chorePlanDao().deleteEntry(date, choreId)

    suspend fun reorderPlan(date: String, orderedChoreIds: List<Long>) {
        val dao = db.chorePlanDao()
        dao.deleteAllForDate(date)
        orderedChoreIds.forEachIndexed { index, choreId ->
            dao.insert(ChorePlanEntry(date = date, choreId = choreId, sortOrder = index))
        }
    }

    /**
     * True once [chore]'s estimatedEndTime has passed on [dueDate], if it's due today and has a
     * time window set. Only evaluated against the real current time, so this only bites same-day;
     * a past due date is already overdue via the date check regardless of this.
     */
    private fun isPastEstimatedEndTime(chore: Chore, dueDate: LocalDate): Boolean {
        if (dueDate != LocalDate.now()) return false
        val endTime = chore.estimatedEndTime ?: return false
        val parsedEnd = runCatching { java.time.LocalTime.parse(endTime) }.getOrNull() ?: return false
        return java.time.LocalTime.now().isAfter(parsedEnd)
    }

    private fun isDueOnRaw(chore: Chore, day: LocalDate): Boolean = when (chore.frequency) {
        Frequency.DAILY -> true
        Frequency.WEEKLY -> day.dayOfWeek == java.time.DayOfWeek.MONDAY
        Frequency.TWICE_WEEKLY -> day.dayOfWeek == java.time.DayOfWeek.MONDAY || day.dayOfWeek == java.time.DayOfWeek.THURSDAY
        Frequency.CUSTOM -> {
            val interval = chore.customIntervalDays
            val createdDay = java.time.Instant.ofEpochMilli(chore.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val daysSince = ChronoUnit.DAYS.between(createdDay, day)
            interval != null && interval > 0 && daysSince >= 0 && daysSince % interval == 0L
        }
    }

    /** Walks backward from [date] (bounded by [MAX_OVERDUE_LOOKBACK_DAYS]) to find the chore's last scheduled due date. */
    private fun lastScheduledDateOnOrBefore(chore: Chore, date: LocalDate): LocalDate? {
        val createdDay = java.time.Instant.ofEpochMilli(chore.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        var day = date
        repeat(MAX_OVERDUE_LOOKBACK_DAYS + 1) {
            if (day.isBefore(createdDay)) return null
            if (isDueOnRaw(chore, day)) return day
            day = day.minusDays(1)
        }
        return null
    }

    suspend fun addChore(
        title: String,
        frequency: Frequency,
        customIntervalDays: Int?,
        assigneeId: Long,
        priority: Priority = Priority.NORMAL,
        startTime: String? = null,
        estimatedEndTime: String? = null,
    ) = db.choreDao().insert(
        Chore(
            title = title,
            frequency = frequency,
            customIntervalDays = customIntervalDays,
            assigneeId = assigneeId,
            priority = priority,
            startTime = startTime,
            estimatedEndTime = estimatedEndTime,
        ),
    )

    suspend fun updateChore(
        choreId: Long,
        title: String,
        frequency: Frequency,
        customIntervalDays: Int?,
        assigneeId: Long,
        priority: Priority = Priority.NORMAL,
        startTime: String? = null,
        estimatedEndTime: String? = null,
    ) {
        val existing = db.choreDao().findById(choreId) ?: return
        db.choreDao().update(
            existing.copy(
                title = title,
                frequency = frequency,
                customIntervalDays = customIntervalDays,
                assigneeId = assigneeId,
                priority = priority,
                startTime = startTime,
                estimatedEndTime = estimatedEndTime,
            ),
        )
    }

    suspend fun setChoreCompleted(choreId: Long, date: String, completed: Boolean, photoUri: String?) {
        val dao = db.choreDao()
        val existing = dao.findOccurrence(choreId, date)
        if (existing != null) {
            dao.updateOccurrence(existing.copy(completed = completed, completedAt = if (completed) System.currentTimeMillis() else null, completedPhotoUri = photoUri ?: existing.completedPhotoUri))
        } else {
            dao.insertOccurrence(ChoreOccurrence(choreId = choreId, dueDate = date, completed = completed, completedAt = if (completed) System.currentTimeMillis() else null, completedPhotoUri = photoUri))
        }
    }

    suspend fun retireChore(choreId: Long) = db.choreDao().deactivate(choreId)
    suspend fun deleteChore(choreId: Long) = db.choreDao().delete(choreId)

    fun observeChorePhotos(choreId: Long): Flow<List<ChorePhoto>> = db.chorePhotoDao().observeForChore(choreId)
    suspend fun addChorePhoto(choreId: Long, uri: String) = db.chorePhotoDao().insert(ChorePhoto(choreId = choreId, uri = uri))
    suspend fun deleteChorePhoto(photoId: Long) = db.chorePhotoDao().delete(photoId)

    // --- Chore subtasks ---
    fun observeSubtasks(choreId: Long, date: String): Flow<List<SubtaskWithChecks>> =
        combine(db.choreSubtaskDao().observeForChore(choreId), db.choreSubtaskDao().observeChecksForChoreAndDate(choreId, date)) { subtasks, checks ->
            val checksBySubtask = checks.groupBy { it.subtaskId }
            subtasks.map { subtask ->
                SubtaskWithChecks(subtask = subtask, checkedByAssigneeIds = checksBySubtask[subtask.id].orEmpty().map { it.assigneeId }.toSet())
            }
        }

    suspend fun addSubtask(choreId: Long, title: String) = db.choreSubtaskDao().insert(ChoreSubtask(choreId = choreId, title = title))
    suspend fun deleteSubtask(subtaskId: Long) = db.choreSubtaskDao().delete(subtaskId)

    suspend fun setSubtaskChecked(subtaskId: Long, assigneeId: Long, date: String, checked: Boolean) {
        val dao = db.choreSubtaskDao()
        val existing = dao.findCheck(subtaskId, assigneeId, date)
        if (checked && existing == null) {
            dao.insertCheck(ChoreSubtaskCheck(subtaskId = subtaskId, assigneeId = assigneeId, date = date))
        } else if (!checked && existing != null) {
            dao.deleteCheck(existing.id)
        }
    }

    // --- Family activities ---
    fun observeFamilyActivities(): Flow<List<FamilyActivity>> = db.familyActivityDao().observeActive()
    fun observeFamilyActivityLogs(date: String): Flow<List<FamilyActivityLog>> = db.familyActivityDao().observeLogsForDate(date)

    suspend fun setFamilyActivityDone(activityId: Long, date: String, done: Boolean) {
        val existing = db.familyActivityDao().findLog(activityId, date)
        db.familyActivityDao().upsertLog(
            (existing ?: FamilyActivityLog(activityId = activityId, date = date)).copy(done = done, id = existing?.id ?: 0),
        )
    }

    suspend fun addFamilyActivity(title: String, category: ActivityCategory, slot: ActivitySlot, quickOption: Boolean = false) =
        db.familyActivityDao().insert(FamilyActivity(title = title, category = category, slot = slot, quickOption = quickOption))

    suspend fun updateFamilyActivity(id: Long, title: String, category: ActivityCategory, slot: ActivitySlot, quickOption: Boolean = false) =
        db.familyActivityDao().update(FamilyActivity(id = id, title = title, category = category, slot = slot, quickOption = quickOption))

    suspend fun deleteFamilyActivity(id: Long) = db.familyActivityDao().delete(id)

    // --- Parental activities ---
    fun observeParentalActivities(): Flow<List<ParentalActivity>> = db.parentalActivityDao().observeActive()
    fun observeParentalActivityLogs(isoWeek: String): Flow<List<ParentalActivityLog>> = db.parentalActivityDao().observeLogsForWeek(isoWeek)

    suspend fun setParentalActivityDone(activityId: Long, isoWeek: String, done: Boolean) {
        val existing = db.parentalActivityDao().findLog(activityId, isoWeek)
        db.parentalActivityDao().upsertLog(
            (existing ?: ParentalActivityLog(activityId = activityId, isoWeek = isoWeek)).copy(done = done, id = existing?.id ?: 0),
        )
    }

    suspend fun addParentalActivity(title: String, audience: ParentalAudience, budget: BudgetTier, isSpicy: Boolean = false) =
        db.parentalActivityDao().insert(ParentalActivity(title = title, audience = audience, budget = budget, isSpicy = isSpicy))

    suspend fun updateParentalActivity(id: Long, title: String, audience: ParentalAudience, budget: BudgetTier, isSpicy: Boolean = false) =
        db.parentalActivityDao().update(ParentalActivity(id = id, title = title, audience = audience, budget = budget, isSpicy = isSpicy))

    suspend fun deleteParentalActivity(id: Long) = db.parentalActivityDao().delete(id)

    // --- Stats (admin) ---
    suspend fun monthlyStats(): MonthlyStats {
        val start = DateUtils.startOfMonth()
        val end = DateUtils.endOfMonth()
        val week = DateUtils.isoWeek()
        val names = db.assigneeDao().observeAll().first().associateBy { it.id }
        val byAssignee = db.choreDao().completedCountByAssignee(start, end).map {
            AssigneeStat(assigneeName = names[it.assigneeId]?.name ?: "Family", completed = it.completed)
        }
        return MonthlyStats(
            choresCompleted = db.choreDao().completedCountBetween(start, end),
            choresTotal = db.choreDao().totalCountBetween(start, end),
            familyActivitiesDone = db.familyActivityDao().doneCountBetween(start, end),
            parentalActivitiesDoneThisWeek = db.parentalActivityDao().doneCountForWeek(week),
            byAssignee = byAssignee,
        )
    }

    /** Chore occurrences still incomplete for [date] — used by the reminder worker. */
    suspend fun incompleteChoreTitlesForDate(date: String): List<String> {
        val incomplete = db.choreDao().incompleteForDate(date)
        if (incomplete.isEmpty()) return emptyList()
        val all = db.choreDao().observeActive().first().associateBy { it.id }
        return incomplete.mapNotNull { all[it.choreId]?.title }
    }
}
