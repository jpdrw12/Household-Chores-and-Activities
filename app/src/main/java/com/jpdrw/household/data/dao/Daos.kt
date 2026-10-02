package com.jpdrw.household.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jpdrw.household.data.entity.ActivityIdea
import com.jpdrw.household.data.entity.Assignee
import com.jpdrw.household.data.entity.Chore
import com.jpdrw.household.data.entity.ChoreOccurrence
import com.jpdrw.household.data.entity.ChorePhoto
import com.jpdrw.household.data.entity.PlanEntry
import com.jpdrw.household.data.entity.PlanItemType
import com.jpdrw.household.data.entity.ChoreSubtask
import com.jpdrw.household.data.entity.ChoreSubtaskCheck
import com.jpdrw.household.data.entity.FamilyActivity
import com.jpdrw.household.data.entity.FamilyActivityLog
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.data.entity.ParentalActivityLog
import kotlinx.coroutines.flow.Flow

data class AssigneeCompletionCount(val assigneeId: String, val completed: Int)

@Dao
interface AssigneeDao {
    @Query("SELECT * FROM assignees ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<Assignee>>

    @Query("SELECT * FROM assignees WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Assignee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignee: Assignee)

    @Update
    suspend fun update(assignee: Assignee)

    @Delete
    suspend fun delete(assignee: Assignee)

    @Query("DELETE FROM assignees WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_entries WHERE date = :date ORDER BY sortOrder ASC")
    fun observeForDate(date: String): Flow<List<PlanEntry>>

    @Insert
    suspend fun insert(entry: PlanEntry): Long

    @Query("DELETE FROM plan_entries WHERE date = :date AND itemType = :itemType AND itemId = :itemId")
    suspend fun deleteEntry(date: String, itemType: PlanItemType, itemId: String)

    @Query("DELETE FROM plan_entries WHERE date = :date")
    suspend fun deleteAllForDate(date: String)

    @Query("SELECT MAX(sortOrder) FROM plan_entries WHERE date = :date")
    suspend fun maxSortOrder(date: String): Int?
}

@Dao
interface ChoreDao {
    @Query("SELECT * FROM chores WHERE active = 1 ORDER BY title ASC")
    fun observeActive(): Flow<List<Chore>>

    @Query("SELECT * FROM chores WHERE id = :choreId LIMIT 1")
    suspend fun findById(choreId: String): Chore?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chore: Chore)

    @Update
    suspend fun update(chore: Chore)

    @Query("UPDATE chores SET active = 0 WHERE id = :choreId")
    suspend fun deactivate(choreId: String)

    @Query("DELETE FROM chores WHERE id = :choreId")
    suspend fun delete(choreId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOccurrence(occurrence: ChoreOccurrence)

    @Update
    suspend fun updateOccurrence(occurrence: ChoreOccurrence)

    @Query("DELETE FROM chore_occurrences WHERE id = :id")
    suspend fun deleteOccurrence(id: String)

    @Query("SELECT * FROM chore_occurrences WHERE dueDate = :date ORDER BY id ASC")
    fun observeOccurrencesForDate(date: String): Flow<List<ChoreOccurrence>>

    @Query("SELECT * FROM chore_occurrences WHERE dueDate BETWEEN :start AND :end ORDER BY id ASC")
    fun observeOccurrencesBetween(start: String, end: String): Flow<List<ChoreOccurrence>>

    @Query("SELECT * FROM chore_occurrences WHERE dueDate = :date AND completed = 0 ORDER BY id ASC")
    suspend fun incompleteForDate(date: String): List<ChoreOccurrence>

    @Query("SELECT * FROM chore_occurrences WHERE choreId = :choreId AND dueDate = :date LIMIT 1")
    suspend fun findOccurrence(choreId: String, date: String): ChoreOccurrence?

    @Query("SELECT COUNT(*) FROM chore_occurrences WHERE dueDate BETWEEN :start AND :end AND completed = 1")
    suspend fun completedCountBetween(start: String, end: String): Int

    @Query("SELECT COUNT(*) FROM chore_occurrences WHERE dueDate BETWEEN :start AND :end")
    suspend fun totalCountBetween(start: String, end: String): Int

    @Query(
        """
        SELECT COALESCE(o.completedByAssigneeId, c.assigneeId) AS assigneeId, COUNT(*) AS completed
        FROM chore_occurrences o
        JOIN chores c ON c.id = o.choreId
        WHERE o.dueDate BETWEEN :start AND :end AND o.completed = 1
        GROUP BY COALESCE(o.completedByAssigneeId, c.assigneeId)
        """,
    )
    suspend fun completedCountByAssignee(start: String, end: String): List<AssigneeCompletionCount>
}

@Dao
interface ChorePhotoDao {
    @Query("SELECT * FROM chore_photos WHERE choreId = :choreId ORDER BY addedAt ASC")
    fun observeForChore(choreId: String): Flow<List<ChorePhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: ChorePhoto)

    @Update
    suspend fun update(photo: ChorePhoto)

    @Query("DELETE FROM chore_photos WHERE id = :photoId")
    suspend fun delete(photoId: String)
}

@Dao
interface ChoreSubtaskDao {
    @Query("SELECT * FROM chore_subtasks WHERE choreId = :choreId ORDER BY sortOrder ASC, id ASC")
    fun observeForChore(choreId: String): Flow<List<ChoreSubtask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subtask: ChoreSubtask)

    @Query("DELETE FROM chore_subtasks WHERE id = :subtaskId")
    suspend fun delete(subtaskId: String)

    @Query("SELECT * FROM chore_subtask_checks WHERE date = :date AND subtaskId IN (SELECT id FROM chore_subtasks WHERE choreId = :choreId)")
    fun observeChecksForChoreAndDate(choreId: String, date: String): Flow<List<ChoreSubtaskCheck>>

    @Query("SELECT * FROM chore_subtask_checks WHERE subtaskId = :subtaskId AND assigneeId = :assigneeId AND date = :date LIMIT 1")
    suspend fun findCheck(subtaskId: String, assigneeId: String, date: String): ChoreSubtaskCheck?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheck(check: ChoreSubtaskCheck)

    @Query("DELETE FROM chore_subtask_checks WHERE id = :checkId")
    suspend fun deleteCheck(checkId: String)
}

@Dao
interface FamilyActivityDao {
    @Query("SELECT * FROM family_activities WHERE active = 1 ORDER BY title ASC")
    fun observeActive(): Flow<List<FamilyActivity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: FamilyActivity)

    @Query("SELECT * FROM family_activities WHERE id = :activityId LIMIT 1")
    suspend fun findById(activityId: String): FamilyActivity?

    @Update
    suspend fun update(activity: FamilyActivity)

    @Query("DELETE FROM family_activities WHERE id = :activityId")
    suspend fun delete(activityId: String)

    @Query("SELECT * FROM family_activity_logs WHERE date = :date")
    fun observeLogsForDate(date: String): Flow<List<FamilyActivityLog>>

    @Query("SELECT * FROM family_activity_logs WHERE activityId = :activityId AND date = :date LIMIT 1")
    suspend fun findLog(activityId: String, date: String): FamilyActivityLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: FamilyActivityLog)

    @Query("DELETE FROM family_activity_logs WHERE id = :id")
    suspend fun deleteLog(id: String)

    @Query("SELECT COUNT(*) FROM family_activity_logs WHERE date BETWEEN :start AND :end AND done = 1")
    suspend fun doneCountBetween(start: String, end: String): Int
}

@Dao
interface ActivityIdeaDao {
    @Query("SELECT * FROM activity_ideas WHERE activityId = :activityId ORDER BY sortOrder ASC, id ASC")
    fun observeForActivity(activityId: String): Flow<List<ActivityIdea>>

    @Query("SELECT * FROM activity_ideas WHERE activityId = :activityId ORDER BY sortOrder ASC, id ASC")
    suspend fun listForActivity(activityId: String): List<ActivityIdea>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(idea: ActivityIdea)

    @Query("DELETE FROM activity_ideas WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ParentalActivityDao {
    @Query("SELECT * FROM parental_activities WHERE active = 1 ORDER BY title ASC")
    fun observeActive(): Flow<List<ParentalActivity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: ParentalActivity)

    @Query("SELECT * FROM parental_activities WHERE id = :activityId LIMIT 1")
    suspend fun findById(activityId: String): ParentalActivity?

    @Update
    suspend fun update(activity: ParentalActivity)

    @Query("DELETE FROM parental_activities WHERE id = :activityId")
    suspend fun delete(activityId: String)

    @Query("SELECT * FROM parental_activity_logs WHERE isoWeek = :isoWeek")
    fun observeLogsForWeek(isoWeek: String): Flow<List<ParentalActivityLog>>

    @Query("SELECT * FROM parental_activity_logs")
    fun observeAllLogs(): Flow<List<ParentalActivityLog>>

    @Query("SELECT * FROM parental_activity_logs WHERE activityId = :activityId AND isoWeek = :isoWeek LIMIT 1")
    suspend fun findLog(activityId: String, isoWeek: String): ParentalActivityLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: ParentalActivityLog)

    @Query("DELETE FROM parental_activity_logs WHERE id = :id")
    suspend fun deleteLog(id: String)

    @Query("SELECT COUNT(*) FROM parental_activity_logs WHERE isoWeek = :isoWeek AND done = 1")
    suspend fun doneCountForWeek(isoWeek: String): Int
}
