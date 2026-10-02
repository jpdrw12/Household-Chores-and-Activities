package com.jpdrw.household.data

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jpdrw.household.data.dao.ParentalActivityDao
import com.jpdrw.household.data.entity.BudgetTier
import com.jpdrw.household.data.entity.ParentalActivity
import com.jpdrw.household.data.entity.ParentalAudience
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "ParentalActivitySync"

/**
 * Fourth entity rolled onto the Firestore sync pattern (see AssigneeSync/ChoreSync/
 * FamilyActivitySync for the first three) — same shape: Room stays the source of truth for local
 * reads, Firestore is a sync transport, writes are fire-and-forget. Syncs the activity itself
 * only, not parental_activity_logs (weekly completion state stays local-only, same as how
 * ChoreSync/FamilyActivitySync leave their own log/occurrence tables local-only).
 */
class ParentalActivitySync(private val activityDao: ParentalActivityDao, householdId: String) {
    private val collection = FirebaseFirestore.getInstance().collection("households/$householdId/parental_activities")
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
                        activityDao.delete(id)
                        continue
                    }
                    val doc = change.document
                    val title = doc.getString("title") ?: continue
                    val audience = doc.getString("audience")?.let { runCatching { ParentalAudience.valueOf(it) }.getOrNull() } ?: continue
                    val budget = doc.getString("budget")?.let { runCatching { BudgetTier.valueOf(it) }.getOrNull() } ?: continue
                    Log.d(TAG, "upserting $id -> $title")
                    activityDao.insert(
                        ParentalActivity(
                            id = id,
                            title = title,
                            audience = audience,
                            budget = budget,
                            isSpicy = doc.getBoolean("isSpicy") ?: false,
                            notes = doc.getString("notes"),
                            scheduledDate = doc.getString("scheduledDate"),
                            active = doc.getBoolean("active") ?: true,
                        ),
                    )
                }
            }
        }
    }

    fun push(activity: ParentalActivity) {
        Log.d(TAG, "push() ${activity.id} -> ${activity.title}")
        val fields = mapOf(
            "title" to activity.title,
            "audience" to activity.audience.name,
            "budget" to activity.budget.name,
            "isSpicy" to activity.isSpicy,
            "notes" to activity.notes,
            "scheduledDate" to activity.scheduledDate,
            "active" to activity.active,
        )
        collection.document(activity.id).set(fields)
            .addOnSuccessListener { Log.d(TAG, "push success ${activity.id}") }
            .addOnFailureListener { Log.e(TAG, "push failed ${activity.id}", it) }
    }

    fun delete(id: String) {
        collection.document(id).delete()
    }
}
