package com.jpdrw.household.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

private const val MAX_DIMENSION = 1024
private const val TARGET_RAW_BYTES = 400_000
private const val MIN_QUALITY = 30

/**
 * Downscales and compresses a photo so it fits as a base64 string inside a single Firestore
 * document — the 1 MiB-per-document cap, combined with base64's ~1.33x inflation, means the raw
 * JPEG needs to stay well under that. This is what lets reference photos sync through Firestore
 * directly instead of needing a Storage bucket (see ChorePhoto's doc comment for why Storage was
 * ruled out). Verified against a real 1.4MB/1932x2576 phone photo: compresses to ~120KB (160KB
 * base64) at these settings with no visible quality loss for a "what done should look like"
 * reference photo.
 *
 * Applies the original's EXIF orientation before compressing — [BitmapFactory] ignores it, so
 * skipping this would make photos taken in portrait on some cameras come out sideways once synced
 * to another device (the original local file is untouched either way, since this only affects the
 * copy that gets uploaded).
 */
suspend fun compressPhotoToBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: return@withContext null
    val rotated = applyExifRotation(context, uri, bitmap)

    val scale = MAX_DIMENSION.toFloat() / maxOf(rotated.width, rotated.height)
    val resized = if (scale < 1f) {
        Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true)
    } else {
        rotated
    }

    var quality = 85
    var bytes: ByteArray
    do {
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        quality -= 5
    } while (bytes.size > TARGET_RAW_BYTES && quality >= MIN_QUALITY)

    Base64.encodeToString(bytes, Base64.NO_WRAP)
}

private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val degrees = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)
    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * Writes a synced-in photo's decoded bytes to the same local directory/FileProvider scheme as a
 * locally-captured one (see createChorePhotoUri in ChoresScreen.kt) — from there, the rest of the
 * app can't tell a synced-in photo apart from one taken on this device.
 */
fun writeDecodedPhoto(context: Context, photoId: String, base64: String): String {
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    val dir = File(context.filesDir, "task_photos").apply { mkdirs() }
    val file = File(dir, "synced_$photoId.jpg")
    file.writeBytes(bytes)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
}
