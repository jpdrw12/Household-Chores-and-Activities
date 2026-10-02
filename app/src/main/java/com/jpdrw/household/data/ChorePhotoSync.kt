package com.jpdrw.household.data

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.jpdrw.household.data.dao.ChorePhotoDao
import com.jpdrw.household.data.entity.ChorePhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "ChorePhotoSync"
private const val PHOTOS_PATH = "households/default-household/chore_photos"
private const val STORAGE_PATH = "chore_photos"

/**
 * Unlike every other synced entity, this one has a real binary payload — the photo itself — not
 * just structured fields, so it needs two pieces working together: Firebase Storage holds the
 * actual image bytes, Firestore holds the metadata ([ChorePhoto.choreId]/[ChorePhoto.remoteUrl]/
 * addedAt) the same way every other XxxSync does. [upload] does the Storage half and returns the
 * resulting download URL (or null on failure); Repository.uploadChorePhoto does the Firestore half
 * via [push], same as any other entity.
 *
 * An incoming photo whose chore hasn't synced locally yet hits the same choreId foreign-key gap
 * as ChoreOccurrence/ChoreSubtask/ActivityIdea — caught and skipped, not crashed (see
 * ChoreOccurrenceSync's doc comment for the full reasoning).
 */
class ChorePhotoSync(private val photoDao: ChorePhotoDao) {
    private val collection by lazy { FirebaseFirestore.getInstance().collection(PHOTOS_PATH) }
    private val storage by lazy { FirebaseStorage.getInstance().reference.child(STORAGE_PATH) }
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
                        photoDao.delete(id)
                        continue
                    }
                    val doc = change.document
                    val choreId = doc.getString("choreId") ?: continue
                    val remoteUrl = doc.getString("remoteUrl") ?: continue
                    Log.d(TAG, "upserting $id")
                    try {
                        photoDao.insert(
                            ChorePhoto(
                                id = id,
                                choreId = choreId,
                                uri = remoteUrl,
                                remoteUrl = remoteUrl,
                                addedAt = doc.getLong("addedAt") ?: System.currentTimeMillis(),
                            ),
                        )
                    } catch (e: SQLiteConstraintException) {
                        Log.w(TAG, "skipping $id — chore $choreId not synced locally yet", e)
                    }
                }
            }
        }
    }

    /** Uploads [bytes] to Firebase Storage and returns the download URL, or null on failure
     *  (offline, Storage not enabled in the Firebase console, etc.) — callers treat that as "stays
     *  local-only for now", not an error to surface to the user. */
    suspend fun upload(photoId: String, choreId: String, bytes: ByteArray): String? {
        val ref = storage.child(choreId).child("$photoId.jpg")
        val uploaded = suspendCancellableCoroutine { cont ->
            ref.putBytes(bytes)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener {
                    Log.e(TAG, "upload failed $photoId", it)
                    cont.resume(false)
                }
        }
        if (!uploaded) return null
        return suspendCancellableCoroutine { cont ->
            ref.downloadUrl
                .addOnSuccessListener { cont.resume(it.toString()) }
                .addOnFailureListener {
                    Log.e(TAG, "downloadUrl failed $photoId", it)
                    cont.resume(null)
                }
        }
    }

    fun push(photo: ChorePhoto) {
        Log.d(TAG, "push() ${photo.id}")
        val fields = mapOf("choreId" to photo.choreId, "remoteUrl" to photo.remoteUrl, "addedAt" to photo.addedAt)
        collection.document(photo.id).set(fields)
            .addOnSuccessListener { Log.d(TAG, "push success ${photo.id}") }
            .addOnFailureListener { Log.e(TAG, "push failed ${photo.id}", it) }
    }

    fun delete(id: String, choreId: String) {
        collection.document(id).delete()
        storage.child(choreId).child("$id.jpg").delete()
            .addOnFailureListener { Log.w(TAG, "storage delete failed $id (may never have finished uploading)", it) }
    }
}
