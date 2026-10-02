package com.jpdrw.household.data

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jpdrw.household.data.dao.ChoreDao
import com.jpdrw.household.data.entity.ChoreOccurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "ChoreOccurrenceSync"

/**
 * Syncs per-day chore completion state. Unlike the template entities (Assignee/Chore/...), this
 * uses a deterministic Firestore doc id ("choreId|dueDate" — see [ChoreOccurrence.id]'s doc
 * comment) instead of a random UUID, so two devices completing the same chore on the same day
 * offline merge into one row instead of racing to create duplicates. [ChoreOccurrence.
 * completedPhotoUri] is deliberately never synced — see that field's doc comment.
 *
 * An incoming occurrence whose chore hasn't synced locally yet (e.g. it references a seeded
 * chore, which — unlike user-added ones — is never pushed to Firestore) would otherwise violate
 * the choreId foreign key and crash the whole sync coroutine. Caught and skipped instead; it's
 * lost rather than retried, which is an accepted gap of this sync pass (see CHANGELOG).
 */
class ChoreOccurrenceSync(private val choreDao: ChoreDao, householdId: String) {
    private val collection = FirebaseFirestore.getInstance().collection("households/$householdId/chore_occurrences")
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
                        choreDao.deleteOccurrence(id)
                        continue
                    }
                    val doc = change.document
                    val choreId = doc.getString("choreId") ?: continue
                    val dueDate = doc.getString("dueDate") ?: continue
                    Log.d(TAG, "upserting $id")
                    try {
                        choreDao.insertOccurrence(
                            ChoreOccurrence(
                                id = id,
                                choreId = choreId,
                                dueDate = dueDate,
                                completed = doc.getBoolean("completed") ?: false,
                                completedAt = doc.getLong("completedAt"),
                                completedPhotoUri = null,
                                completedByAssigneeId = doc.getString("completedByAssigneeId"),
                            ),
                        )
                    } catch (e: SQLiteConstraintException) {
                        Log.w(TAG, "skipping $id — chore $choreId not synced locally yet", e)
                    }
                }
            }
        }
    }

    fun push(occurrence: ChoreOccurrence) {
        Log.d(TAG, "push() ${occurrence.id}")
        val fields = mapOf(
            "choreId" to occurrence.choreId,
            "dueDate" to occurrence.dueDate,
            "completed" to occurrence.completed,
            "completedAt" to occurrence.completedAt,
            "completedByAssigneeId" to occurrence.completedByAssigneeId,
        )
        collection.document(occurrence.id).set(fields)
            .addOnSuccessListener { Log.d(TAG, "push success ${occurrence.id}") }
            .addOnFailureListener { Log.e(TAG, "push failed ${occurrence.id}", it) }
    }
}
