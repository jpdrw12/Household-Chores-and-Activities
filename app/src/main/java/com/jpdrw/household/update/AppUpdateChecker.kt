package com.jpdrw.household.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "AppUpdateChecker"
private const val RELEASES_API_URL =
    "https://api.github.com/repos/jpdrw12/Household-Chores-and-Activities/releases/latest"
private const val APK_ASSET_SUFFIX = ".apk"
private const val DOWNLOAD_FILE_NAME = "update.apk"

data class UpdateInfo(val tagName: String, val downloadUrl: String, val releaseUrl: String)

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Self-updater for a sideloaded, non-Play-Store app — there's no Play In-App Update API available
 * here, so this does the same job by hand: poll the GitHub Releases API for a newer tag than the
 * running `versionName`, download the matching APK asset via [DownloadManager] (handles the
 * background download + system notification for free), then hand the downloaded file to the
 * system installer via an ACTION_VIEW intent, which still requires the user's explicit
 * confirmation — this only triggers that prompt, it can never install silently.
 *
 * Two bugs worth remembering from building this once before (see CHANGELOG/git history): do the
 * network/download work off the main thread (everything here is `suspend`, called from a
 * coroutine), and don't try to auto-relaunch the app post-install — just let the system installer
 * finish its own flow and leave it at that.
 */
object AppUpdateChecker {

    suspend fun checkForUpdate(currentVersionName: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
                // GitHub's API 403s anonymous requests with no User-Agent header.
                setRequestProperty("User-Agent", "household-tracker-app")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = connection.responseCode
            if (code != 200) {
                val message = if (code == 404) {
                    "Couldn't reach the update server. Check your connection and try again."
                } else {
                    "Update check failed (HTTP $code). Try again later."
                }
                return@withContext UpdateCheckResult.Error(message)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.getString("tag_name")
            val latestVersion = tagName.removePrefix("v")
            if (!isNewer(latestVersion, currentVersionName)) {
                return@withContext UpdateCheckResult.UpToDate
            }
            val assets = json.getJSONArray("assets")
            var downloadUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(APK_ASSET_SUFFIX)) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }
            if (downloadUrl == null) {
                return@withContext UpdateCheckResult.Error("Release $tagName has no .apk attached")
            }
            UpdateCheckResult.Available(UpdateInfo(tagName, downloadUrl, json.getString("html_url")))
        } catch (e: Exception) {
            Log.e(TAG, "update check failed", e)
            UpdateCheckResult.Error(e.message ?: "Unknown error")
        }
    }

    /** Simple major.minor.patch comparison — good enough for this project's own tagging scheme
     *  (bump-version.sh always produces exactly three numeric parts). */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lp = l.getOrElse(i) { 0 }
            val cp = c.getOrElse(i) { 0 }
            if (lp != cp) return lp > cp
        }
        return false
    }

    fun startDownload(context: Context, info: UpdateInfo): Long {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("Household Tracker ${info.tagName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, DOWNLOAD_FILE_NAME)
        return manager.enqueue(request)
    }

    /** Polls [DownloadManager] for [downloadId]'s status rather than registering a
     *  BroadcastReceiver — simpler to scope to a single Compose call site, and this isn't
     *  latency-sensitive enough to need push notification of completion. */
    suspend fun awaitDownload(context: Context, downloadId: Long): Boolean = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        while (true) {
            val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
            cursor.use {
                if (it.moveToFirst()) {
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> return@withContext true
                        DownloadManager.STATUS_FAILED -> return@withContext false
                    }
                }
            }
            delay(500)
        }
        @Suppress("UNREACHABLE_CODE") false
    }

    /** Hands the downloaded APK to the system installer. Still requires the user to confirm the
     *  install in the system UI that opens — this can't and doesn't skip that. */
    fun promptInstall(context: Context) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_FILE_NAME)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
