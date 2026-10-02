package com.jpdrw.household.data

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jpdrw.household.data.dao.AssigneeDao
import com.jpdrw.household.data.entity.Assignee
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "AssigneeSync"

/**
 * Proof-of-concept cross-device sync for Assignee, the one entity being migrated first (see the
 * architecture discussion that led here). Firestore is purely a sync transport — Room stays the
 * single source of truth for every local read in Repository, so no screen needed to change beyond
 * the Long-to-String id swap. A Firestore write is fire-and-forget: if it fails (offline, Firebase
 * unreachable), Room already has the change locally and the retry happens naturally on the next
 * successful [push], so nothing is lost — it just doesn't sync until connectivity returns.
 *
 * [householdId] scopes every path under "households/$householdId/..." — see HouseholdId.kt for
 * where it comes from (a short per-install code, generated locally, shared by typing it into
 * another device). This is what actually separates one household's data from another's; it
 * replaced an earlier version of this class that hardcoded a single path every install shared,
 * which meant literally any install of the app could read and write everyone's data.
 */
class AssigneeSync(private val assigneeDao: AssigneeDao, householdId: String) {
    private val collection = FirebaseFirestore.getInstance().collection("households/$householdId/assignees")
    private var listener: ListenerRegistration? = null

    /** Starts a live listener that upserts/removes rows in Room's `assignees` table to match
     *  Firestore. Safe to call more than once — a second call is a no-op. */
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
                        assigneeDao.deleteById(id)
                    } else {
                        val name = change.document.getString("name") ?: continue
                        val isDefault = change.document.getBoolean("isDefault") ?: false
                        Log.d(TAG, "upserting $id -> $name")
                        assigneeDao.insert(Assignee(id = id, name = name, isDefault = isDefault))
                    }
                }
            }
        }
    }

    fun push(assignee: Assignee) {
        Log.d(TAG, "push() ${assignee.id} -> ${assignee.name}")
        collection.document(assignee.id).set(mapOf("name" to assignee.name, "isDefault" to assignee.isDefault))
            .addOnSuccessListener { Log.d(TAG, "push success ${assignee.id}") }
            .addOnFailureListener { Log.e(TAG, "push failed ${assignee.id}", it) }
    }

    fun delete(id: String) {
        collection.document(id).delete()
    }
}
