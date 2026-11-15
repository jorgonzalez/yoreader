package com.yoreader.app.fetch

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yoreader.app.YoReaderApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** Refreshes one feed (input [KEY_FEED_ID]) or, without input, every feed. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val refresher = (applicationContext as YoReaderApp).refresher
        val feedId = inputData.getLong(KEY_FEED_ID, NO_FEED)
        // Per-feed failures are stored on the feed and shown in the UI, so the
        // work itself succeeds; retrying a dead feed every few minutes helps nobody.
        if (feedId == NO_FEED) refresher.refreshAll() else refresher.refresh(feedId)
        return Result.success()
    }

    companion object {
        const val KEY_FEED_ID = "feedId"
        private const val NO_FEED = -1L
    }
}

/** Schedules refresh work and reports whether any is running. */
object RefreshScheduler {

    private const val TAG = "refresh"
    private const val PERIODIC = "refresh-periodic"
    private const val ALL_NOW = "refresh-all-now"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Background refresh of every feed about once an hour, when online and battery isn't low. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** User-requested refresh of everything; ignored if one is already queued or running. */
    fun refreshAllNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraint)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ALL_NOW, ExistingWorkPolicy.KEEP, request)
    }

    /** Fetch a single feed right away, e.g. just after it was added. */
    fun refreshFeed(context: Context, feedId: Long) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraint)
            .setInputData(workDataOf(RefreshWorker.KEY_FEED_ID to feedId))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("refresh-feed-$feedId", ExistingWorkPolicy.KEEP, request)
    }

    /** True while any refresh (manual, single feed or periodic) is actually running. */
    fun isRefreshing(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context).getWorkInfosByTagFlow(TAG)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
            .distinctUntilChanged()
}
