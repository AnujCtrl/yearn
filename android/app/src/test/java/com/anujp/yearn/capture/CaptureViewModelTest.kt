package com.anujp.yearn.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anujp.yearn.FakeSyncScheduler
import com.anujp.yearn.GatedCopier
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.FileState
import com.anujp.yearn.data.INTERRUPTED_COPY_ERROR
import com.anujp.yearn.data.YearnDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CaptureViewModelTest {
    private lateinit var db: YearnDatabase
    private lateinit var dao: CaptureDao
    private lateinit var service: CaptureService
    private val copier = GatedCopier()
    private val scheduler = FakeSyncScheduler()
    private val store = ViewModelStore()
    private val hints = SourceHints(null, null, null, null, null, null)

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, YearnDatabase::class.java).allowMainThreadQueries().build()
        dao = db.captureDao()
        service = CaptureService(dao, copier, scheduler, File(ctx.cacheDir, "thumbs-vm"), JsonObject(emptyMap()))
    }

    @After
    fun tearDown() {
        copier.gate?.complete(Unit)
        store.clear()
        runCatching { db.close() }
    }

    private fun streamIntent(): Intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(
            Intent.EXTRA_STREAM, arrayListOf(Uri.parse("content://m/0"), Uri.parse("content://m/1")),
        )

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()): CaptureViewModel =
        ViewModelProvider.create(
            store,
            viewModelFactory { initializer { CaptureViewModel(service, saved, savedHoldMillis = 60_000, newId = { "new-id" }) } },
        )[CaptureViewModel::class]

    private fun idleUntil(what: String, timeoutMs: Long = 5_000, done: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (done()) return
            if (System.currentTimeMillis() > end) fail("timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    private fun allIds(): List<String> =
        db.openHelper.readableDatabase.query("SELECT id FROM captures").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    @Test
    fun freshStart_savesTheIdMarkerBeforeTheRowExists() {
        copier.gate = CompletableDeferred()
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        vm.start(streamIntent()) { hints }
        assertEquals("new-id", saved.get<String>(CaptureViewModel.KEY_CAPTURE_ID))
        idleUntil("Saving") { vm.state.value is SheetState.Saving }
        assertEquals(listOf("new-id"), allIds())
    }

    @Test
    fun restoreWithMarkerButNoRow_capturesUnderThatId() {
        val saved = SavedStateHandle(mapOf(CaptureViewModel.KEY_CAPTURE_ID to "died-before-commit"))
        val vm = viewModel(saved)
        vm.start(streamIntent()) { hints }
        idleUntil("Saved") { vm.state.value is SheetState.Saved }
        assertEquals(listOf("died-before-commit"), allIds())
        assertEquals(listOf(FileState.DONE, FileState.DONE), runBlocking { dao.files("died-before-commit") }.map { it.state })
        assertEquals(listOf(0, 1), copier.calls.toList())
    }

    @Test
    fun restoreWithRow_finishesWithoutASecondCapture() {
        runBlocking { service.begin(streamIntent(), hints, id = "already-here") }
        val saved = SavedStateHandle(mapOf(CaptureViewModel.KEY_CAPTURE_ID to "already-here"))
        val vm = viewModel(saved)
        vm.start(streamIntent()) { hints }
        idleUntil("Finished") { vm.state.value == SheetState.Finished }
        assertEquals(listOf("already-here"), allIds())
        assertTrue(copier.calls.isEmpty())
    }

    @Test
    fun clearedMidCopy_marksTheRestInterruptedAndQueuesTheUpload() {
        copier.gate = CompletableDeferred()
        val vm = viewModel()
        vm.start(streamIntent()) { hints }
        idleUntil("copy started") { copier.calls.isNotEmpty() }
        assertFalse(runBlocking { dao.get("new-id")!!.filesComplete })

        store.clear() // the activity finished for good, not a recreate

        idleUntil("capture completed") { runBlocking { dao.get("new-id")!!.filesComplete } }
        val files = runBlocking { dao.files("new-id") }
        assertEquals(listOf(FileState.ERROR, FileState.ERROR), files.map { it.state })
        assertEquals(INTERRUPTED_COPY_ERROR, files[0].error)
        assertEquals(1, scheduler.nowCount)
        assertEquals(listOf("new-id"), runBlocking { dao.pendingUploads() }.map { it.id })
    }
}
