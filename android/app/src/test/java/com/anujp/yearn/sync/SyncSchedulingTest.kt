package com.anujp.yearn.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.impl.WorkManagerImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import java.util.concurrent.Executor
import java.util.concurrent.Executors
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
    private val direct = Executor { it.run() }

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
        val s = WorkSyncScheduler(context, direct)
        s.enqueueNow()
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("yearn-sync-now").get()
        assertEquals(1, infos.size)
        assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
        val spec = WorkManagerImpl.getInstance(context).workDatabase.workSpecDao().getWorkSpec(infos.single().id.toString())!!
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(30_000L, spec.backoffDelayDuration)
    }

    @Test
    fun enqueueNow_replacesPredecessorInBackoff_notChainedBehindIt() {
        val s = WorkSyncScheduler(context, direct)
        s.enqueueNow() // no network in the test driver: stays ENQUEUED, like a run sleeping in backoff
        s.enqueueNow()
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("yearn-sync-now").get()
        val live = infos.filter { !it.state.isFinished }
        assertEquals(1, live.size)
        assertEquals(WorkInfo.State.ENQUEUED, live.single().state)
        assertTrue(infos.none { it.state == WorkInfo.State.BLOCKED })
    }

    @Test
    fun enqueueNow_appendsBehindARunningPass() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(Executors.newCachedThreadPool())
                .setWorkerFactory(object : WorkerFactory() {
                    override fun createWorker(c: Context, name: String, p: WorkerParameters): ListenableWorker =
                        SyncWorker(c, p, PendingUploader { started.complete(Unit); gate.await(); UploadOutcome(0, false) }, SettingsStore(c, XorBox()))
                }).build(),
        )
        val wm = WorkManager.getInstance(context)
        val s = WorkSyncScheduler(context, direct)
        s.enqueueNow()
        val first = wm.getWorkInfosForUniqueWork("yearn-sync-now").get().single().id
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(first)
        withTimeout(10_000) { started.await() }
        assertEquals(WorkInfo.State.RUNNING, wm.getWorkInfoById(first).get()!!.state)

        s.enqueueNow()
        val infos = wm.getWorkInfosForUniqueWork("yearn-sync-now").get()
        assertEquals(2, infos.size)
        assertEquals(WorkInfo.State.BLOCKED, infos.single { it.id != first }.state)
        gate.complete(Unit)
        Unit
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
