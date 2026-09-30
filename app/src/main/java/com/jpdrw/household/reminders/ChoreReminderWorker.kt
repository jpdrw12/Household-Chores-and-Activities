package com.jpdrw.household.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jpdrw.household.HouseholdApp
import com.jpdrw.household.R
import com.jpdrw.household.data.DateUtils

const val CHORE_REMINDER_CHANNEL_ID = "chore_reminders"
private const val NOTIFICATION_ID = 1001

/** Runs once a day; if any chores due today are still unchecked, posts a single reminder notification. */
class ChoreReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as HouseholdApp).repository
        val incompleteTitles = repository.incompleteChoreTitlesForDate(DateUtils.today())
        if (incompleteTitles.isNotEmpty()) {
            postNotification(incompleteTitles)
        }
        return Result.success()
    }

    private fun postNotification(titles: List<String>) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val summary = if (titles.size == 1) titles.first() else "${titles.size} chores still need doing today"
        val notification = NotificationCompat.Builder(context, CHORE_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Chores waiting on you")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")))
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHORE_REMINDER_CHANNEL_ID,
                    "Chore reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Reminds you about chores still due today" }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }
    }
}
