package com.jpdrw.household.data

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jpdrw.household.data.dao.ParentalActivityDao
import com.jpdrw.household.data.entity.ParentalActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "ParentalActivityLogSync"

/** Syncs per-ISO-week parental-activity completion state. Deterministic id ("activityId|isoWeek"
 *  — see [ParentalActivityLog.id]'s doc comment), same reasoning as ChoreOccurrenceSync. */
class ParentalActivityLogSync(private val activityDao: ParentalActivityDao, householdId: String) {
    private val collection = FirebaseFirestore.getInstance().collection("households/$householdId/parental_activity_logs")
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
                        activityDao.deleteLog(id)
                        continue
                    }
                    val doc = change.document
                    val activityId = doc.getString("activityId") ?: continue
                    val isoWeek = doc.getString("isoWeek") ?: continue
                    Log.d(TAG, "upserting $id")
                    try {
                        activityDao.upsertLog(
                            ParentalActivityLog(id = id, activityId = activityId, isoWeek = isoWeek, done = doc.getBoolean("done") ?: false),
                        )
                    } catch (e: SQLiteConstraintException) {
                        Log.w(TAG, "skipping $id — activity $activityId not synced locally yet", e)
                    }
                }
            }
        }
    }

    fun push(log: ParentalActivityLog) {
        Log.d(TAG, "push() ${log.id}")
        val fields = mapOf("activityId" to log.activityId, "isoWeek" to log.isoWeek, "done" to log.done)
        collection.document(log.id).set(fields)
            .addOnSuccessListener { Log.d(TAG, "push success ${log.id}") }
            .addOnFailureListener { Log.e(TAG, "push failed ${log.id}", it) }
    }
}
