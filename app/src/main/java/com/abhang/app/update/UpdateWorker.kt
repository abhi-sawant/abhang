package com.abhang.app.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Background update check: downloads a newer release, and installs it while the app is closed. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Updater.restore(applicationContext)
        if (inputData.getBoolean(KEY_INSTALL_ONLY, false)) {
            Updater.installIfIdle(applicationContext)
            return Result.success()
        }
        val ok = Updater.check(applicationContext, manual = false)
        if (!ok) return if (runAttemptCount < 3) Result.retry() else Result.success()
        Updater.installIfIdle(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "update-check"
        private const val ON_START = "update-check-now"
        private const val INSTALL = "update-install"
        private const val KEY_INSTALL_ONLY = "install_only"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)   // many users only have mobile data; the APK is a few MB
            .setRequiresBatteryNotLow(true)
            .build()

        /**
         * Called when the app leaves the screen with an update ready. The download usually finishes
         * while the app is open (the start-up check), so without this the install would wait for the
         * next 12-hour check. The delay lets a quick return to the app cancel it (installIfIdle checks again).
         */
        fun installSoon(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                INSTALL, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<UpdateWorker>()
                    .setInitialDelay(30, TimeUnit.SECONDS)
                    .setInputData(workDataOf(KEY_INSTALL_ONLY to true))
                    .build(),
            )
        }

        /** Called on every app start: a check twice a day, plus one now. */
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .build(),
            )
            wm.enqueueUniqueWork(
                ON_START, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .build(),
            )
        }
    }
}
