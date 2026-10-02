package com.jpdrw.household.data

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jpdrw.household.data.dao.ActivityIdeaDao
import com.jpdrw.household.data.entity.ActivityIdea
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "ActivityIdeaSync"

/** Syncs activity-idea suggestions (e.g. "Build a castle" under "Building blocks / Lego"). Same
 *  shape as ChoreSubtaskSync. */
class ActivityIdeaSync(private val ideaDao: ActivityIdeaDao, householdId: String) {
    private val collection = FirebaseFirestore.getInstance().collection("households/$householdId/activity_ideas")
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
                        ideaDao.delete(id)
                        continue
                    }
                    val doc = change.document
                    val activityId = doc.getString("activityId") ?: continue
                    val text = doc.getString("text") ?: continue
                    Log.d(TAG, "upserting $id -> $text")
                    try {
                        ideaDao.insert(
                            ActivityIdea(
                                id = id,
                                activityId = activityId,
                                text = text,
                                sortOrder = (doc.getLong("sortOrder") ?: 0).toInt(),
                            ),
                        )
                    } catch (e: SQLiteConstraintException) {
                        Log.w(TAG, "skipping $id — activity $activityId not synced locally yet", e)
                    }
                }
            }
        }
    }

    fun push(idea: ActivityIdea) {
        Log.d(TAG, "push() ${idea.id} -> ${idea.text}")
        val fields = mapOf("activityId" to idea.activityId, "text" to idea.text, "sortOrder" to idea.sortOrder)
        collection.document(idea.id).set(fields)
            .addOnSuccessListener { Log.d(TAG, "push success ${idea.id}") }
            .addOnFailureListener { Log.e(TAG, "push failed ${idea.id}", it) }
    }

    fun delete(id: String) {
        collection.document(id).delete()
    }
}
