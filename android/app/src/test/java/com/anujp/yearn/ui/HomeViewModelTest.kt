package com.anujp.yearn.ui

import com.anujp.yearn.FakeSyncScheduler
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.CaptureEntity
import com.anujp.yearn.data.FileEntity
import com.anujp.yearn.data.SyncStatus
import com.anujp.yearn.ui.home.HomeViewModel
import com.anujp.yearn.ui.home.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZonedDateTime

/** In-memory stand-in for the Room DAO: observeAll is a StateFlow, setSync rewrites the row. */
private class FakeCaptureDao : CaptureDao() {
    val all = MutableStateFlow<List<CaptureEntity>>(emptyList())

    override suspend fun insertCaptureRow(c: CaptureEntity) {
        all.value = (all.value + c).sortedByDescending { it.capturedAtMillis }
    }
    override suspend fun insertFileRows(files: List<FileEntity>) = Unit
    override fun observeAll(): Flow<List<CaptureEntity>> = all
    override fun observe(id: String): Flow<CaptureEntity?> = all.map { l -> l.find { it.id == id } }
    override suspend fun get(id: String): CaptureEntity? = all.value.find { it.id == id }
    override suspend fun files(id: String): List<FileEntity> = emptyList()
    override suspend fun updateFile(f: FileEntity) = Unit
    override suspend fun setFilesComplete(id: String, thumbPath: String?) = Unit
    override suspend fun setSync(id: String, status: SyncStatus, attempts: Int, lastError: String?, uploadedAt: String?) {
        all.value = all.value.map {
            if (it.id == id) it.copy(syncStatus = status, attempts = attempts, lastError = lastError, uploadedAt = uploadedAt) else it
        }
    }
    override suspend fun setNote(id: String, note: String, noteUpdatedAt: String) = Unit
    override suspend fun clearNoteNeedsSync(id: String) = Unit
    override suspend fun pendingUploads(): List<CaptureEntity> = emptyList()
    override suspend fun failPendingFiles(error: String): Int = 0
    override suspend fun completeCapturesWithoutPending(): Int = 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dao = FakeCaptureDao()
    private val scheduler = FakeSyncScheduler()
    private var now: ZonedDateTime = ZonedDateTime.parse("2026-10-27T15:00:00+05:30[Asia/Kolkata]")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun capture(
        id: String,
        sync: SyncStatus = SyncStatus.WAITING,
        lastError: String? = null,
        at: ZonedDateTime = now.minusMinutes(5),
        attempts: Int = 0,
    ) = CaptureEntity(
        id = id, capturedAt = at.toOffsetDateTime().toString(), capturedAtMillis = at.toInstant().toEpochMilli(),
        baseRecordJson = "{}", sourceLabel = "Chrome", previewTitle = null, previewText = "text $id",
        previewUrl = null, thumbPath = null, note = null, noteUpdatedAt = null, noteNeedsSync = false,
        filesComplete = true, syncStatus = sync, attempts = attempts, lastError = lastError, uploadedAt = null,
    )

    private fun TestScope.viewModel(): HomeViewModel {
        val vm = HomeViewModel(dao, scheduler) { now }
        // WhileSubscribed flows need a collector, as the screen would be.
        backgroundScope.launch { vm.rows.collect {} }
        backgroundScope.launch { vm.waitingCount.collect {} }
        runCurrent()
        return vm
    }

    private fun z(s: String) = ZonedDateTime.parse(s)

    @Test
    fun relativeTime_buckets() {
        val n = z("2026-10-27T15:00:00+05:30")
        assertEquals("now", relativeTime(n, n))
        assertEquals("now", relativeTime(n.minusSeconds(59), n))
        assertEquals("now", relativeTime(n.plusMinutes(3), n)) // clock skew: never "in the future"
        assertEquals("1 min", relativeTime(n.minusMinutes(1), n))
        assertEquals("59 min", relativeTime(n.minusMinutes(59), n))
        assertEquals("59 min", relativeTime(n.minusMinutes(59).minusSeconds(59), n))
        assertEquals("1 h", relativeTime(n.minusMinutes(60), n))
        assertEquals("23 h", relativeTime(n.minusHours(23), n))
        assertEquals("23 h", relativeTime(n.minusHours(23).minusMinutes(59), n))
        // Under 24 h wins even when the calendar date is yesterday.
        val oneAm = z("2026-10-27T01:00:00+05:30")
        assertEquals("3 h", relativeTime(z("2026-10-26T22:00:00+05:30"), oneAm))
        assertEquals("Yesterday", relativeTime(n.minusHours(24), n))
        assertEquals("Yesterday", relativeTime(z("2026-10-26T08:00:00+05:30"), z("2026-10-27T23:30:00+05:30")))
        // 26 h ago but two calendar days back.
        assertEquals("Oct 25", relativeTime(z("2026-10-25T23:00:00+05:30"), oneAm))
        assertEquals("Oct 24", relativeTime(z("2026-10-24T12:00:00+05:30"), n))
        // The calendar check happens in now's zone.
        assertEquals("Yesterday", relativeTime(z("2026-10-25T20:00:00Z"), n))
    }

    @Test
    fun rows_mapStatusWordsAndReasons() = runTest {
        dao.all.value = listOf(
            capture("failed", SyncStatus.FAILED, lastError = "Desktop rejected the file"),
            capture("uploading", SyncStatus.UPLOADING, lastError = "Couldn't reach your desktop"),
            capture("waiting", SyncStatus.WAITING),
            capture("uploaded", SyncStatus.UPLOADED),
        )
        val rows = viewModel().rows.value.associateBy { it.id }
        assertEquals("Failed", rows.getValue("failed").status)
        assertEquals("Desktop rejected the file", rows.getValue("failed").failedReason)
        assertEquals("Uploading", rows.getValue("uploading").status)
        assertNull(rows.getValue("uploading").failedReason) // a stale error on a non-failed row stays hidden
        assertEquals("Waiting", rows.getValue("waiting").status)
        assertEquals("Uploaded", rows.getValue("uploaded").status)
        assertEquals("text failed", rows.getValue("failed").title)
        assertEquals("Chrome · 5 min", rows.getValue("failed").subtitle)
        assertFalse(rows.getValue("failed").hasNote)
    }

    @Test
    fun retry_resetsFailedAndEnqueues() = runTest {
        dao.all.value = listOf(
            capture("bad", SyncStatus.FAILED, lastError = "Desktop rejected the file", attempts = 4),
            capture("ok", SyncStatus.UPLOADED),
        )
        val vm = viewModel()
        vm.retry("ok")
        runCurrent()
        assertEquals(0, scheduler.nowCount)
        assertEquals(SyncStatus.UPLOADED, dao.all.value.first { it.id == "ok" }.syncStatus)

        vm.retry("bad")
        runCurrent()
        val bad = dao.all.value.first { it.id == "bad" }
        assertEquals(SyncStatus.WAITING, bad.syncStatus)
        assertEquals(0, bad.attempts)
        assertNull(bad.lastError)
        assertEquals(1, scheduler.nowCount)
        assertEquals("Waiting", vm.rows.value.first { it.id == "bad" }.status)
        assertEquals(1, vm.waitingCount.value)
    }

    @Test
    fun waitingCount_countsWaitingAndUploading() = runTest {
        dao.all.value = listOf(
            capture("w1", SyncStatus.WAITING),
            capture("u", SyncStatus.UPLOADING),
            capture("done", SyncStatus.UPLOADED),
            capture("f", SyncStatus.FAILED),
            capture("w2", SyncStatus.WAITING),
        )
        val vm = viewModel()
        assertEquals(3, vm.waitingCount.value)
        vm.syncNow()
        assertEquals(1, scheduler.nowCount)
        dao.setSync("w1", SyncStatus.UPLOADED, 0, null, null)
        runCurrent()
        assertEquals(2, vm.waitingCount.value)
    }

    @Test
    fun rows_refreshRelativeTimeEveryMinute() = runTest {
        dao.all.value = listOf(capture("a", at = now.minusSeconds(30)))
        val vm = viewModel()
        assertEquals("Chrome · now", vm.rows.value.single().subtitle)
        assertTrue(vm.rows.value.single().today)
        now = now.plusMinutes(1)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("Chrome · 1 min", vm.rows.value.single().subtitle)
    }
}
