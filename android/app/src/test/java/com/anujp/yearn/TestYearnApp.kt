package com.anujp.yearn

import android.net.Uri
import androidx.room.Room
import com.anujp.yearn.capture.CaptureService
import com.anujp.yearn.capture.Copier
import com.anujp.yearn.capture.CopyResult
import com.anujp.yearn.capture.deviceJson
import com.anujp.yearn.data.YearnDatabase
import com.anujp.yearn.sync.CaptureUploader
import com.anujp.yearn.sync.SettingsStore
import com.anujp.yearn.sync.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/** Copier that never touches a real URI; with [gate] set, every copy waits for it. */
class GatedCopier : Copier {
    @Volatile var gate: CompletableDeferred<Unit>? = null
    val calls = java.util.Collections.synchronizedList(mutableListOf<Int>())

    override suspend fun copy(captureId: String, index: Int, uri: Uri, onBytes: (Long) -> Unit): CopyResult {
        calls += index
        gate?.await()
        onBytes(10)
        return CopyResult.Done("/nonexistent/$captureId/$index", "f$index", "image/jpeg", 10, "sha$index")
    }
}

class FakeSyncScheduler : SyncScheduler {
    var nowCount = 0
    var dailyCount = 0
    override fun enqueueNow() { nowCount++ }
    override fun ensureDaily() { dailyCount++ }
}

/** Swaps in an in-memory graph before [YearnApp.onCreate] reads it. */
class TestYearnApp : YearnApp() {
    lateinit var db: YearnDatabase
    val copier = GatedCopier()
    val scheduler = FakeSyncScheduler()

    override fun onCreate() {
        db = Room.inMemoryDatabaseBuilder(this, YearnDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.captureDao()
        val thumbs = File(cacheDir, "thumbs-test")
        val service = CaptureService(dao, copier, scheduler, thumbs, deviceJson(this))
        val settings = SettingsStore(this, object : com.anujp.yearn.sync.SecretBox {
            override fun seal(plain: ByteArray) = plain
            override fun open(sealed: ByteArray) = sealed
        })
        val uploader = CaptureUploader(dao, { null }, { java.time.ZonedDateTime.now() })
        graphOverride = AppGraph(db, dao, copier, thumbs, scheduler, service, uploader, settings)
        super.onCreate()
    }

    fun captureCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM captures").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
}
