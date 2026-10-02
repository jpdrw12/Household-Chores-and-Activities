package com.jpdrw.household.data

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jpdrw.household.data.dao.FamilyActivityDao
import com.jpdrw.household.data.entity.ActivityCategory
import com.jpdrw.household.data.entity.ActivitySlot
import com.jpdrw.household.data.entity.FamilyActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "FamilyActivitySync"

/**
 * Third entity rolled onto the Firestore sync pattern (see AssigneeSync/ChoreSync for the first
 * two) — same shape: Room stays the source of truth for local reads, Firestore is a sync
 * transport, writes are fire-and-forget. Syncs the activity itself only, not activity_ideas or
 * family_activity_logs (same as how ChoreSync leaves occurrences/photos/subtasks local-only).
 */
class FamilyActivitySync(private val activityDao: FamilyActivityDao, householdId: String) {
    private val collection = FirebaseFirestore.getInstance().collection("households/$householdId/family_activities")
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
                    val category = doc.getString("category")?.let { runCatching { ActivityCategory.valueOf(it) }.getOrNull() } ?: continue
                    val slot = doc.getString("slot")?.let { runCatching { ActivitySlot.valueOf(it) }.getOrNull() } ?: continue
                    Log.d(TAG, "upserting $id -> $title")
                    activityDao.insert(
                        FamilyActivity(
                            id = id,
                            title = title,
                            category = category,
                            slot = slot,
                            quickOption = doc.getBoolean("quickOption") ?: false,
                            notes = doc.getString("notes"),
                            active = doc.getBoolean("active") ?: true,
                        ),
                    )
                }
            }
        }
    }

    fun push(activity: FamilyActivity) {
        Log.d(TAG, "push() ${activity.id} -> ${activity.title}")
        val fields = mapOf(
            "title" to activity.title,
            "category" to activity.category.name,
            "slot" to activity.slot.name,
            "quickOption" to activity.quickOption,
            "notes" to activity.notes,
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
