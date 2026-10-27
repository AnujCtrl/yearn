package com.anujp.yearn.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SyncSchedulingTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @Test
    fun enqueueNow_requiresNetworkAndIsUnique() {
        val s = WorkSyncScheduler(context)
        s.enqueueNow()
        s.enqueueNow()
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("yearn-sync-now").get()
        assertEquals(2, infos.size) // APPEND_OR_REPLACE chains the second behind the first
        infos.forEach { assertEquals(NetworkType.CONNECTED, it.constraints.requiredNetworkType) }
    }

    @Test
    fun ensureDaily_isPeriodicAndKept() {
        val s = WorkSyncScheduler(context)
        s.ensureDaily()
        val id = WorkManager.getInstance(context).getWorkInfosForUniqueWork("yearn-sync-daily").get().single().id
        s.ensureDaily()
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("yearn-sync-daily").get()
        assertEquals(1, infos.size)
        assertEquals(id, infos.single().id)
        val w = infos.single()
        assertEquals(NetworkType.CONNECTED, w.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(24), w.periodicityInfo!!.repeatIntervalMillis)
    }

    @Test
    fun worker_returnsRetryWhenUploaderAsks() = runBlocking {
        val settings = SettingsStore(context, XorBox())
        fun build(outcome: UploadOutcome?) = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(c: Context, name: String, p: WorkerParameters): ListenableWorker =
                    SyncWorker(c, p, PendingUploader {
                        outcome ?: throw IllegalStateException("boom")
                    }, settings)
            }).build()

        assertEquals(ListenableWorker.Result.retry(), build(UploadOutcome(1, true)).doWork())
        assertEquals(ListenableWorker.Result.retry(), build(null).doWork())
        assertNull(settings.lastSuccessfulSync.first())
        assertEquals(ListenableWorker.Result.success(), build(UploadOutcome(2, false)).doWork())
        assertNotNull(settings.lastSuccessfulSync.first())
    }
}
