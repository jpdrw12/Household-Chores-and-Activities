package com.jpdrw.household

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jpdrw.household.data.AppDatabase
import com.jpdrw.household.data.Repository
import com.jpdrw.household.data.AppPrefs
import com.jpdrw.household.reminders.ChoreReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val REMINDER_WORK_NAME = "chore_reminder_daily"
private val REMINDER_TIME: LocalTime = LocalTime.of(18, 0)

class HouseholdApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())
    val database by lazy { AppDatabase.get(this, applicationScope) }
    val repository by lazy { Repository(database) }
    val appPrefs by lazy { AppPrefs(this) }

    override fun onCreate() {
        super.onCreate()
        ChoreReminderWorker.ensureChannel(this)
        scheduleDailyReminder()
    }

    private fun scheduleDailyReminder() {
        val now = LocalDateTime.now()
        var firstRun = now.toLocalDate().atTime(REMINDER_TIME)
        if (firstRun.isBefore(now)) firstRun = firstRun.plusDays(1)
        val initialDelay = Duration.between(now, firstRun)

        val request = PeriodicWorkRequestBuilder<ChoreReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
