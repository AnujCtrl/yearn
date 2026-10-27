package com.anujp.yearn.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.FileState
import com.anujp.yearn.data.SyncStatus
import com.anujp.yearn.data.YearnDatabase
import com.anujp.yearn.sync.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CaptureServiceTest {
    private lateinit var db: YearnDatabase
    private lateinit var dao: CaptureDao
    private lateinit var thumbsDir: File
    private val hints = SourceHints(null, null, null, null, null, null)
    private val device = JsonObject(mapOf("model" to JsonPrimitive("test"), "app_version" to JsonNull))
    private val now = ZonedDateTime.of(2026, 10, 27, 9, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30))

    private class FakeScheduler : SyncScheduler {
        var now = 0
        var daily = 0
        override fun enqueueNow() { now++ }
        override fun ensureDaily() { daily++ }
    }

    private class FakeCopier(val onCall: suspend (String, Int) -> Unit = { _, _ -> }) : Copier {
        val calls = mutableListOf<Int>()
        val failIndexes = mutableSetOf<Int>()
        override suspend fun copy(captureId: String, index: Int, uri: Uri, onBytes: (Long) -> Unit): CopyResult {
            calls += index
            onCall(captureId, index)
            onBytes(10)
            if (index in failIndexes) return CopyResult.Failed("f$index", "boom")
            return CopyResult.Done("/data/$captureId/$index", "f$index", "image/jpeg", 10, "sha$index")
        }
    }

    private val scheduler = FakeScheduler()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, YearnDatabase::class.java).allowMainThreadQueries().build()
        dao = db.captureDao()
        thumbsDir = File(ctx.cacheDir, "thumbs-test").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
        thumbsDir.deleteRecursively()
    }

    private fun service(
        copier: Copier = FakeCopier(),
        before: suspend () -> Unit = {},
        thumb: (File, String?, File) -> File? = { _, _, _ -> null },
    ) = CaptureService(
        dao, copier, scheduler, thumbsDir, device,
        clock = { now }, newId = { "cap-1" }, beforeFirstCapture = before, thumbnailer = thumb,
    )

    private fun streamIntent(n: Int): Intent {
        val uris = ArrayList((0 until n).map { Uri.parse("content://m/$it") })
        return Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }

    private fun textIntent() =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "hello https://example.com/a")

    @Test
    fun begin_commitsRowBeforeAnyCopy() = runTest {
        var seen: Boolean? = null
        val copier = FakeCopier { id, _ -> seen = dao.get(id) != null }
        val svc = service(copier)
        val p = svc.begin(streamIntent(2), hints)
        assertEquals(2, p.filesTotal)
        assertFalse(p.complete)
        val row = dao.get("cap-1")!!
        assertEquals(SyncStatus.WAITING, row.syncStatus)
        assertFalse(row.filesComplete)
        assertEquals(listOf(FileState.PENDING, FileState.PENDING), dao.files("cap-1").map { it.state })
        assertTrue(copier.calls.isEmpty())
        svc.copyFiles("cap-1") {}
        assertEquals(true, seen)
    }

    @Test
    fun textShare_isCompleteAtBeginAndEnqueuesOnce() = runTest {
        val p = service().begin(textIntent(), hints)
        assertTrue(p.complete)
        assertEquals(0, p.filesTotal)
        assertEquals("https://example.com/a", p.preview.url)
        val row = dao.get("cap-1")!!
        assertTrue(row.filesComplete)
        assertEquals("2026-10-27T09:30:00+05:30", row.capturedAt)
        assertEquals(now.toInstant().toEpochMilli(), row.capturedAtMillis)
        assertEquals(0, row.attempts)
        assertFalse(row.noteNeedsSync)
        assertEquals(1, scheduler.now)
    }

    @Test
    fun copyFiles_updatesRowsThumbnailsAndEnqueuesOnce() = runTest {
        val copier = FakeCopier().apply { failIndexes += 0 }
        val svc = service(copier, thumb = { src, _, out -> if (src.path.endsWith("/1")) out else null })
        svc.begin(streamIntent(3), hints)
        assertEquals(0, scheduler.now)
        val progress = mutableListOf<CaptureProgress>()
        svc.copyFiles("cap-1") { progress += it }
        val files = dao.files("cap-1")
        assertEquals(listOf(FileState.ERROR, FileState.DONE, FileState.DONE), files.map { it.state })
        assertEquals("boom", files[0].error)
        assertEquals("sha1", files[1].sha256)
        assertEquals("/data/cap-1/1", files[1].localPath)
        val row = dao.get("cap-1")!!
        assertTrue(row.filesComplete)
        assertEquals(File(thumbsDir, "cap-1.jpg").path, row.thumbPath)
        assertEquals(1, scheduler.now)
        assertTrue(progress.last().complete)
        assertEquals(row.thumbPath, progress.last().thumbPath)
        assertEquals(3, progress.last().filesDone)
        assertNull(progress.last().bytesTotal)
        assertEquals(listOf(0, 1, 2), copier.calls)
        // already complete: no-op
        svc.copyFiles("cap-1") {}
        assertEquals(1, scheduler.now)
        assertEquals(listOf(0, 1, 2), copier.calls)
    }

    @Test
    fun emptyShare_createsCaptureWithNoFiles() = runTest {
        val p = service().begin(Intent(Intent.ACTION_SEND).setType("application/x-weird"), hints)
        assertEquals(0, p.filesTotal)
        assertTrue(p.complete)
        assertNotNull(dao.get("cap-1"))
        assertTrue(dao.files("cap-1").isEmpty())
    }

    @Test
    fun begin_wrapsDatabaseErrorInCaptureFailed() = runTest {
        db.close()
        try {
            service().begin(textIntent(), hints)
            fail("expected CaptureFailed")
        } catch (e: CaptureFailed) {
            assertNotNull(e.cause)
        }
    }

    @Test
    fun saveNote_setsNeedsSyncAndEnqueues() = runTest {
        val svc = service()
        svc.begin(textIntent(), hints)
        val before = scheduler.now
        svc.saveNote("cap-1", "remember this")
        val row = dao.get("cap-1")!!
        assertEquals("remember this", row.note)
        assertTrue(row.noteNeedsSync)
        assertEquals("2026-10-27T09:30:00+05:30", row.noteUpdatedAt)
        assertEquals(before + 1, scheduler.now)
    }

    @Test
    fun begin_waitsForInterruptedCopyRecovery() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var n = 0
        val svc = CaptureService(
            dao, FakeCopier(), scheduler, thumbsDir, device,
            clock = { now }, newId = { "cap-${++n}" },
            beforeFirstCapture = { calls++; gate.await() },
        )
        val job = launch { svc.begin(textIntent(), hints) }
        advanceUntilIdle()
        assertNull(dao.get("cap-1"))
        assertEquals(1, calls)
        gate.complete(Unit)
        job.join()
        assertNotNull(dao.get("cap-1"))
        svc.begin(textIntent(), hints)
        assertNotNull(dao.get("cap-2"))
        assertEquals(1, calls)
    }
}
