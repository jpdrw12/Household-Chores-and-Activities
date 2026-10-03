package com.jpdrw.household.data

import com.jpdrw.household.data.entity.ActivityCategory
import com.jpdrw.household.data.entity.ActivityIdea
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
import com.jpdrw.household.data.entity.PlanEntry
import com.jpdrw.household.data.entity.PlanItemType
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
    /** Who actually checked it off, if completed and recorded — may differ from [assigneeName]. */
    val completedByName: String? = null,
)

data class AssigneeStat(val assigneeName: String, val completed: Int)

/** An active chore with nothing due today — not because it was completed today (that's
 *  [ChoreWithOccurrence] in the Completed section), but because today just isn't one of its
 *  scheduled days and it has no overdue instance hanging over it either. */
data class UnscheduledChore(val chore: Chore, val assigneeName: String)

data class SubtaskWithChecks(
    val subtask: ChoreSubtask,
    /** Assignee IDs who have checked this subtask off for the date in question. */
    val checkedByAssigneeIds: Set<String>,
)

data class PlanTask(
    val itemType: PlanItemType,
    val itemId: String,
    val title: String,
    val subtitle: String,
    val isSpicy: Boolean = false,
    /** True for a PARENTAL_ACTIVITY whose scheduledDate matches the Mapper's selected date — highlighted in the roadmap. */
    val isScheduledToday: Boolean = false,
)

private fun audienceLabel(audience: ParentalAudience): String = when (audience) {
    ParentalAudience.PERSONAL -> "Personal"
    ParentalAudience.TOGETHER -> "Together"
    ParentalAudience.ADULT_ONLY -> "Adult only"
    ParentalAudience.FAMILY -> "Family & Kids"
}

data class ScheduledActivity(
    val activity: ParentalActivity,
    val done: Boolean,
    /** The ISO week activity.scheduledDate falls in — needed so toggling "done" records against
     *  the right week regardless of which week it's currently viewed in. */
    val isoWeek: String,
)

data class MonthlyStats(
    val choresCompleted: Int,
    val choresTotal: Int,
    val familyActivitiesDone: Int,
    val parentalActivitiesDoneThisWeek: Int,
    val byAssignee: List<AssigneeStat>,
)

private const val MAX_OVERDUE_LOOKBACK_DAYS = 60

/** Single access point for screens: joins Room tables and fills in "for today" rows on the fly.
 *  [householdId] (see HouseholdId.kt) scopes every synced collection to this household alone —
 *  passed in from HouseholdApp, which resolves it once at startup before constructing this.
 *  [context] is only needed for ChorePhotoSync (compressing/decoding photo bytes to local files). */
class Repository(private val db: AppDatabase, householdId: String, context: android.content.Context) {
    private val assigneeSync = AssigneeSync(db.assigneeDao(), householdId)
    private val chorePhotoSync = ChorePhotoSync(db.chorePhotoDao(), context.applicationContext, householdId)
    private val choreSync = ChoreSync(db.choreDao(), householdId)
    private val familyActivitySync = FamilyActivitySync(db.familyActivityDao(), householdId)
    private val parentalActivitySync = ParentalActivitySync(db.parentalActivityDao(), householdId)
    private val choreOccurrenceSync = ChoreOccurrenceSync(db.choreDao(), householdId)
    private val choreSubtaskSync = ChoreSubtaskSync(db.choreSubtaskDao(), householdId)
    private val choreSubtaskCheckSync = ChoreSubtaskCheckSync(db.choreSubtaskDao(), householdId)
    private val activityIdeaSync = ActivityIdeaSync(db.activityIdeaDao(), householdId)
    private val familyActivityLogSync = FamilyActivityLogSync(db.familyActivityDao(), householdId)
    private val parentalActivityLogSync = ParentalActivityLogSync(db.parentalActivityDao(), householdId)

    /** Starts mirroring every synced Firestore collection into Room. Call once, after sign-in,
     *  from HouseholdApp — see each XxxSync class's own doc comment for the design. */
    fun startSync(scope: kotlinx.coroutines.CoroutineScope) {
        assigneeSync.start(scope)
        chorePhotoSync.start(scope)
        choreSync.start(scope)
        familyActivitySync.start(scope)
        parentalActivitySync.start(scope)
        choreOccurrenceSync.start(scope)
        choreSubtaskSync.start(scope)
        choreSubtaskCheckSync.start(scope)
        activityIdeaSync.start(scope)
        familyActivityLogSync.start(scope)
        parentalActivityLogSync.start(scope)
    }


    // --- Assignees ---
    // Proof-of-concept for cross-device sync: Assignee is the one entity mirrored to Firestore
    // (see AssigneeSync below). Every local read still goes through Room as before — Firestore is
    // purely a sync transport that keeps Room's `assignees` table in agreement across devices.
    fun observeAssignees(): Flow<List<Assignee>> = db.assigneeDao().observeAll()

    suspend fun addAssignee(name: String) {
        val assignee = Assignee(name = name)
        db.assigneeDao().insert(assignee)
        assigneeSync.push(assignee)
    }

    suspend fun renameAssignee(assignee: Assignee, newName: String) {
        val updated = assignee.copy(name = newName)
        db.assigneeDao().update(updated)
        assigneeSync.push(updated)
    }

    suspend fun deleteAssignee(id: String) {
        db.assigneeDao().deleteById(id)
        assigneeSync.delete(id)
    }

    // --- Chores ---

    /**
     * A chore is due on [date] if [date] is literally one of its scheduled days. Deliberately
     * does NOT also fall back to "or it's the chore's creation day" the way
     * [lastScheduledDateOnOrBefore] does for the Overdue section: that fallback exists so a
     * freshly-seeded/added chore isn't invisible before its first real cycle date, but it's
     * redundant here — a chore added through the UI already defaults its due day to today's
     * weekday (see ChoreDialog), so [isDueOnRaw] alone already covers it. Including the fallback
     * here too caused every seeded chore to count as "due today" on any day a destructive schema
     * migration re-seeds them (createdAt resets to that moment for anything not already present),
     * which defeated the Due/Overdue/Not-scheduled split entirely on migration day. A chore that
     * only matches via the creation-day fallback now lands in Overdue instead — still prominent,
     * not hidden, just not forced into the main list. Doesn't look at completion state or past
     * misses; see [observeOverdueChoresForDate] for those.
     */
    private fun isDueToday(chore: Chore, day: LocalDate): Boolean = isDueOnRaw(chore, day)

    /** Chores whose scheduled day is literally [date], not yet completed for it. */
    fun observeChoresForDate(date: String): Flow<List<ChoreWithOccurrence>> {
        val day = LocalDate.parse(date)
        return combine(
            db.choreDao().observeActive(),
            db.assigneeDao().observeAll(),
            db.choreDao().observeOccurrencesForDate(date),
        ) { chores, assignees, occurrences ->
            val assigneeNames = assignees.associateBy { it.id }
            val occurrenceByChoreId = occurrences.associateBy { it.choreId }
            chores
                .filter { isDueToday(it, day) }
                .mapNotNull { chore ->
                    val occurrence = occurrenceByChoreId[chore.id]
                    if (occurrence?.completed == true) return@mapNotNull null
                    ChoreWithOccurrence(
                        chore = chore,
                        occurrence = occurrence,
                        assigneeName = assigneeNames[chore.assigneeId]?.name ?: "Family",
                        effectiveDueDate = date,
                        isOverdue = isPastEstimatedEndTime(chore, day),
                    )
                }
                .sortedWith(
                    compareByDescending<ChoreWithOccurrence> { it.isOverdue }
                        .thenByDescending { it.chore.priority.ordinal }
                        .thenBy { it.chore.title },
                )
        }
    }

    /**
     * Chores whose scheduled day was some day *before* [date] (not [date] itself — that's
     * [observeChoresForDate]) and still haven't been checked off for it. Separated out so a
     * Monday-only chore doesn't sit in the main "due today" list on a Saturday just because it
     * was missed — it belongs here instead, distinct from both "due today" and "not scheduled
     * today at all" ([observeChoresNotScheduledForDate]).
     */
    fun observeOverdueChoresForDate(date: String): Flow<List<ChoreWithOccurrence>> {
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
                .filter { !isDueToday(it, day) }
                .mapNotNull { chore ->
                    val lastDue = lastScheduledDateOnOrBefore(chore, day) ?: return@mapNotNull null
                    val occurrence = occurrenceByKey[chore.id to lastDue.toString()]
                    if (occurrence?.completed == true) return@mapNotNull null
                    ChoreWithOccurrence(
                        chore = chore,
                        occurrence = occurrence,
                        assigneeName = assigneeNames[chore.assigneeId]?.name ?: "Family",
                        effectiveDueDate = lastDue.toString(),
                        isOverdue = true,
                    )
                }
                .sortedWith(compareByDescending<ChoreWithOccurrence> { it.chore.priority.ordinal }.thenBy { it.chore.title })
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
                        completedByName = assigneeNames[occurrence.completedByAssigneeId]?.name,
                    )
                }
                .sortedBy { it.chore.title }
        }

    /**
     * Active chores where [date] isn't a scheduled day at all ([observeChoresForDate] doesn't
     * include it) and there's no missed-and-incomplete past occurrence hanging over it either
     * ([observeOverdueChoresForDate] doesn't include it). Mutually exclusive with both of those
     * and with the Completed section.
     */
    fun observeChoresNotScheduledForDate(date: String): Flow<List<UnscheduledChore>> {
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
                .filter { chore ->
                    if (isDueToday(chore, day)) return@filter false
                    val lastDue = lastScheduledDateOnOrBefore(chore, day) ?: return@filter true
                    occurrenceByKey[chore.id to lastDue.toString()]?.completed == true
                }
                .map { chore -> UnscheduledChore(chore = chore, assigneeName = assigneeNames[chore.assigneeId]?.name ?: "Family") }
                .sortedBy { it.chore.title }
        }
    }

    // --- Day roadmap (Mapper tab) — mixes chores, family activities, and "For Us" activities ---

    private fun availableFamilyActivities(date: String): Flow<List<FamilyActivity>> =
        combine(db.familyActivityDao().observeActive(), db.familyActivityDao().observeLogsForDate(date)) { activities, logs ->
            val doneIds = logs.filter { it.done }.map { it.activityId }.toSet()
            activities.filter { it.id !in doneIds }
        }

    private fun availableParentalActivities(week: String): Flow<List<ParentalActivity>> =
        combine(db.parentalActivityDao().observeActive(), db.parentalActivityDao().observeLogsForWeek(week)) { activities, logs ->
            val doneIds = logs.filter { it.done }.map { it.activityId }.toSet()
            activities.filter { it.id !in doneIds }
        }

    /** Tasks (of any type) due on [date] that haven't been placed into the roadmap yet. Chores
     *  includes both due-today and overdue — [observeChoresForDate] and
     *  [observeOverdueChoresForDate] split those for the Chores screen's display, but both still
     *  need a day's work done, so both are plannable. */
    fun observeAvailableForPlan(date: String): Flow<List<PlanTask>> =
        combine(
            observeChoresForDate(date),
            observeOverdueChoresForDate(date),
            availableFamilyActivities(date),
            availableParentalActivities(DateUtils.isoWeek(LocalDate.parse(date))),
            db.planDao().observeForDate(date),
        ) { chores, overdueChores, familyActs, parentalActs, planned ->
            val plannedKeys = planned.map { it.itemType to it.itemId }.toSet()
            buildList {
                (chores + overdueChores).forEach { c -> add(PlanTask(PlanItemType.CHORE, c.chore.id, c.chore.title, c.assigneeName)) }
                familyActs.forEach { a -> add(PlanTask(PlanItemType.FAMILY_ACTIVITY, a.id, a.title, if (a.category == ActivityCategory.INDOOR) "Indoor" else "Outdoor")) }
                parentalActs.forEach { a -> add(PlanTask(PlanItemType.PARENTAL_ACTIVITY, a.id, a.title, audienceLabel(a.audience), isSpicy = a.isSpicy, isScheduledToday = a.scheduledDate == date)) }
            }.filter { (it.itemType to it.itemId) !in plannedKeys }
        }

    /** Tasks placed into [date]'s roadmap, in order, regardless of completion state. */
    fun observeDayPlan(date: String): Flow<List<PlanTask>> =
        combine(
            db.planDao().observeForDate(date),
            db.choreDao().observeActive(),
            db.assigneeDao().observeAll(),
            db.familyActivityDao().observeActive(),
            db.parentalActivityDao().observeActive(),
        ) { entries, chores, assignees, familyActs, parentalActs ->
            val choresById = chores.associateBy { it.id }
            val assigneeNames = assignees.associateBy { it.id }
            val familyById = familyActs.associateBy { it.id }
            val parentalById = parentalActs.associateBy { it.id }
            entries.sortedBy { it.sortOrder }.mapNotNull { entry ->
                when (entry.itemType) {
                    PlanItemType.CHORE -> choresById[entry.itemId]?.let { c ->
                        PlanTask(PlanItemType.CHORE, c.id, c.title, assigneeNames[c.assigneeId]?.name ?: "Family")
                    }
                    PlanItemType.FAMILY_ACTIVITY -> familyById[entry.itemId]?.let { a ->
                        PlanTask(PlanItemType.FAMILY_ACTIVITY, a.id, a.title, if (a.category == ActivityCategory.INDOOR) "Indoor" else "Outdoor")
                    }
                    PlanItemType.PARENTAL_ACTIVITY -> parentalById[entry.itemId]?.let { a ->
                        PlanTask(PlanItemType.PARENTAL_ACTIVITY, a.id, a.title, audienceLabel(a.audience), isSpicy = a.isSpicy, isScheduledToday = a.scheduledDate == entry.date)
                    }
                }
            }
        }

    suspend fun addToPlan(date: String, itemType: PlanItemType, itemId: String) {
        val dao = db.planDao()
        val nextOrder = (dao.maxSortOrder(date) ?: -1) + 1
        dao.insert(PlanEntry(date = date, itemType = itemType, itemId = itemId, sortOrder = nextOrder))
    }

    suspend fun removeFromPlan(date: String, itemType: PlanItemType, itemId: String) = db.planDao().deleteEntry(date, itemType, itemId)

    suspend fun reorderPlan(date: String, orderedItems: List<Pair<PlanItemType, String>>) {
        val dao = db.planDao()
        dao.deleteAllForDate(date)
        orderedItems.forEachIndexed { index, (itemType, itemId) ->
            dao.insert(PlanEntry(date = date, itemType = itemType, itemId = itemId, sortOrder = index))
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
        Frequency.WEEKDAYS -> day.dayOfWeek !in setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)
        Frequency.WEEKLY -> day.dayOfWeek.value == (chore.dueDayOfWeek ?: java.time.DayOfWeek.MONDAY.value)
        Frequency.TWICE_WEEKLY ->
            day.dayOfWeek.value == (chore.dueDayOfWeek ?: java.time.DayOfWeek.MONDAY.value) ||
                day.dayOfWeek.value == (chore.dueDayOfWeek2 ?: java.time.DayOfWeek.THURSDAY.value)
        Frequency.CUSTOM -> {
            val interval = chore.customIntervalDays
            val createdDay = java.time.Instant.ofEpochMilli(chore.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val daysSince = ChronoUnit.DAYS.between(createdDay, day)
            interval != null && interval > 0 && daysSince >= 0 && daysSince % interval == 0L
        }
    }

    /**
     * Walks backward from [date] (bounded by [MAX_OVERDUE_LOOKBACK_DAYS]) to find the chore's last
     * scheduled due date. Always treats the chore's creation day as a valid due date even if its
     * cycle wouldn't otherwise land there (e.g. a Weekly chore created on a Thursday is only "due"
     * on Mondays by [isDueOnRaw]) — otherwise a freshly added chore can have no due date at all
     * until its first real cycle date arrives, making it invisible anywhere in the app right after
     * being added.
     */
    private fun lastScheduledDateOnOrBefore(chore: Chore, date: LocalDate): LocalDate? {
        val createdDay = java.time.Instant.ofEpochMilli(chore.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        var day = date
        repeat(MAX_OVERDUE_LOOKBACK_DAYS + 1) {
            if (day.isBefore(createdDay)) return null
            if (isDueOnRaw(chore, day) || day == createdDay) return day
            day = day.minusDays(1)
        }
        return null
    }

    suspend fun addChore(
        title: String,
        frequency: Frequency,
        customIntervalDays: Int?,
        assigneeId: String,
        priority: Priority = Priority.NORMAL,
        startTime: String? = null,
        estimatedEndTime: String? = null,
        notes: String? = null,
        dueDayOfWeek: Int? = null,
        dueDayOfWeek2: Int? = null,
    ) {
        val chore = Chore(
            title = title,
            frequency = frequency,
            customIntervalDays = customIntervalDays,
            dueDayOfWeek = dueDayOfWeek,
            dueDayOfWeek2 = dueDayOfWeek2,
            assigneeId = assigneeId,
            priority = priority,
            startTime = startTime,
            estimatedEndTime = estimatedEndTime,
            notes = notes,
        )
        db.choreDao().insert(chore)
        choreSync.push(chore)
    }

    suspend fun updateChore(
        choreId: String,
        title: String,
        frequency: Frequency,
        customIntervalDays: Int?,
        assigneeId: String,
        priority: Priority = Priority.NORMAL,
        startTime: String? = null,
        estimatedEndTime: String? = null,
        notes: String? = null,
        dueDayOfWeek: Int? = null,
        dueDayOfWeek2: Int? = null,
    ) {
        val existing = db.choreDao().findById(choreId) ?: return
        val updated = existing.copy(
            title = title,
            frequency = frequency,
            customIntervalDays = customIntervalDays,
            dueDayOfWeek = dueDayOfWeek,
            dueDayOfWeek2 = dueDayOfWeek2,
            assigneeId = assigneeId,
            priority = priority,
            startTime = startTime,
            estimatedEndTime = estimatedEndTime,
            notes = notes,
        )
        db.choreDao().update(updated)
        choreSync.push(updated)
    }

    suspend fun setChoreCompleted(
        choreId: String,
        date: String,
        completed: Boolean,
        photoUri: String?,
        completedByAssigneeId: String? = null,
    ) {
        val dao = db.choreDao()
        val existing = dao.findOccurrence(choreId, date)
        val occurrence = (existing ?: ChoreOccurrence(id = "$choreId|$date", choreId = choreId, dueDate = date)).copy(
            completed = completed,
            completedAt = if (completed) System.currentTimeMillis() else null,
            completedPhotoUri = photoUri ?: existing?.completedPhotoUri,
            completedByAssigneeId = if (completed) completedByAssigneeId else null,
        )
        if (existing != null) dao.updateOccurrence(occurrence) else dao.insertOccurrence(occurrence)
        choreOccurrenceSync.push(occurrence)
    }

    suspend fun retireChore(choreId: String) {
        db.choreDao().deactivate(choreId)
        db.choreDao().findById(choreId)?.let { choreSync.push(it) }
    }

    suspend fun deleteChore(choreId: String) {
        db.choreDao().delete(choreId)
        choreSync.delete(choreId)
    }

    fun observeChorePhotos(choreId: String): Flow<List<ChorePhoto>> = db.chorePhotoDao().observeForChore(choreId)

    suspend fun addChorePhoto(choreId: String, uri: String) {
        val photo = ChorePhoto(choreId = choreId, uri = uri)
        db.chorePhotoDao().insert(photo)
        chorePhotoSync.push(photo)
    }

    suspend fun deleteChorePhoto(photoId: String) {
        db.chorePhotoDao().delete(photoId)
        chorePhotoSync.delete(photoId)
    }

    // --- Chore subtasks ---
    fun observeSubtasks(choreId: String, date: String): Flow<List<SubtaskWithChecks>> =
        combine(db.choreSubtaskDao().observeForChore(choreId), db.choreSubtaskDao().observeChecksForChoreAndDate(choreId, date)) { subtasks, checks ->
            val checksBySubtask = checks.groupBy { it.subtaskId }
            subtasks.map { subtask ->
                SubtaskWithChecks(subtask = subtask, checkedByAssigneeIds = checksBySubtask[subtask.id].orEmpty().map { it.assigneeId }.toSet())
            }
        }

    suspend fun addSubtask(choreId: String, title: String) {
        val subtask = ChoreSubtask(choreId = choreId, title = title)
        db.choreSubtaskDao().insert(subtask)
        choreSubtaskSync.push(subtask)
    }

    suspend fun deleteSubtask(subtaskId: String) {
        db.choreSubtaskDao().delete(subtaskId)
        choreSubtaskSync.delete(subtaskId)
    }

    suspend fun setSubtaskChecked(subtaskId: String, assigneeId: String, date: String, checked: Boolean) {
        val dao = db.choreSubtaskDao()
        val id = "$subtaskId|$assigneeId|$date"
        if (checked) {
            val check = ChoreSubtaskCheck(id = id, subtaskId = subtaskId, assigneeId = assigneeId, date = date)
            dao.insertCheck(check)
            choreSubtaskCheckSync.push(check)
        } else if (dao.findCheck(subtaskId, assigneeId, date) != null) {
            dao.deleteCheck(id)
            choreSubtaskCheckSync.delete(id)
        }
    }

    // --- Family activities ---
    fun observeFamilyActivities(): Flow<List<FamilyActivity>> = db.familyActivityDao().observeActive()
    fun observeFamilyActivityLogs(date: String): Flow<List<FamilyActivityLog>> = db.familyActivityDao().observeLogsForDate(date)

    suspend fun setFamilyActivityDone(activityId: String, date: String, done: Boolean) {
        val log = FamilyActivityLog(id = "$activityId|$date", activityId = activityId, date = date, done = done)
        db.familyActivityDao().upsertLog(log)
        familyActivityLogSync.push(log)
    }

    suspend fun addFamilyActivity(title: String, category: ActivityCategory, slot: ActivitySlot, quickOption: Boolean = false, notes: String? = null) {
        val activity = FamilyActivity(title = title, category = category, slot = slot, quickOption = quickOption, notes = notes)
        db.familyActivityDao().insert(activity)
        familyActivitySync.push(activity)
    }

    suspend fun updateFamilyActivity(id: String, title: String, category: ActivityCategory, slot: ActivitySlot, quickOption: Boolean = false, notes: String? = null) {
        val activity = FamilyActivity(id = id, title = title, category = category, slot = slot, quickOption = quickOption, notes = notes)
        db.familyActivityDao().update(activity)
        familyActivitySync.push(activity)
    }

    suspend fun deleteFamilyActivity(id: String) {
        db.familyActivityDao().delete(id)
        familyActivitySync.delete(id)
    }

    fun observeActivityIdeas(activityId: String): Flow<List<ActivityIdea>> = db.activityIdeaDao().observeForActivity(activityId)
    suspend fun addActivityIdea(activityId: String, text: String) {
        val idea = ActivityIdea(activityId = activityId, text = text)
        db.activityIdeaDao().insert(idea)
        activityIdeaSync.push(idea)
    }

    suspend fun deleteActivityIdea(id: String) {
        db.activityIdeaDao().delete(id)
        activityIdeaSync.delete(id)
    }

    // --- Parental activities ---
    fun observeParentalActivities(): Flow<List<ParentalActivity>> = db.parentalActivityDao().observeActive()
    fun observeParentalActivityLogs(isoWeek: String): Flow<List<ParentalActivityLog>> = db.parentalActivityDao().observeLogsForWeek(isoWeek)

    suspend fun setParentalActivityDone(activityId: String, isoWeek: String, done: Boolean) {
        val log = ParentalActivityLog(id = "$activityId|$isoWeek", activityId = activityId, isoWeek = isoWeek, done = done)
        db.parentalActivityDao().upsertLog(log)
        parentalActivityLogSync.push(log)
    }

    suspend fun addParentalActivity(
        title: String,
        audience: ParentalAudience,
        budget: BudgetTier,
        isSpicy: Boolean = false,
        notes: String? = null,
        scheduledDate: String? = null,
    ) {
        val activity = ParentalActivity(title = title, audience = audience, budget = budget, isSpicy = isSpicy, notes = notes, scheduledDate = scheduledDate)
        db.parentalActivityDao().insert(activity)
        parentalActivitySync.push(activity)
    }

    suspend fun updateParentalActivity(
        id: String,
        title: String,
        audience: ParentalAudience,
        budget: BudgetTier,
        isSpicy: Boolean = false,
        notes: String? = null,
        scheduledDate: String? = null,
    ) {
        val activity = ParentalActivity(id = id, title = title, audience = audience, budget = budget, isSpicy = isSpicy, notes = notes, scheduledDate = scheduledDate)
        db.parentalActivityDao().update(activity)
        parentalActivitySync.push(activity)
    }

    suspend fun updateChoreNotes(chore: Chore, notes: String?) = db.choreDao().update(chore.copy(notes = notes))
    suspend fun updateFamilyActivityNotes(activity: FamilyActivity, notes: String?) = db.familyActivityDao().update(activity.copy(notes = notes))

    suspend fun updateParentalActivityNotes(activity: ParentalActivity, notes: String?) {
        val updated = activity.copy(notes = notes)
        db.parentalActivityDao().update(updated)
        parentalActivitySync.push(updated)
    }

    suspend fun updateParentalActivitySchedule(activity: ParentalActivity, scheduledDate: String?) {
        val updated = activity.copy(scheduledDate = scheduledDate)
        db.parentalActivityDao().update(updated)
        parentalActivitySync.push(updated)
    }

    suspend fun deleteParentalActivity(id: String) {
        db.parentalActivityDao().delete(id)
        parentalActivitySync.delete(id)
    }

    // --- Scheduled activities (Scheduled tab) ---

    /** Any active parental/family activity with a scheduledDate set, regardless of audience, sorted soonest first. */
    fun observeScheduledActivities(): Flow<List<ScheduledActivity>> =
        combine(db.parentalActivityDao().observeActive(), db.parentalActivityDao().observeAllLogs()) { activities, logs ->
            activities.filter { it.scheduledDate != null }.map { activity ->
                val week = DateUtils.isoWeek(LocalDate.parse(activity.scheduledDate!!))
                val done = logs.any { it.activityId == activity.id && it.isoWeek == week && it.done }
                ScheduledActivity(activity = activity, done = done, isoWeek = week)
            }.sortedBy { it.activity.scheduledDate }
        }

    /** Marks a scheduled activity done/undone for the ISO week its own scheduledDate falls in — not
     *  necessarily the current week, since a past or future scheduled item may be viewed anytime. */
    suspend fun setScheduledActivityDone(scheduled: ScheduledActivity, done: Boolean) =
        setParentalActivityDone(scheduled.activity.id, scheduled.isoWeek, done)

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

    /**
     * Pushes every row currently in Room to Firestore under this device's current household code,
     * regardless of whether it's been pushed before. Joining a household only starts a *listener*
     * on that household's path (see AssigneeSync's doc comment) — it doesn't retroactively send
     * this device's existing data anywhere, so data created before a join (or before this device
     * had a household code at all) is otherwise invisible to anyone else under that code until
     * this runs once. Safe to call repeatedly: every push is a plain upsert keyed by the row's own
     * id, so re-sending unchanged rows is a no-op overwrite, not a duplicate.
     */
    suspend fun resyncAll() {
        db.assigneeDao().listAll().forEach { assigneeSync.push(it) }
        db.choreDao().listAll().forEach { choreSync.push(it) }
        db.choreDao().listAllOccurrences().forEach { choreOccurrenceSync.push(it) }
        db.choreSubtaskDao().listAll().forEach { choreSubtaskSync.push(it) }
        db.choreSubtaskDao().listAllChecks().forEach { choreSubtaskCheckSync.push(it) }
        db.familyActivityDao().listAll().forEach { familyActivitySync.push(it) }
        db.familyActivityDao().listAllLogs().forEach { familyActivityLogSync.push(it) }
        db.activityIdeaDao().listAll().forEach { activityIdeaSync.push(it) }
        db.parentalActivityDao().listAll().forEach { parentalActivitySync.push(it) }
        db.parentalActivityDao().listAllLogs().forEach { parentalActivityLogSync.push(it) }
        db.chorePhotoDao().listAll().forEach { chorePhotoSync.push(it) }
    }
}
