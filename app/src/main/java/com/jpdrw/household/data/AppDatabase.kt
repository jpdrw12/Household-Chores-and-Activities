package com.jpdrw.household.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jpdrw.household.data.dao.AssigneeDao
import com.jpdrw.household.data.dao.ChoreDao
import com.jpdrw.household.data.dao.FamilyActivityDao
import com.jpdrw.household.data.dao.ParentalActivityDao
import com.jpdrw.household.data.entity.ActivityCategory
import com.jpdrw.household.data.entity.ActivitySlot
import com.jpdrw.household.data.entity.Assignee
import com.jpdrw.household.data.entity.BudgetTier
import com.jpdrw.household.data.entity.Chore
import com.jpdrw.household.data.entity.FamilyActivity
import com.jpdrw.household.data.entity.Frequency
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.data.entity.ParentalAudience
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(
    entities = [
        Assignee::class,
        Chore::class,
        ChoreOccurrence::class,
        FamilyActivity::class,
        com.jpdrw.household.data.entity.FamilyActivityLog::class,
        ParentalActivity::class,
        com.jpdrw.household.data.entity.ParentalActivityLog::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assigneeDao(): AssigneeDao
    abstract fun choreDao(): ChoreDao
    abstract fun familyActivityDao(): FamilyActivityDao
    abstract fun parentalActivityDao(): ParentalActivityDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context, scope: CoroutineScope): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "household.db",
                ).addCallback(SeedCallback(scope)).build().also { instance = it }
            }
    }
}

/** Seeds the starter chores, the default "Family" assignee, and starter activity suggestions on first run. */
private class SeedCallback(private val scope: CoroutineScope) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        scope.launch {
            val database = instance ?: return@launch
            seed(database)
        }
    }

    private suspend fun seed(db: AppDatabase) {
        val assigneeDao = db.assigneeDao()
        val familyId = assigneeDao.insert(Assignee(name = "Family", isDefault = true))

        val choreDao = db.choreDao()
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
        starterChores.forEach { (title, freq) ->
            choreDao.insert(Chore(title = title, frequency = freq, assigneeId = familyId))
        }

        val activityDao = db.familyActivityDao()
        val starterActivities = listOf(
            Triple("Morning stretch/dance party", ActivityCategory.INDOOR, ActivitySlot.START_UP),
            Triple("Walk around the block", ActivityCategory.OUTDOOR, ActivitySlot.START_UP),
            Triple("Board game", ActivityCategory.INDOOR, ActivitySlot.MID_PLAY),
            Triple("Backyard tag / ball games", ActivityCategory.OUTDOOR, ActivitySlot.MID_PLAY),
            Triple("Building blocks / Lego", ActivityCategory.INDOOR, ActivitySlot.MID_PLAY),
            Triple("Bike ride", ActivityCategory.OUTDOOR, ActivitySlot.MID_PLAY),
            Triple("Coloring/drawing", ActivityCategory.INDOOR, ActivitySlot.WIND_DOWN),
            Triple("Quiet backyard time", ActivityCategory.OUTDOOR, ActivitySlot.WIND_DOWN),
            Triple("Bath time", ActivityCategory.INDOOR, ActivitySlot.BEDTIME),
            Triple("Bedtime story", ActivityCategory.INDOOR, ActivitySlot.BEDTIME),
        )
        starterActivities.forEach { (title, cat, slot) ->
            activityDao.insert(FamilyActivity(title = title, category = cat, slot = slot))
        }

        val parentalDao = db.parentalActivityDao()
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
    }
}
