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

/** A chore template, e.g. "Sweep" or "Clean bathroom". */
@Entity(tableName = "chores")
data class Chore(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val frequency: Frequency,
    /** Only meaningful when [frequency] is [Frequency.CUSTOM]: due every N days, counted from [createdAt]. */
    val customIntervalDays: Int? = null,
    val assigneeId: Long,
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

enum class ActivityCategory { INDOOR, OUTDOOR }
enum class ActivitySlot { START_UP, MID_PLAY, WIND_DOWN, BEDTIME }

/** A kid/family-friendly activity suggestion. */
@Entity(tableName = "family_activities")
data class FamilyActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: ActivityCategory,
    val slot: ActivitySlot,
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
