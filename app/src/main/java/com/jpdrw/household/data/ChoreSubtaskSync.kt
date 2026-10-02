package com.jpdrw.household.data

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jpdrw.household.data.dao.ChoreSubtaskDao
import com.jpdrw.household.data.entity.ChoreSubtask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "ChoreSubtaskSync"
private const val SUBTASKS_PATH = "households/default-household/chore_subtasks"

/** Syncs chore subtask definitions (e.g. "Shirts" under "Put clothes away") — same shape as
 *  ActivityIdeaSync. Does NOT sync chore_subtask_checks (per-day, per-assignee completion state —
 *  see ChoreSubtaskCheckSync.kt for that). */
class ChoreSubtaskSync(private val subtaskDao: ChoreSubtaskDao) {
    private val collection by lazy { FirebaseFirestore.getInstance().collection(SUBTASKS_PATH) }
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
                        subtaskDao.delete(id)
                        continue
                    }
                    val doc = change.document
                    val choreId = doc.getString("choreId") ?: continue
                    val title = doc.getString("title") ?: continue
                    Log.d(TAG, "upserting $id -> $title")
                    try {
                        subtaskDao.insert(
                            ChoreSubtask(
                                id = id,
                                choreId = choreId,
                                title = title,
                                sortOrder = (doc.getLong("sortOrder") ?: 0).toInt(),
                            ),
                        )
                    } catch (e: SQLiteConstraintException) {
                        Log.w(TAG, "skipping $id — chore $choreId not synced locally yet", e)
                    }
                }
            }
        }
    }

    fun push(subtask: ChoreSubtask) {
        Log.d(TAG, "push() ${subtask.id} -> ${subtask.title}")
        val fields = mapOf("choreId" to subtask.choreId, "title" to subtask.title, "sortOrder" to subtask.sortOrder)
        collection.document(subtask.id).set(fields)
            .addOnSuccessListener { Log.d(TAG, "push success ${subtask.id}") }
            .addOnFailureListener { Log.e(TAG, "push failed ${subtask.id}", it) }
    }

    fun delete(id: String) {
        collection.document(id).delete()
    }
}
