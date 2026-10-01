package com.jpdrw.household.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jpdrw.household.data.dao.AssigneeDao
import com.jpdrw.household.data.dao.ChoreDao
import com.jpdrw.household.data.dao.ChorePhotoDao
import com.jpdrw.household.data.dao.ChoreSubtaskDao
import com.jpdrw.household.data.dao.FamilyActivityDao
import com.jpdrw.household.data.dao.ParentalActivityDao
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
import com.jpdrw.household.data.entity.Frequency
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.data.entity.ParentalAudience
import kotlinx.coroutines.flow.first

@Database(
    entities = [
        Assignee::class,
        Chore::class,
        ChoreOccurrence::class,
        ChorePhoto::class,
        ChoreSubtask::class,
        ChoreSubtaskCheck::class,
        FamilyActivity::class,
        com.jpdrw.household.data.entity.FamilyActivityLog::class,
        ParentalActivity::class,
        com.jpdrw.household.data.entity.ParentalActivityLog::class,
    ],
    // Pre-1.0: bump this on every entity/column change (paired with fallbackToDestructiveMigration
    // below). Room only takes the destructive-migration path when the version number itself
    // changes — leaving it the same while the schema drifts hits a hard identity-hash crash on
    // any device with an existing install, instead of a clean wipe-and-reseed.
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assigneeDao(): AssigneeDao
    abstract fun choreDao(): ChoreDao
    abstract fun chorePhotoDao(): ChorePhotoDao
    abstract fun choreSubtaskDao(): ChoreSubtaskDao
    abstract fun familyActivityDao(): FamilyActivityDao
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
 * Seeds starter data if the database is empty. Called from HouseholdApp on every launch rather
 * than from a RoomDatabase.Callback.onCreate — that callback does not reliably fire after a
 * destructive migration recreates tables (only guaranteed on a brand-new database file), which
 * left the app with empty tables and no way to recover after a schema-version bump. Checking
 * "is it empty" directly is slower by one query but self-heals regardless of how the tables came
 * to be empty.
 */
suspend fun AppDatabase.seedIfEmpty() {
    if (assigneeDao().observeAll().first().isNotEmpty()) return

    val assigneeDao = assigneeDao()
    val familyId = assigneeDao.insert(Assignee(name = "Family", isDefault = true))

    val choreDao = choreDao()
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
    )
    val subtaskDao = choreSubtaskDao()
    starterChores.forEach { (title, freq) ->
        val choreId = choreDao.insert(Chore(title = title, frequency = freq, assigneeId = familyId))
        if (title == "Put clothes away") {
            listOf("Shirts", "Pants", "Socks/underwear", "Outerwear").forEachIndexed { index, subtaskTitle ->
                subtaskDao.insert(ChoreSubtask(choreId = choreId, title = subtaskTitle, sortOrder = index))
            }
        }
    }

    val activityDao = familyActivityDao()
    // quickOption marks START_UP suggestions that fit a tight pre-school window; non-quick
    // START_UP options (a full walk, a bike ride) only surface on weekend/no-school mornings.
    data class StarterActivity(val title: String, val category: ActivityCategory, val slot: ActivitySlot, val quick: Boolean = false)
    val starterActivities = listOf(
        StarterActivity("Morning stretch/dance party", ActivityCategory.INDOOR, ActivitySlot.START_UP, quick = true),
        StarterActivity("Get-dressed race", ActivityCategory.INDOOR, ActivitySlot.START_UP, quick = true),
        StarterActivity("5-minute room tidy", ActivityCategory.INDOOR, ActivitySlot.START_UP, quick = true),
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
    starterActivities.forEach { (title, cat, slot, quick) ->
        activityDao.insert(FamilyActivity(title = title, category = cat, slot = slot, quickOption = quick))
    }

    val parentalDao = parentalActivityDao()
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
    )
    starterParental.forEach { (title, audience, budget) ->
        parentalDao.insert(ParentalActivity(title = title, audience = audience, budget = budget))
    }

    // "Intimate" suggestions stay deliberately mild placeholders — hidden by default behind the
    // admin toggle, and meant as a starting point the couple edits/replaces with their own ideas.
    val starterIntimate = listOf(
        Triple("Self-care evening, no interruptions", ParentalAudience.PERSONAL, BudgetTier.LOW),
        Triple("Write down what you're craving from each other", ParentalAudience.PERSONAL, BudgetTier.LOW),
        Triple("Device-free date night in", ParentalAudience.TOGETHER, BudgetTier.LOW),
        Triple("Plan a surprise for each other", ParentalAudience.TOGETHER, BudgetTier.MEDIUM),
        Triple("Overnight away, just the two of you", ParentalAudience.TOGETHER, BudgetTier.HIGH),
    )
    starterIntimate.forEach { (title, audience, budget) ->
        parentalDao.insert(ParentalActivity(title = title, audience = audience, budget = budget, isSpicy = true))
    }
}
