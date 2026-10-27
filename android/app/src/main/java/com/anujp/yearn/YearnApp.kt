package com.anujp.yearn

import android.app.Application
import android.content.Context
import com.anujp.yearn.capture.CaptureService
import com.anujp.yearn.capture.Copier
import com.anujp.yearn.capture.FileCopier
import com.anujp.yearn.capture.deviceJson
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.YearnDatabase
import androidx.work.Configuration
import com.anujp.yearn.sync.CaptureUploader
import com.anujp.yearn.sync.KeystoreSecretBox
import com.anujp.yearn.sync.ReceiverClient
import com.anujp.yearn.sync.SettingsStore
import com.anujp.yearn.sync.SyncWorkerFactory
import com.anujp.yearn.sync.WorkSyncScheduler
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.ZonedDateTime
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
    val uploader: CaptureUploader,
    val settings: SettingsStore,
    /** The one way to get a receiver client: from the saved address and token; null means not set up. */
    val clientFactory: suspend () -> ReceiverClient? = receiverClientFactory(settings),
) {
    companion object {
        fun build(
            context: Context,
            recovery: suspend () -> Unit = {},
            db: YearnDatabase = YearnDatabase.create(context),
            scheduler: SyncScheduler = WorkSyncScheduler(context),
            settings: SettingsStore = SettingsStore(context, KeystoreSecretBox()),
        ): AppGraph {
            val app = context.applicationContext
            val dao = db.captureDao()
            val copier = FileCopier(app.contentResolver, File(app.filesDir, "captures"))
            val thumbs = File(app.filesDir, "thumbs")
            val service = CaptureService(
                dao, copier, scheduler, thumbs, deviceJson(app), beforeFirstCapture = recovery,
            )
            val clientFactory = receiverClientFactory(settings)
            val uploader = CaptureUploader(dao, client = clientFactory, clock = { ZonedDateTime.now() })
            return AppGraph(db, dao, copier, thumbs, scheduler, service, uploader, settings, clientFactory)
        }
    }
}

fun receiverClientFactory(settings: SettingsStore): suspend () -> ReceiverClient? = {
    val url = settings.baseUrl.first()?.toHttpUrlOrNull()
    val token = settings.token()
    if (url == null || token == null) null else ReceiverClient.create(url, token)
}

open class YearnApp : Application(), Configuration.Provider {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recovery = CompletableDeferred<Unit>()
    private val defaultGraph: AppGraph by lazy { AppGraph.build(this, recovery = { recovery.await() }) }

    /** Swap in tests; null means use the real graph. */
    var graphOverride: AppGraph? = null

    val graph: AppGraph get() = graphOverride ?: defaultGraph

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(SyncWorkerFactory(graph.uploader, graph.settings))
            .build()

    override fun onCreate() {
        super.onCreate()
        val g = graph
        appScope.launch {
            try {
                if (g.dao.markInterruptedCopies() > 0) g.scheduler.enqueueNow()
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
