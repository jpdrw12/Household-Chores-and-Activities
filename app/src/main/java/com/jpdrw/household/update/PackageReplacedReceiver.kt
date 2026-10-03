package com.jpdrw.household.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.jpdrw.household.MainActivity
import com.jpdrw.household.R

const val UPDATE_CHANNEL_ID = "app_updates"
private const val NOTIFICATION_ID = 1002

/**
 * Fires when Android finishes replacing this app's APK — whether from our own [AppUpdateChecker]
 * or a manual sideload. The app's process is normally killed as part of that replacement, so
 * there's no way to hook "install just finished" from inside the Activity that triggered it (the
 * system installer runs out-of-process, after the user confirms it, possibly well after this
 * process is gone). A notification is the reliable way to prompt a fresh launch — tapping it
 * starts [MainActivity] as a new task, guaranteeing the new code is what loads, instead of relying
 * on whoever updated to know they need to force-stop rather than just switching back to it.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Household Tracker updated")
            .setContentText("Tap to open the new version")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "App updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Lets you know when an update finished installing" }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }
    }
}
