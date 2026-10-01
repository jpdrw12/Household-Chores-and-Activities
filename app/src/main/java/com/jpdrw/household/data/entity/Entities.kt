package com.jpdrw.household.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** A family member or group who can be assigned a chore. "Family" is seeded as the default. */
@Entity(tableName = "assignees")
data class Assignee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false,
)

enum class Frequency { DAILY, WEEKLY, TWICE_WEEKLY, CUSTOM }
enum class Priority { LOW, NORMAL, HIGH, CRITICAL }

/** A chore template, e.g. "Sweep" or "Clean bathroom". */
@Entity(tableName = "chores")
data class Chore(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val frequency: Frequency,
    /** Only meaningful when [frequency] is [Frequency.CUSTOM]: due every N days, counted from [createdAt]. */
    val customIntervalDays: Int? = null,
    val assigneeId: Long,
    val priority: Priority = Priority.NORMAL,
    /** Optional time-of-day window ("HH:mm", 24h). When [estimatedEndTime] has passed on the due
     *  date and the chore isn't done, it's marked overdue the same day instead of waiting for the
     *  date to roll over. */
    val startTime: String? = null,
    val estimatedEndTime: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A reference photo attached to a chore showing what "done" should look like. A chore can have several. */
@Entity(
    tableName = "chore_photos",
    foreignKeys = [
        ForeignKey(entity = Chore::class, parentColumns = ["id"], childColumns = ["choreId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChorePhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val choreId: Long,
    val uri: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** A sub-step of a chore, e.g. "Put away shirts" under "Put clothes away". Each is checked off per-assignee, per-day. */
@Entity(
    tableName = "chore_subtasks",
    foreignKeys = [
        ForeignKey(entity = Chore::class, parentColumns = ["id"], childColumns = ["choreId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChoreSubtask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val choreId: Long,
    val title: String,
    val sortOrder: Int = 0,
)

/** Records that a specific assignee checked off a specific subtask on a specific day. Row presence = checked. */
@Entity(
    tableName = "chore_subtask_checks",
    foreignKeys = [
        ForeignKey(entity = ChoreSubtask::class, parentColumns = ["id"], childColumns = ["subtaskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Assignee::class, parentColumns = ["id"], childColumns = ["assigneeId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChoreSubtaskCheck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subtaskId: Long,
    val assigneeId: Long,
    val date: String, // ISO yyyy-MM-dd
    val checkedAt: Long = System.currentTimeMillis(),
)

/** One occurrence of a chore due on a specific date, with completion state. */
@Entity(
    tableName = "chore_occurrences",
    foreignKeys = [
        ForeignKey(entity = Chore::class, parentColumns = ["id"], childColumns = ["choreId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChoreOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val choreId: Long,
    val dueDate: String, // ISO yyyy-MM-dd
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val completedPhotoUri: String? = null,
)

enum class PlanItemType { CHORE, FAMILY_ACTIVITY, PARENTAL_ACTIVITY }

/** One task (chore, family activity, or "For Us" activity) placed into a given day's roadmap, in
 *  order. Lets a day's available tasks be strung together into a sequence on the Mapper tab,
 *  independent of completion state. No FK here since [itemId] points at a different table
 *  depending on [itemType]. */
@Entity(tableName = "plan_entries")
data class PlanEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // ISO yyyy-MM-dd
    val itemType: PlanItemType,
    val itemId: Long,
    val sortOrder: Int,
)

enum class ActivityCategory { INDOOR, OUTDOOR }
enum class ActivitySlot { START_UP, MID_PLAY, WIND_DOWN, BEDTIME }

/** A kid/family-friendly activity suggestion. */
@Entity(tableName = "family_activities")
data class FamilyActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: ActivityCategory,
    val slot: ActivitySlot,
    /** Fits a tight pre-school window. Gates which START_UP suggestions show on weekday mornings. */
    val quickOption: Boolean = false,
    val notes: String? = null,
    val active: Boolean = true,
)

/** Tracks whether a given family activity was done on a given day. */
@Entity(
    tableName = "family_activity_logs",
    foreignKeys = [
        ForeignKey(entity = FamilyActivity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class FamilyActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val date: String, // ISO yyyy-MM-dd
    val done: Boolean = false,
)

enum class ParentalAudience { PERSONAL, TOGETHER, ADULT_ONLY }
enum class BudgetTier { LOW, MEDIUM, HIGH }

/** A weekly parental/couple activity suggestion. */
@Entity(tableName = "parental_activities")
data class ParentalActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val audience: ParentalAudience,
    val budget: BudgetTier,
    /** Intimate solo/together suggestion, hidden unless the admin "Show spicy activities" toggle is on. */
    val isSpicy: Boolean = false,
    val notes: String? = null,
    /** Optional date (ISO yyyy-MM-dd) this should happen by. Shown as active/critical that day,
     *  and overdue (if not done that week) after it passes. */
    val scheduledDate: String? = null,
    val active: Boolean = true,
)

/** Tracks whether a parental activity was done in a given ISO week (yyyy-'W'ww). */
@Entity(
    tableName = "parental_activity_logs",
    foreignKeys = [
        ForeignKey(entity = ParentalActivity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ParentalActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val isoWeek: String,
    val done: Boolean = false,
)
