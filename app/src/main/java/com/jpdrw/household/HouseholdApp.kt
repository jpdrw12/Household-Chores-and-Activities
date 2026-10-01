package com.jpdrw.household

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.jpdrw.household.data.AppDatabase
import com.jpdrw.household.data.Repository
import com.jpdrw.household.data.AppPrefs
import com.jpdrw.household.data.seedIfEmpty
import com.jpdrw.household.reminders.ChoreReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val REMINDER_WORK_NAME = "chore_reminder_daily"
private val REMINDER_TIME: LocalTime = LocalTime.of(18, 0)

class HouseholdApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())
    val database by lazy { AppDatabase.get(this) }
    val repository by lazy { Repository(database) }
    val appPrefs by lazy { AppPrefs(this) }

    override fun onCreate() {
        super.onCreate()
        ChoreReminderWorker.ensureChannel(this)
        scheduleDailyReminder()
        applicationScope.launch { database.seedIfEmpty() }
        signInAndStartAssigneeSync()
    }

    /** Anonymous auth is enough for the proof-of-concept sync — it only needs *a* signed-in user
     *  for Firestore's default security rules, not a real identity yet (see AssigneeSync.kt for
     *  why that's a known limitation, not an oversight). Failure here (offline, no
     *  google-services.json, Firebase unreachable) is swallowed: the app is local-first, so it
     *  must keep working against Room with sync simply not running until this succeeds. */
    private fun signInAndStartAssigneeSync() {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            repository.startAssigneeSync(applicationScope)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { repository.startAssigneeSync(applicationScope) }
            .addOnFailureListener { /* sync stays off; local Room usage is unaffected */ }
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
