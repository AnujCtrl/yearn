package com.anujp.yearn.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class WorkSyncScheduler(
    context: Context,
    /** Serial, off the caller's thread; tests may pass a direct executor. */
    private val executor: Executor = defaultExecutor,
) : SyncScheduler {
    private val app = context.applicationContext
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun enqueueNow() {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .build()
        executor.execute {
            // An exception here would kill the app from the executor thread. Losing one enqueue is fine:
            // the next share, Sync now, a settings change or the daily job enqueues again.
            try {
                val wm = WorkManager.getInstance(app)
                // A running pass may already have read the pending list, so queue one more behind it. Anything
                // else (nothing, backing off, blocked, finished) is replaced so a fresh share never waits
                // behind a predecessor that is sleeping in backoff.
                val running = try {
                    wm.getWorkInfosForUniqueWork(NOW).get().any { it.state == WorkInfo.State.RUNNING }
                } catch (_: Exception) {
                    false
                }
                wm.enqueueUniqueWork(
                    NOW,
                    if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                    req,
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun ensureDaily() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofHours(24))
            .setConstraints(connected)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    companion object {
        const val NOW = "yearn-sync-now"
        const val DAILY = "yearn-sync-daily"
        private val defaultExecutor: Executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "yearn-sync-enqueue").apply { isDaemon = true }
        }
    }
}
