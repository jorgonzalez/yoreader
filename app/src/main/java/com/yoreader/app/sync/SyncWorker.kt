package com.yoreader.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yoreader.app.YoReaderApp

/**
 * Laptop sync as background work, for when the app is left: Android may freeze an app
 * soon after it goes to the background, but not while its work is running, so the
 * sync file isn't left half written.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as YoReaderApp).laptopSync.sync()
        return Result.success()
    }

    companion object {
        /** Queued after a sync already waiting or running, so the newest changes go too. */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "laptop-sync",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().build(),
            )
        }
    }
}
