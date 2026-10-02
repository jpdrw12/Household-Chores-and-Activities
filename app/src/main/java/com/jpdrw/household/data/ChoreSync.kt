package com.jpdrw.household.data

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jpdrw.household.data.dao.ChoreDao
import com.jpdrw.household.data.entity.Chore
import com.jpdrw.household.data.entity.Frequency
import com.jpdrw.household.data.entity.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "ChoreSync"

/**
 * Second entity rolled onto the Firestore sync pattern proven out by AssigneeSync — same shape:
 * Room stays the source of truth for local reads, Firestore is a sync transport, writes are
 * fire-and-forget (Room already has the change; a failed push just retries on the next write).
 *
 * This syncs the chore *template* only (title, schedule, assignment) — not chore_occurrences
 * (day-by-day completion state), chore_photos, or chore_subtasks. Those stay local-only for now;
 * syncing them is the same pattern again, just not done yet.
 */
class ChoreSync(private val choreDao: ChoreDao, householdId: String) {
    private val collection = FirebaseFirestore.getInstance().collection("households/$householdId/chores")
    private var listener: ListenerRegistration? = null

    fun start(scope: CoroutineScope) {
        Log.d(TAG, "start() called, listener already active = ${listener != null}")
        if (listener != null) return
        listener = collection.addSnapshotListener { snapshot, error ->
            Log.d(TAG, "snapshot listener fired: error=$error, docCount=${snapshot?.documentChanges?.size}")
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                for (change in snapshot.documentChanges) {
                    val id = change.document.id
                    if (change.type == DocumentChange.Type.REMOVED) {
                        choreDao.delete(id)
                        continue
                    }
                    val doc = change.document
                    val title = doc.getString("title") ?: continue
                    val assigneeId = doc.getString("assigneeId") ?: continue
                    val frequency = doc.getString("frequency")?.let { runCatching { Frequency.valueOf(it) }.getOrNull() } ?: continue
                    val priority = doc.getString("priority")?.let { runCatching { Priority.valueOf(it) }.getOrNull() } ?: Priority.NORMAL
                    Log.d(TAG, "upserting $id -> $title")
                    choreDao.insert(
                        Chore(
                            id = id,
                            title = title,
                            frequency = frequency,
                            customIntervalDays = (doc.getLong("customIntervalDays"))?.toInt(),
                            dueDayOfWeek = doc.getLong("dueDayOfWeek")?.toInt(),
                            dueDayOfWeek2 = doc.getLong("dueDayOfWeek2")?.toInt(),
                            assigneeId = assigneeId,
                            priority = priority,
                            startTime = doc.getString("startTime"),
                            estimatedEndTime = doc.getString("estimatedEndTime"),
                            notes = doc.getString("notes"),
                            active = doc.getBoolean("active") ?: true,
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    fun push(chore: Chore) {
        Log.d(TAG, "push() ${chore.id} -> ${chore.title}")
        val fields = mapOf(
            "title" to chore.title,
            "frequency" to chore.frequency.name,
            "customIntervalDays" to chore.customIntervalDays,
            "dueDayOfWeek" to chore.dueDayOfWeek,
            "dueDayOfWeek2" to chore.dueDayOfWeek2,
            "assigneeId" to chore.assigneeId,
            "priority" to chore.priority.name,
            "startTime" to chore.startTime,
            "estimatedEndTime" to chore.estimatedEndTime,
            "notes" to chore.notes,
            "active" to chore.active,
            "createdAt" to chore.createdAt,
        )
        collection.document(chore.id).set(fields)
            .addOnSuccessListener { Log.d(TAG, "push success ${chore.id}") }
            .addOnFailureListener { Log.e(TAG, "push failed ${chore.id}", it) }
    }

    fun delete(id: String) {
        collection.document(id).delete()
    }
}
