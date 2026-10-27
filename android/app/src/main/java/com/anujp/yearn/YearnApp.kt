package com.anujp.yearn

import android.app.Application
import android.content.Context
import com.anujp.yearn.capture.CaptureService
import com.anujp.yearn.capture.Copier
import com.anujp.yearn.capture.FileCopier
import com.anujp.yearn.capture.deviceJson
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.YearnDatabase
import com.anujp.yearn.sync.NoopSyncScheduler
import com.anujp.yearn.sync.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Plain singletons; tests replace [YearnApp.graph] with an in-memory one. */
class AppGraph(
    val db: YearnDatabase,
    val dao: CaptureDao,
    val copier: Copier,
    val thumbsDir: File,
    val scheduler: SyncScheduler,
    val service: CaptureService,
) {
    companion object {
        fun build(
            context: Context,
            recovery: suspend () -> Unit = {},
            db: YearnDatabase = YearnDatabase.create(context),
            scheduler: SyncScheduler = NoopSyncScheduler,
        ): AppGraph {
            val app = context.applicationContext
            val dao = db.captureDao()
            val copier = FileCopier(app.contentResolver, File(app.filesDir, "captures"))
            val thumbs = File(app.filesDir, "thumbs")
            val service = CaptureService(
                dao, copier, scheduler, thumbs, deviceJson(app), beforeFirstCapture = recovery,
            )
            return AppGraph(db, dao, copier, thumbs, scheduler, service)
        }
    }
}

class YearnApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recovery = CompletableDeferred<Unit>()
    private val defaultGraph: AppGraph by lazy { AppGraph.build(this, recovery = { recovery.await() }) }

    /** Swap in tests; null means use the real graph. */
    var graphOverride: AppGraph? = null

    val graph: AppGraph get() = graphOverride ?: defaultGraph

    override fun onCreate() {
        super.onCreate()
        val g = graph
        appScope.launch {
            try {
                g.dao.markInterruptedCopies()
            } finally {
                // Never block shares forever, even if recovery itself failed.
                recovery.complete(Unit)
            }
        }
        g.scheduler.ensureDaily()
    }

    companion object {
        val Context.yearnGraph: AppGraph get() = (applicationContext as YearnApp).graph
    }
}
