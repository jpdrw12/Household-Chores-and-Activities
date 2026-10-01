package com.jpdrw.household.data

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jpdrw.household.data.dao.AssigneeDao
import com.jpdrw.household.data.entity.Assignee
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Every household currently shares this one fixed Firestore path — there's no auth/household-
 *  join flow yet, so this is NOT safe for multiple unrelated households using the app. It only
 *  exists to validate the sync mechanism end to end before building that out for every entity. */
private const val ASSIGNEES_PATH = "households/default-household/assignees"

/**
 * Proof-of-concept cross-device sync for Assignee, the one entity being migrated first (see the
 * architecture discussion that led here). Firestore is purely a sync transport — Room stays the
 * single source of truth for every local read in Repository, so no screen needed to change beyond
 * the Long-to-String id swap. A Firestore write is fire-and-forget: if it fails (offline, Firebase
 * unreachable), Room already has the change locally and the retry happens naturally on the next
 * successful [push], so nothing is lost — it just doesn't sync until connectivity returns.
 */
class AssigneeSync(private val assigneeDao: AssigneeDao) {
    private val collection by lazy { FirebaseFirestore.getInstance().collection(ASSIGNEES_PATH) }
    private var listener: ListenerRegistration? = null

    /** Starts a live listener that upserts/removes rows in Room's `assignees` table to match
     *  Firestore. Safe to call more than once — a second call is a no-op. */
    fun start(scope: CoroutineScope) {
        if (listener != null) return
        listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                for (change in snapshot.documentChanges) {
                    val id = change.document.id
                    if (change.type == DocumentChange.Type.REMOVED) {
                        assigneeDao.deleteById(id)
                    } else {
                        val name = change.document.getString("name") ?: continue
                        val isDefault = change.document.getBoolean("isDefault") ?: false
                        assigneeDao.insert(Assignee(id = id, name = name, isDefault = isDefault))
                    }
                }
            }
        }
    }

    fun push(assignee: Assignee) {
        collection.document(assignee.id).set(mapOf("name" to assignee.name, "isDefault" to assignee.isDefault))
    }

    fun delete(id: String) {
        collection.document(id).delete()
    }
}
