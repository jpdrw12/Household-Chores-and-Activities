package com.jpdrw.household.data

import com.jpdrw.household.data.entity.ActivityCategory
import com.jpdrw.household.data.entity.ActivitySlot
import com.jpdrw.household.data.entity.Assignee
import com.jpdrw.household.data.entity.BudgetTier
import com.jpdrw.household.data.entity.Chore
import com.jpdrw.household.data.entity.ChoreOccurrence
import com.jpdrw.household.data.entity.ChorePhoto
import com.jpdrw.household.data.entity.ChoreSubtask
import com.jpdrw.household.data.entity.ChoreSubtaskCheck
import com.jpdrw.household.data.entity.FamilyActivity
import com.jpdrw.household.data.entity.FamilyActivityLog
import com.jpdrw.household.data.entity.Frequency
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.data.entity.ParentalActivityLog
import com.jpdrw.household.data.entity.ParentalAudience
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ChoreWithOccurrence(
    val chore: Chore,
    val occurrence: ChoreOccurrence?,
    val assigneeName: String,
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

/** Single access point for screens: joins Room tables and fills in "for today" rows on the fly. */
class Repository(private val db: AppDatabase) {

    // --- Assignees ---
    fun observeAssignees(): Flow<List<Assignee>> = db.assigneeDao().observeAll()
    suspend fun addAssignee(name: String) = db.assigneeDao().insert(Assignee(name = name))

    // --- Chores ---
    fun observeChoresForDate(date: String): Flow<List<ChoreWithOccurrence>> =
        combine(db.choreDao().observeActive(), db.assigneeDao().observeAll(), db.choreDao().observeOccurrencesForDate(date)) { chores, assignees, occurrences ->
            val assigneeNames = assignees.associateBy { it.id }
            val occurrenceByChore = occurrences.associateBy { it.choreId }
            chores
                .filter { isDueOn(it, date) || occurrenceByChore.containsKey(it.id) }
                .map { chore ->
                    ChoreWithOccurrence(
                        chore = chore,
                        occurrence = occurrenceByChore[chore.id],
                        assigneeName = assigneeNames[chore.assigneeId]?.name ?: "Family",
                    )
                }
        }

    private fun isDueOn(chore: Chore, date: String): Boolean {
        val day = LocalDate.parse(date)
        return when (chore.frequency) {
            Frequency.DAILY -> true
            Frequency.WEEKLY -> day.dayOfWeek == java.time.DayOfWeek.MONDAY
            Frequency.TWICE_WEEKLY -> day.dayOfWeek == java.time.DayOfWeek.MONDAY || day.dayOfWeek == java.time.DayOfWeek.THURSDAY
            Frequency.CUSTOM -> {
                val interval = chore.customIntervalDays ?: return false
                if (interval <= 0) return false
                val createdDay = java.time.Instant.ofEpochMilli(chore.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val daysSince = ChronoUnit.DAYS.between(createdDay, day)
                daysSince >= 0 && daysSince % interval == 0L
            }
        }
    }

    suspend fun addChore(title: String, frequency: Frequency, customIntervalDays: Int?, assigneeId: Long) =
        db.choreDao().insert(Chore(title = title, frequency = frequency, customIntervalDays = customIntervalDays, assigneeId = assigneeId))

    suspend fun updateChore(choreId: Long, title: String, frequency: Frequency, customIntervalDays: Int?, assigneeId: Long) {
        val existing = db.choreDao().findById(choreId) ?: return
        db.choreDao().update(existing.copy(title = title, frequency = frequency, customIntervalDays = customIntervalDays, assigneeId = assigneeId))
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

    suspend fun addFamilyActivity(title: String, category: ActivityCategory, slot: ActivitySlot) =
        db.familyActivityDao().insert(FamilyActivity(title = title, category = category, slot = slot))

    suspend fun updateFamilyActivity(id: Long, title: String, category: ActivityCategory, slot: ActivitySlot) =
        db.familyActivityDao().update(FamilyActivity(id = id, title = title, category = category, slot = slot))

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

    suspend fun addParentalActivity(title: String, audience: ParentalAudience, budget: BudgetTier) =
        db.parentalActivityDao().insert(ParentalActivity(title = title, audience = audience, budget = budget))

    suspend fun updateParentalActivity(id: Long, title: String, audience: ParentalAudience, budget: BudgetTier) =
        db.parentalActivityDao().update(ParentalActivity(id = id, title = title, audience = audience, budget = budget))

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
