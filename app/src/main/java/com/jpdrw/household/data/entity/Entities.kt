package com.jpdrw.household.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

/** A family member or group who can be assigned a chore. "Family" is seeded as the default.
 *
 *  Id is a client-generated UUID string, not an autoincrement Long, so a new assignee created
 *  offline on one device can't collide with one created offline on another device once both sync
 *  to the same Firestore collection — see Repository's Firestore sync functions. Every other
 *  entity still uses Room's own autoincrement Long id; this is the one entity being used as the
 *  cross-device sync proof of concept. */
@Entity(tableName = "assignees")
data class Assignee(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isDefault: Boolean = false,
)

enum class Frequency { DAILY, WEEKLY, TWICE_WEEKLY, CUSTOM }
enum class Priority { LOW, NORMAL, HIGH, CRITICAL }

/** A chore template, e.g. "Sweep" or "Clean bathroom".
 *
 *  Id is a client-generated UUID string, same reasoning as [Assignee.id] — this entity syncs to
 *  Firestore too (see ChoreSync.kt), and a Long autoincrement id would let two devices adding a
 *  chore offline collide once both synced. */
@Entity(tableName = "chores")
data class Chore(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val frequency: Frequency,
    /** Only meaningful when [frequency] is [Frequency.CUSTOM]: due every N days, counted from [createdAt]. */
    val customIntervalDays: Int? = null,
    /** ISO day-of-week (1=Monday..7=Sunday) this is due on, when [frequency] is [Frequency.WEEKLY]
     *  or [Frequency.TWICE_WEEKLY] (first of its two days). Null falls back to Monday — only
     *  happens for chores created before this field existed. */
    val dueDayOfWeek: Int? = null,
    /** Second due day, only meaningful for [Frequency.TWICE_WEEKLY]. Null falls back to Thursday. */
    val dueDayOfWeek2: Int? = null,
    val assigneeId: String,
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

/**
 * A reference photo attached to a chore showing what "done" should look like. A chore can have
 * several. [uri] is a local content:// uri (FileProvider, for ones captured here, or a persisted
 * gallery-picker uri) that wouldn't resolve on another device as-is — synced via ChorePhotoSync,
 * which embeds a downscaled, compressed copy directly in its Firestore document rather than
 * uploading to Firebase Storage. A Storage-backed version of this was built and verified working
 * once, but Storage now requires the paid Blaze plan (a billing account) just to create a bucket at
 * all, even for usage that stays within its free tier — out of step with this project's "stays
 * free" goal, so that version was reverted. See CHANGELOG.
 *
 * Id is a client-generated UUID string, same reasoning as [Chore.id] — lets every device agree on
 * the same Firestore document for the same photo without a round-trip.
 */
@Entity(
    tableName = "chore_photos",
    foreignKeys = [
        ForeignKey(entity = Chore::class, parentColumns = ["id"], childColumns = ["choreId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChorePhoto(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val choreId: String,
    val uri: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** A sub-step of a chore, e.g. "Put away shirts" under "Put clothes away". Each is checked off
 *  per-assignee, per-day.
 *
 *  Id is a client-generated UUID string, same reasoning as [Chore.id] — syncs to Firestore too
 *  (see ChoreSubtaskSync.kt). */
@Entity(
    tableName = "chore_subtasks",
    foreignKeys = [
        ForeignKey(entity = Chore::class, parentColumns = ["id"], childColumns = ["choreId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChoreSubtask(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val choreId: String,
    val title: String,
    val sortOrder: Int = 0,
)

/** Records that a specific assignee checked off a specific subtask on a specific day. Row presence
 *  = checked.
 *
 *  Id is deterministic ("subtaskId|assigneeId|date"), not random — this is a presence-only
 *  table (existence = checked), so a device re-checking the same box offline just re-writes the
 *  same Firestore doc instead of creating a duplicate once both sync. See
 *  ChoreSubtaskCheckSync.kt. */
@Entity(
    tableName = "chore_subtask_checks",
    foreignKeys = [
        ForeignKey(entity = ChoreSubtask::class, parentColumns = ["id"], childColumns = ["subtaskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Assignee::class, parentColumns = ["id"], childColumns = ["assigneeId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChoreSubtaskCheck(
    @PrimaryKey val id: String,
    val subtaskId: String,
    val assigneeId: String,
    val date: String, // ISO yyyy-MM-dd
    val checkedAt: Long = System.currentTimeMillis(),
)

/** One occurrence of a chore due on a specific date, with completion state.
 *
 *  Id is deterministic ("choreId|dueDate"), not random — at most one occurrence row can
 *  meaningfully exist per chore per date, so a deterministic id makes the Firestore sync a clean
 *  upsert instead of needing conflict resolution between two devices completing the same chore
 *  offline on the same day. See ChoreOccurrenceSync.kt. [completedPhotoUri] is deliberately NOT
 *  synced — it's a local file:// URI that wouldn't resolve on another device without a real
 *  photo-upload pipeline (Firebase Storage), which is out of scope for this sync pass. */
@Entity(
    tableName = "chore_occurrences",
    foreignKeys = [
        ForeignKey(entity = Chore::class, parentColumns = ["id"], childColumns = ["choreId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChoreOccurrence(
    @PrimaryKey val id: String,
    val choreId: String,
    val dueDate: String, // ISO yyyy-MM-dd
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val completedPhotoUri: String? = null,
    /** Who actually checked this off — asked at check time since a chore assigned to "Family"
     *  could be done by anyone. Falls back to the chore's own assigneeId when null (e.g. rows
     *  completed before this field existed). */
    val completedByAssigneeId: String? = null,
)

enum class PlanItemType { CHORE, FAMILY_ACTIVITY, PARENTAL_ACTIVITY }

/** One task (chore, family activity, or "For Us" activity) placed into a given day's roadmap, in
 *  order. Lets a day's available tasks be strung together into a sequence on the Mapper tab,
 *  independent of completion state. No FK here since [itemId] points at a different table
 *  depending on [itemType].
 *
 *  itemId is a String, matching every item type's own id (Chore/FamilyActivity/ParentalActivity
 *  all moved from autoincrement Long to a UUID string for Firestore sync). */
@Entity(tableName = "plan_entries")
data class PlanEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // ISO yyyy-MM-dd
    val itemType: PlanItemType,
    val itemId: String,
    val sortOrder: Int,
)

enum class ActivityCategory { INDOOR, OUTDOOR }
enum class ActivitySlot { START_UP, MID_PLAY, WIND_DOWN, BEDTIME }

/** A kid/family-friendly activity suggestion.
 *
 *  Id is a client-generated UUID string, same reasoning as [Assignee.id]/[Chore.id] — this entity
 *  syncs to Firestore too (see FamilyActivitySync.kt). */
@Entity(tableName = "family_activities")
data class FamilyActivity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: ActivityCategory,
    val slot: ActivitySlot,
    /** Fits a tight pre-school window. Gates which START_UP suggestions show on weekday mornings. */
    val quickOption: Boolean = false,
    val notes: String? = null,
    val active: Boolean = true,
)

/** A suggested idea for an open-ended creative activity, e.g. "Build a castle" under
 *  "Building blocks / Lego". Browsing inspiration, not a per-day checklist like chore subtasks.
 *
 *  Id is a client-generated UUID string, same reasoning as [FamilyActivity.id] — syncs to
 *  Firestore too (see ActivityIdeaSync.kt). */
@Entity(
    tableName = "activity_ideas",
    foreignKeys = [
        ForeignKey(entity = FamilyActivity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ActivityIdea(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val activityId: String,
    val text: String,
    val sortOrder: Int = 0,
)

/** Tracks whether a given family activity was done on a given day.
 *
 *  Id is deterministic ("activityId|date") — same reasoning as [ChoreOccurrence.id]. See
 *  FamilyActivityLogSync.kt. */
@Entity(
    tableName = "family_activity_logs",
    foreignKeys = [
        ForeignKey(entity = FamilyActivity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class FamilyActivityLog(
    @PrimaryKey val id: String,
    val activityId: String,
    val date: String, // ISO yyyy-MM-dd
    val done: Boolean = false,
)

enum class ParentalAudience { PERSONAL, TOGETHER, ADULT_ONLY, FAMILY }
enum class BudgetTier { LOW, MEDIUM, HIGH }

/** A weekly parental/couple activity suggestion.
 *
 *  Id is a client-generated UUID string, same reasoning as [Assignee.id]/[Chore.id]/
 *  [FamilyActivity.id] — this entity syncs to Firestore too (see ParentalActivitySync.kt). */
@Entity(tableName = "parental_activities")
data class ParentalActivity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
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

/** Tracks whether a parental activity was done in a given ISO week (yyyy-'W'ww).
 *
 *  Id is deterministic ("activityId|isoWeek") — same reasoning as [ChoreOccurrence.id]. See
 *  ParentalActivityLogSync.kt. */
@Entity(
    tableName = "parental_activity_logs",
    foreignKeys = [
        ForeignKey(entity = ParentalActivity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ParentalActivityLog(
    @PrimaryKey val id: String,
    val activityId: String,
    val isoWeek: String,
    val done: Boolean = false,
)
