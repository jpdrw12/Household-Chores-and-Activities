package com.jpdrw.household.data

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jpdrw.household.data.dao.ChoreSubtaskDao
import com.jpdrw.household.data.entity.ChoreSubtaskCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "ChoreSubtaskCheckSync"

/** Syncs per-assignee, per-day subtask check state. Presence-only (row existing = checked), same
 *  reasoning as [ChoreSubtaskCheck.id]'s doc comment — deterministic id, so a re-check offline on
 *  two devices just re-writes the same doc instead of racing. */
class ChoreSubtaskCheckSync(private val subtaskDao: ChoreSubtaskDao, householdId: String) {
    private val collection = FirebaseFirestore.getInstance().collection("households/$householdId/chore_subtask_checks")
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
                        subtaskDao.deleteCheck(id)
                        continue
                    }
                    val doc = change.document
                    val subtaskId = doc.getString("subtaskId") ?: continue
                    val assigneeId = doc.getString("assigneeId") ?: continue
                    val date = doc.getString("date") ?: continue
                    Log.d(TAG, "upserting $id")
                    try {
                        subtaskDao.insertCheck(
                            ChoreSubtaskCheck(
                                id = id,
                                subtaskId = subtaskId,
                                assigneeId = assigneeId,
                                date = date,
                                checkedAt = doc.getLong("checkedAt") ?: System.currentTimeMillis(),
                            ),
                        )
                    } catch (e: SQLiteConstraintException) {
                        Log.w(TAG, "skipping $id — subtask $subtaskId or assignee $assigneeId not synced locally yet", e)
                    }
                }
            }
        }
    }

    fun push(check: ChoreSubtaskCheck) {
        Log.d(TAG, "push() ${check.id}")
        val fields = mapOf(
            "subtaskId" to check.subtaskId,
            "assigneeId" to check.assigneeId,
            "date" to check.date,
            "checkedAt" to check.checkedAt,
        )
        collection.document(check.id).set(fields)
            .addOnSuccessListener { Log.d(TAG, "push success ${check.id}") }
            .addOnFailureListener { Log.e(TAG, "push failed ${check.id}", it) }
    }

    fun delete(id: String) {
        collection.document(id).delete()
    }
}
