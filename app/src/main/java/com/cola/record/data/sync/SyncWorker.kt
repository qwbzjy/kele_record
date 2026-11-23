package com.cola.record.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cola.record.ColaApp

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ColaApp ?: return Result.failure()
        val outcome = try {
            app.syncManager.autoUpload()
        } catch (e: Exception) {
            return if (runAttemptCount >= 5) Result.failure() else Result.retry()
        }

        return when (outcome) {
            is SyncOutcome.Success -> {
                // 上传期间又有新记录：交给 WorkManager 退避后继续
                if (app.syncSettings.snapshot().dirty && runAttemptCount < 5) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
            is SyncOutcome.Retry -> {
                if (runAttemptCount >= 5) Result.failure() else Result.retry()
            }
            is SyncOutcome.Conflict -> Result.success()
            is SyncOutcome.Skipped -> Result.success()
            is SyncOutcome.Failure -> Result.failure()
        }
    }
}
