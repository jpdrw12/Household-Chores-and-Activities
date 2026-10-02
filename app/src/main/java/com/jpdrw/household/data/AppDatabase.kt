package com.jpdrw.household.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jpdrw.household.data.dao.ActivityIdeaDao
import com.jpdrw.household.data.dao.AssigneeDao
import com.jpdrw.household.data.dao.ChoreDao
import com.jpdrw.household.data.dao.ChorePhotoDao
import com.jpdrw.household.data.dao.ChoreSubtaskDao
import com.jpdrw.household.data.dao.FamilyActivityDao
import com.jpdrw.household.data.dao.ParentalActivityDao
import com.jpdrw.household.data.dao.PlanDao
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
import com.jpdrw.household.data.entity.Frequency
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.data.entity.ParentalAudience
import com.jpdrw.household.data.entity.PlanEntry
import kotlinx.coroutines.flow.first

@Database(
    entities = [
        Assignee::class,
        Chore::class,
        ChoreOccurrence::class,
        ChorePhoto::class,
        PlanEntry::class,
        ChoreSubtask::class,
        ChoreSubtaskCheck::class,
        FamilyActivity::class,
        com.jpdrw.household.data.entity.FamilyActivityLog::class,
        ActivityIdea::class,
        ParentalActivity::class,
        com.jpdrw.household.data.entity.ParentalActivityLog::class,
    ],
    // Pre-1.0: bump this on every entity/column change (paired with fallbackToDestructiveMigration
    // below). Room only takes the destructive-migration path when the version number itself
    // changes — leaving it the same while the schema drifts hits a hard identity-hash crash on
    // any device with an existing install, instead of a clean wipe-and-reseed.
    version = 15,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assigneeDao(): AssigneeDao
    abstract fun choreDao(): ChoreDao
    abstract fun chorePhotoDao(): ChorePhotoDao
    abstract fun planDao(): PlanDao
    abstract fun choreSubtaskDao(): ChoreSubtaskDao
    abstract fun familyActivityDao(): FamilyActivityDao
    abstract fun activityIdeaDao(): ActivityIdeaDao
    abstract fun parentalActivityDao(): ParentalActivityDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "household.db",
                )
                    // Pre-1.0: schema is still moving. Wipe on a mismatch instead of writing a
                    // migration for every in-development change; revisit once released. Note this
                    // only recreates tables — it does NOT reseed them (see seedIfEmpty below).
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}

/**
 * Seeds starter data, inserting only titles that don't already exist. Called from HouseholdApp on
 * every launch rather than from a RoomDatabase.Callback.onCreate — that callback does not reliably
 * fire after a destructive migration recreates tables (only guaranteed on a brand-new database
 * file), which once left the app with empty tables and no way to recover after a schema-version
 * bump. Checking per-title also means a starter list added in a later app update reaches existing
 * installs automatically, instead of only ever applying to a first-ever launch.
 */
suspend fun AppDatabase.seedIfEmpty() {
    val assigneeDao = assigneeDao()
    val familyId = assigneeDao.observeAll().first().firstOrNull { it.isDefault }?.id
        ?: Assignee(name = "Family", isDefault = true).also { assigneeDao.insert(it) }.id

    val choreDao = choreDao()
    val existingChoreTitles = choreDao.observeActive().first().map { it.title }.toSet()
    val starterChores = listOf(
        "Clean rooms/closets" to Frequency.WEEKLY,
        "Gather dirty laundry and sort it" to Frequency.WEEKLY,
        "Pick up after ourselves (toys, food, clothes, etc.)" to Frequency.DAILY,
        "Sweep" to Frequency.DAILY,
        "Mop" to Frequency.TWICE_WEEKLY,
        "Clean bathroom" to Frequency.WEEKLY,
        "Put clothes away" to Frequency.DAILY,
        "Clean family rooms" to Frequency.WEEKLY,
        "Clean counters" to Frequency.DAILY,
        "Clear tops of cupboards" to Frequency.WEEKLY,
        "Do dishes" to Frequency.DAILY,
        "Pack lunch bags" to Frequency.DAILY,
    )
    starterChores.filter { it.first !in existingChoreTitles }.forEach { (title, freq) ->
        choreDao.insert(Chore(title = title, frequency = freq, assigneeId = familyId))
    }

    // "Put clothes away" subtasks are per family member, not per clothing item, so everyone can
    // check off their own share. Replaces any old item-based subtasks from an earlier seed and
    // tops up anyone missing — covers both a fresh install and an existing one.
    val subtaskDao = choreSubtaskDao()
    val putClothesAwayChore = choreDao.observeActive().first().firstOrNull { it.title == "Put clothes away" }
    if (putClothesAwayChore != null) {
        val personNames = listOf("Mom", "Dad", "Ben", "Henry", "Aiden")
        val existingSubtasks = subtaskDao.observeForChore(putClothesAwayChore.id).first()
        existingSubtasks.filter { it.title !in personNames }.forEach { subtaskDao.delete(it.id) }
        val existingSubtaskTitles = existingSubtasks.map { it.title }.toSet()
        personNames.forEachIndexed { index, name ->
            if (name !in existingSubtaskTitles) {
                subtaskDao.insert(ChoreSubtask(choreId = putClothesAwayChore.id, title = name, sortOrder = index))
            }
        }
    }

    val activityDao = familyActivityDao()
    val existingActivityTitles = activityDao.observeActive().first().map { it.title }.toSet()
    // quickOption marks START_UP suggestions that fit a tight pre-school window; non-quick
    // START_UP options (a full walk, a bike ride) only surface on weekend/no-school mornings.
    data class StarterActivity(val title: String, val category: ActivityCategory, val slot: ActivitySlot, val quick: Boolean = false)
    val starterActivities = listOf(
        StarterActivity("Morning stretch/dance party", ActivityCategory.INDOOR, ActivitySlot.START_UP, quick = true),
        StarterActivity("Get-dressed race", ActivityCategory.INDOOR, ActivitySlot.START_UP, quick = true),
        StarterActivity("5-minute room tidy", ActivityCategory.INDOOR, ActivitySlot.START_UP, quick = true),
        StarterActivity("Make breakfast", ActivityCategory.INDOOR, ActivitySlot.START_UP, quick = true),
        StarterActivity("Walk around the block", ActivityCategory.OUTDOOR, ActivitySlot.START_UP),
        StarterActivity("Bike ride before school", ActivityCategory.OUTDOOR, ActivitySlot.START_UP),
        StarterActivity("Board game", ActivityCategory.INDOOR, ActivitySlot.MID_PLAY),
        StarterActivity("Backyard tag / ball games", ActivityCategory.OUTDOOR, ActivitySlot.MID_PLAY),
        StarterActivity("Building blocks / Lego", ActivityCategory.INDOOR, ActivitySlot.MID_PLAY),
        StarterActivity("Bike ride", ActivityCategory.OUTDOOR, ActivitySlot.MID_PLAY),
        StarterActivity("Coloring/drawing", ActivityCategory.INDOOR, ActivitySlot.WIND_DOWN),
        StarterActivity("Quiet backyard time", ActivityCategory.OUTDOOR, ActivitySlot.WIND_DOWN),
        StarterActivity("Bath time", ActivityCategory.INDOOR, ActivitySlot.BEDTIME),
        StarterActivity("Bedtime story", ActivityCategory.INDOOR, ActivitySlot.BEDTIME),
    )
    starterActivities.filter { it.title !in existingActivityTitles }.forEach { (title, cat, slot, quick) ->
        activityDao.insert(FamilyActivity(title = title, category = cat, slot = slot, quickOption = quick))
    }

    // Idea suggestions for open-ended creative activities — "what should we make?" prompts, not a
    // per-day checklist. Keyed by activity title so this tops up existing installs too, same as
    // the "Put clothes away" subtasks above.
    val ideaDao = activityIdeaDao()
    val starterIdeas = mapOf(
        "Building blocks / Lego" to listOf(
            "Build a castle", "Build a spaceship", "Build your dream house", "Build an animal",
            "Build a vehicle", "Build the tallest tower you can",
        ),
        "Coloring/drawing" to listOf(
            "Draw your favorite animal", "Draw a superhero", "Draw your family",
            "Draw a monster", "Draw what you want to be when you grow up", "Draw your favorite place",
        ),
        "Board game" to listOf(
            "Candy Land", "Chutes and Ladders", "Uno", "Memory match", "Checkers", "Jenga",
        ),
        "Backyard tag / ball games" to listOf(
            "Freeze tag", "Kickball", "Catch", "Hopscotch", "Obstacle course", "Hide and seek",
        ),
        "Bedtime story" to listOf(
            "Make up a story about a dragon", "Retell their favorite book in a silly voice",
            "An animal adventure", "A story about today, but turned into an adventure",
            "A superhero bedtime story", "Let them make up the story and you act it out",
        ),
        "Quiet backyard time" to listOf(
            "Cloud-watching", "Bug hunting", "Chalk drawing", "Leaf or rock collecting",
            "Blow bubbles", "Quiet picnic",
        ),
    )
    starterIdeas.forEach { (activityTitle, ideas) ->
        val activity = activityDao.observeActive().first().firstOrNull { it.title == activityTitle } ?: return@forEach
        val existingIdeaTexts = ideaDao.listForActivity(activity.id).map { it.text }.toSet()
        ideas.forEachIndexed { index, idea ->
            if (idea !in existingIdeaTexts) {
                ideaDao.insert(ActivityIdea(activityId = activity.id, text = idea, sortOrder = index))
            }
        }
    }

    val parentalDao = parentalActivityDao()
    val existingParentalTitles = parentalDao.observeActive().first().map { it.title }.toSet()
    val starterParental = listOf(
        Triple("Read a book / hobby time", ParentalAudience.PERSONAL, BudgetTier.LOW),
        Triple("Home workout", ParentalAudience.PERSONAL, BudgetTier.LOW),
        Triple("Solo coffee shop trip", ParentalAudience.PERSONAL, BudgetTier.MEDIUM),
        Triple("Massage / spa visit", ParentalAudience.PERSONAL, BudgetTier.HIGH),
        Triple("Movie night at home", ParentalAudience.TOGETHER, BudgetTier.LOW),
        Triple("Cook a new recipe together", ParentalAudience.TOGETHER, BudgetTier.MEDIUM),
        Triple("Dinner out", ParentalAudience.TOGETHER, BudgetTier.HIGH),
        Triple("Game night with friends", ParentalAudience.ADULT_ONLY, BudgetTier.LOW),
        Triple("Night out / concert", ParentalAudience.ADULT_ONLY, BudgetTier.MEDIUM),
        Triple("Weekend getaway", ParentalAudience.ADULT_ONLY, BudgetTier.HIGH),
        // Bigger planned outings the whole family (including kids) does together.
        Triple("Family camping trip", ParentalAudience.FAMILY, BudgetTier.MEDIUM),
        Triple("Family hike", ParentalAudience.FAMILY, BudgetTier.LOW),
        Triple("Backyard campout", ParentalAudience.FAMILY, BudgetTier.LOW),
        Triple("Visit the zoo", ParentalAudience.FAMILY, BudgetTier.MEDIUM),
        Triple("Beach day", ParentalAudience.FAMILY, BudgetTier.LOW),
        Triple("Road trip", ParentalAudience.FAMILY, BudgetTier.HIGH),
        Triple("Visit a museum", ParentalAudience.FAMILY, BudgetTier.MEDIUM),
        Triple("Amusement park", ParentalAudience.FAMILY, BudgetTier.HIGH),
    )
    starterParental.filter { it.first !in existingParentalTitles }.forEach { (title, audience, budget) ->
        parentalDao.insert(ParentalActivity(title = title, audience = audience, budget = budget))
    }

    // "Intimate" suggestions stay deliberately non-explicit — romantic/sensual framing rather than
    // graphic — hidden by default behind the admin toggle, and meant as a starting point the
    // couple edits/replaces with their own, more specific ideas.
    val starterIntimate = listOf(
        Triple("Self-care evening, no interruptions", ParentalAudience.PERSONAL, BudgetTier.LOW),
        Triple("Write down what you're craving from each other", ParentalAudience.PERSONAL, BudgetTier.LOW),
        Triple("Device-free date night in", ParentalAudience.TOGETHER, BudgetTier.LOW),
        Triple("Plan a surprise for each other", ParentalAudience.TOGETHER, BudgetTier.MEDIUM),
        Triple("Overnight away, just the two of you", ParentalAudience.TOGETHER, BudgetTier.HIGH),
        Triple("Give each other a massage", ParentalAudience.TOGETHER, BudgetTier.LOW),
        Triple("Slow dance in the kitchen", ParentalAudience.TOGETHER, BudgetTier.LOW),
        Triple("Take a bath together", ParentalAudience.TOGETHER, BudgetTier.LOW),
        Triple("Recreate your first date", ParentalAudience.TOGETHER, BudgetTier.MEDIUM),
        Triple("Try a new adult game together", ParentalAudience.TOGETHER, BudgetTier.MEDIUM),
        Triple("Write each other a love letter", ParentalAudience.PERSONAL, BudgetTier.LOW),
        Triple("Book a couples massage or spa night", ParentalAudience.TOGETHER, BudgetTier.HIGH),
    )
    starterIntimate.filter { it.first !in existingParentalTitles }.forEach { (title, audience, budget) ->
        parentalDao.insert(ParentalActivity(title = title, audience = audience, budget = budget, isSpicy = true))
    }
}
