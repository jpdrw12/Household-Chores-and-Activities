package com.jpdrw.household.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jpdrw.household.data.dao.ChorePhotoDao
import com.jpdrw.household.data.entity.ChorePhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "ChorePhotoSync"

/**
 * Syncs chore reference photos by embedding a compressed, base64-encoded JPEG directly in each
 * Firestore document (see compressPhotoToBase64) instead of uploading to Firebase Storage — see
 * ChorePhoto's doc comment for why Storage was ruled out. [context] is needed (unlike the other
 * XxxSync classes) to decode/compress the local uri on push and to write a synced-in photo's bytes
 * to local storage on pull — see writeDecodedPhoto.
 */
class ChorePhotoSync(private val photoDao: ChorePhotoDao, private val context: Context, householdId: String) {
    private val collection = FirebaseFirestore.getInstance().collection("households/$householdId/chore_photos")
    private var listener: ListenerRegistration? = null

    fun start(scope: CoroutineScope) {
        if (listener != null) return
        listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                for (change in snapshot.documentChanges) {
                    val id = change.document.id
                    if (change.type == DocumentChange.Type.REMOVED) {
                        photoDao.delete(id)
                        continue
                    }
                    val choreId = change.document.getString("choreId") ?: continue
                    val data = change.document.getString("data") ?: continue
                    val addedAt = change.document.getLong("addedAt") ?: System.currentTimeMillis()
                    val uri = writeDecodedPhoto(context, id, data)
                    photoDao.insert(ChorePhoto(id = id, choreId = choreId, uri = uri, addedAt = addedAt))
                }
            }
        }
    }

    /** Compression happens inline here (unlike other XxxSync.push calls, this one is suspend) since
     *  it needs to read and downscale the local file before there's anything to upload. */
    suspend fun push(photo: ChorePhoto) {
        val base64 = compressPhotoToBase64(context, Uri.parse(photo.uri))
        if (base64 == null) {
            Log.e(TAG, "compression failed for ${photo.id}, not pushing")
            return
        }
        collection.document(photo.id)
            .set(mapOf("choreId" to photo.choreId, "data" to base64, "addedAt" to photo.addedAt))
            .addOnSuccessListener { Log.d(TAG, "push success ${photo.id}") }
            .addOnFailureListener { Log.e(TAG, "push failed ${photo.id}", it) }
    }

    fun delete(id: String) {
        collection.document(id).delete()
    }
}
