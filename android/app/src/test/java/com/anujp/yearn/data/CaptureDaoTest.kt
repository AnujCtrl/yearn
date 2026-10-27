package com.anujp.yearn.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureDaoTest {
    private lateinit var db: YearnDatabase
    private lateinit var dao: CaptureDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, YearnDatabase::class.java).allowMainThreadQueries().build()
        dao = db.captureDao()
    }

    @After
    fun tearDown() = db.close()

    private fun capture(
        id: String,
        millis: Long = 1000,
        filesComplete: Boolean = true,
        sync: SyncStatus = SyncStatus.WAITING,
        noteNeedsSync: Boolean = false,
    ) = CaptureEntity(
        id = id, capturedAt = "2026-10-26T10:00:00+05:30", capturedAtMillis = millis,
        baseRecordJson = "{}", sourceLabel = "Chrome", previewTitle = null, previewText = null,
        previewUrl = null, thumbPath = null, note = null, noteUpdatedAt = null,
        noteNeedsSync = noteNeedsSync, filesComplete = filesComplete, syncStatus = sync,
        attempts = 0, lastError = null, uploadedAt = null,
    )

    private fun file(id: String, index: Int, state: FileState) = FileEntity(
        captureId = id, index = index, sourceUri = "content://x/$index", displayName = "f$index",
        mimeType = null, size = null, localPath = null, sha256 = null, error = null, state = state,
    )

    @Test
    fun observeAll_newestFirst() = runTest {
        dao.insertCapture(capture("old", 1000), emptyList())
        dao.insertCapture(capture("new", 3000), emptyList())
        dao.insertCapture(capture("mid", 2000), emptyList())
        assertEquals(listOf("new", "mid", "old"), dao.observeAll().first().map { it.id })
    }

    @Test
    fun pendingUploads_skipsIncompleteAndUploaded() = runTest {
        dao.insertCapture(capture("b-waiting", 2000), emptyList())
        dao.insertCapture(capture("a-uploading", 1000, sync = SyncStatus.UPLOADING), emptyList())
        dao.insertCapture(capture("incomplete", 500, filesComplete = false), emptyList())
        dao.insertCapture(capture("uploaded", 600, sync = SyncStatus.UPLOADED), emptyList())
        dao.insertCapture(capture("failed", 700, sync = SyncStatus.FAILED), emptyList())
        dao.insertCapture(capture("note-only", 3000, sync = SyncStatus.UPLOADED, noteNeedsSync = true), emptyList())
        assertEquals(
            listOf("a-uploading", "b-waiting", "note-only"),
            dao.pendingUploads().map { it.id },
        )
    }

    @Test
    fun setNote_marksNeedsSync() = runTest {
        dao.insertCapture(capture("c"), emptyList())
        dao.setNote("c", "hello", "2026-10-26T11:00:00+05:30")
        val c = dao.get("c")!!
        assertEquals("hello", c.note)
        assertEquals("2026-10-26T11:00:00+05:30", c.noteUpdatedAt)
        assertTrue(c.noteNeedsSync)
        dao.clearNoteNeedsSync("c")
        assertFalse(dao.get("c")!!.noteNeedsSync)
    }

    @Test
    fun markInterruptedCopies_turnsPendingIntoErrorsAndCompletesCapture() = runTest {
        dao.insertCapture(capture("c", filesComplete = false), listOf(
            file("c", 0, FileState.DONE).copy(sha256 = "a".repeat(64)),
            file("c", 1, FileState.PENDING),
        ))
        assertTrue(dao.pendingUploads().isEmpty())

        assertEquals(1, dao.markInterruptedCopies())

        val files = dao.files("c")
        assertEquals(FileState.DONE, files[0].state)
        assertEquals(FileState.ERROR, files[1].state)
        assertEquals(INTERRUPTED_COPY_ERROR, files[1].error)
        assertEquals("Copy interrupted: yearn was closed before this file finished copying", INTERRUPTED_COPY_ERROR)
        val c = dao.get("c")!!
        assertTrue(c.filesComplete)
        assertEquals(SyncStatus.WAITING, c.syncStatus)
        assertEquals(listOf("c"), dao.pendingUploads().map { it.id })
        assertEquals(0, dao.markInterruptedCopies())
    }
}
