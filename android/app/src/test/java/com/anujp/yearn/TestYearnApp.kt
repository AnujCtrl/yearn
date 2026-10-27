package com.anujp.yearn

import android.net.Uri
import androidx.room.Room
import com.anujp.yearn.capture.CaptureService
import com.anujp.yearn.capture.Copier
import com.anujp.yearn.capture.CopyResult
import com.anujp.yearn.capture.deviceJson
import com.anujp.yearn.data.YearnDatabase
import com.anujp.yearn.sync.NoopSyncScheduler
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

/** Swaps in an in-memory graph before [YearnApp.onCreate] reads it. */
class TestYearnApp : YearnApp() {
    lateinit var db: YearnDatabase
    val copier = GatedCopier()

    override fun onCreate() {
        db = Room.inMemoryDatabaseBuilder(this, YearnDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.captureDao()
        val thumbs = File(cacheDir, "thumbs-test")
        val service = CaptureService(dao, copier, NoopSyncScheduler, thumbs, deviceJson(this))
        graphOverride = AppGraph(db, dao, copier, thumbs, NoopSyncScheduler, service)
        super.onCreate()
    }

    fun captureCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM captures").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
}
