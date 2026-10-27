package com.anujp.yearn.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import com.anujp.yearn.data.Timestamps
import kotlinx.coroutines.CancellationException
import java.time.ZonedDateTime

fun interface PendingUploader {
    suspend fun uploadPending(): UploadOutcome
}

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val uploader: PendingUploader,
    private val settings: SettingsStore,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = try {
            uploader.uploadPending()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return Result.retry()
        }
        if (outcome.retry) return Result.retry()
        if (outcome.uploaded > 0) {
            try {
                settings.setLastSuccessfulSync(Timestamps.format(ZonedDateTime.now()))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The uploads themselves succeeded; a failed bookkeeping write must not redo them.
            }
        }
        return Result.success()
    }
}

class SyncWorkerFactory(
    private val uploader: PendingUploader,
    private val settings: SettingsStore,
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) SyncWorker(appContext, workerParameters, uploader, settings) else null
}
