package com.jpdrw.household.data

import com.jpdrw.household.data.entity.Assignee
import com.jpdrw.household.data.entity.Chore
import com.jpdrw.household.data.entity.ChoreOccurrence
import com.jpdrw.household.data.entity.FamilyActivity
import com.jpdrw.household.data.entity.FamilyActivityLog
import com.jpdrw.household.data.entity.Frequency
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.data.entity.ParentalActivityLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class ChoreWithOccurrence(
    val chore: Chore,
    val occurrence: ChoreOccurrence?,
    val assigneeName: String,
)

data class MonthlyStats(
    val choresCompleted: Int,
    val choresTotal: Int,
    val familyActivitiesDone: Int,
    val parentalActivitiesDoneThisWeek: Int,
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
                .filter { isDueOn(it.frequency, date) || occurrenceByChore.containsKey(it.id) }
                .map { chore ->
                    ChoreWithOccurrence(
                        chore = chore,
                        occurrence = occurrenceByChore[chore.id],
                        assigneeName = assigneeNames[chore.assigneeId]?.name ?: "Family",
                    )
                }
        }

    private fun isDueOn(frequency: Frequency, date: String): Boolean {
        val dayOfWeek = java.time.LocalDate.parse(date).dayOfWeek
        return when (frequency) {
            Frequency.DAILY -> true
            Frequency.WEEKLY -> dayOfWeek == java.time.DayOfWeek.MONDAY
            Frequency.TWICE_WEEKLY -> dayOfWeek == java.time.DayOfWeek.MONDAY || dayOfWeek == java.time.DayOfWeek.THURSDAY
            Frequency.CUSTOM -> false
        }
    }

    suspend fun addChore(title: String, frequency: Frequency, assigneeId: Long, photoRefUri: String?) =
        db.choreDao().insert(Chore(title = title, frequency = frequency, assigneeId = assigneeId, photoRefUri = photoRefUri))

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

    // --- Family activities ---
    fun observeFamilyActivities(): Flow<List<FamilyActivity>> = db.familyActivityDao().observeActive()
    fun observeFamilyActivityLogs(date: String): Flow<List<FamilyActivityLog>> = db.familyActivityDao().observeLogsForDate(date)

    suspend fun setFamilyActivityDone(activityId: Long, date: String, done: Boolean) {
        val existing = db.familyActivityDao().findLog(activityId, date)
        db.familyActivityDao().upsertLog(
            (existing ?: FamilyActivityLog(activityId = activityId, date = date)).copy(done = done, id = existing?.id ?: 0),
        )
    }

    suspend fun addFamilyActivity(title: String, category: com.jpdrw.household.data.entity.ActivityCategory, slot: com.jpdrw.household.data.entity.ActivitySlot) =
        db.familyActivityDao().insert(FamilyActivity(title = title, category = category, slot = slot))

    // --- Parental activities ---
    fun observeParentalActivities(): Flow<List<ParentalActivity>> = db.parentalActivityDao().observeActive()
    fun observeParentalActivityLogs(isoWeek: String): Flow<List<ParentalActivityLog>> = db.parentalActivityDao().observeLogsForWeek(isoWeek)

    suspend fun setParentalActivityDone(activityId: Long, isoWeek: String, done: Boolean) {
        val existing = db.parentalActivityDao().findLog(activityId, isoWeek)
        db.parentalActivityDao().upsertLog(
            (existing ?: ParentalActivityLog(activityId = activityId, isoWeek = isoWeek)).copy(done = done, id = existing?.id ?: 0),
        )
    }

    suspend fun addParentalActivity(title: String, audience: com.jpdrw.household.data.entity.ParentalAudience, budget: com.jpdrw.household.data.entity.BudgetTier) =
        db.parentalActivityDao().insert(ParentalActivity(title = title, audience = audience, budget = budget))

    // --- Stats (admin) ---
    suspend fun monthlyStats(): MonthlyStats {
        val start = DateUtils.startOfMonth()
        val end = DateUtils.endOfMonth()
        val week = DateUtils.isoWeek()
        return MonthlyStats(
            choresCompleted = db.choreDao().completedCountBetween(start, end),
            choresTotal = db.choreDao().totalCountBetween(start, end),
            familyActivitiesDone = db.familyActivityDao().doneCountBetween(start, end),
            parentalActivitiesDoneThisWeek = db.parentalActivityDao().doneCountForWeek(week),
        )
    }
}
