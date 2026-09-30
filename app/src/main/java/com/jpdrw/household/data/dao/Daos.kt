package com.jpdrw.household.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jpdrw.household.data.entity.Assignee
import com.jpdrw.household.data.entity.Chore
import com.jpdrw.household.data.entity.ChoreOccurrence
import com.jpdrw.household.data.entity.FamilyActivity
import com.jpdrw.household.data.entity.FamilyActivityLog
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.data.entity.ParentalActivityLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AssigneeDao {
    @Query("SELECT * FROM assignees ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<Assignee>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(assignee: Assignee): Long

    @Delete
    suspend fun delete(assignee: Assignee)
}

@Dao
interface ChoreDao {
    @Query("SELECT * FROM chores WHERE active = 1 ORDER BY title ASC")
    fun observeActive(): Flow<List<Chore>>

    @Insert
    suspend fun insert(chore: Chore): Long

    @Update
    suspend fun update(chore: Chore)

    @Query("UPDATE chores SET active = 0 WHERE id = :choreId")
    suspend fun deactivate(choreId: Long)

    @Insert
    suspend fun insertOccurrence(occurrence: ChoreOccurrence): Long

    @Update
    suspend fun updateOccurrence(occurrence: ChoreOccurrence)

    @Query("SELECT * FROM chore_occurrences WHERE dueDate = :date ORDER BY id ASC")
    fun observeOccurrencesForDate(date: String): Flow<List<ChoreOccurrence>>

    @Query("SELECT * FROM chore_occurrences WHERE choreId = :choreId AND dueDate = :date LIMIT 1")
    suspend fun findOccurrence(choreId: Long, date: String): ChoreOccurrence?

    @Query("SELECT COUNT(*) FROM chore_occurrences WHERE dueDate BETWEEN :start AND :end AND completed = 1")
    suspend fun completedCountBetween(start: String, end: String): Int

    @Query("SELECT COUNT(*) FROM chore_occurrences WHERE dueDate BETWEEN :start AND :end")
    suspend fun totalCountBetween(start: String, end: String): Int
}

@Dao
interface FamilyActivityDao {
    @Query("SELECT * FROM family_activities WHERE active = 1 ORDER BY title ASC")
    fun observeActive(): Flow<List<FamilyActivity>>

    @Insert
    suspend fun insert(activity: FamilyActivity): Long

    @Query("SELECT * FROM family_activity_logs WHERE date = :date")
    fun observeLogsForDate(date: String): Flow<List<FamilyActivityLog>>

    @Query("SELECT * FROM family_activity_logs WHERE activityId = :activityId AND date = :date LIMIT 1")
    suspend fun findLog(activityId: Long, date: String): FamilyActivityLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: FamilyActivityLog)

    @Query("SELECT COUNT(*) FROM family_activity_logs WHERE date BETWEEN :start AND :end AND done = 1")
    suspend fun doneCountBetween(start: String, end: String): Int
}

@Dao
interface ParentalActivityDao {
    @Query("SELECT * FROM parental_activities WHERE active = 1 ORDER BY title ASC")
    fun observeActive(): Flow<List<ParentalActivity>>

    @Insert
    suspend fun insert(activity: ParentalActivity): Long

    @Query("SELECT * FROM parental_activity_logs WHERE isoWeek = :isoWeek")
    fun observeLogsForWeek(isoWeek: String): Flow<List<ParentalActivityLog>>

    @Query("SELECT * FROM parental_activity_logs WHERE activityId = :activityId AND isoWeek = :isoWeek LIMIT 1")
    suspend fun findLog(activityId: Long, isoWeek: String): ParentalActivityLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: ParentalActivityLog)

    @Query("SELECT COUNT(*) FROM parental_activity_logs WHERE isoWeek = :isoWeek AND done = 1")
    suspend fun doneCountForWeek(isoWeek: String): Int
}
