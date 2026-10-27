package com.anujp.yearn.ui

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anujp.yearn.FakeSyncScheduler
import com.anujp.yearn.capture.CaptureService
import com.anujp.yearn.capture.SourceHints
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.SyncStatus
import com.anujp.yearn.data.YearnDatabase
import com.anujp.yearn.GatedCopier
import com.anujp.yearn.ui.detail.DetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DetailViewModelTest {
    private lateinit var db: YearnDatabase
    private lateinit var dao: CaptureDao
    private lateinit var service: CaptureService
    private val scheduler = FakeSyncScheduler()
    private val hints = SourceHints(null, null, null, null, null, null)
    private val now = ZonedDateTime.of(2026, 10, 27, 12, 41, 0, 0, ZoneOffset.ofHoursMinutes(5, 30))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, YearnDatabase::class.java).allowMainThreadQueries().build()
        dao = db.captureDao()
        val device = JsonObject(mapOf("model" to JsonPrimitive("test"), "app_version" to JsonNull))
        service = CaptureService(dao, GatedCopier(), scheduler, File(ctx.cacheDir, "thumbs-test"), device, clock = { now })
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
        Dispatchers.resetMain()
    }

    private suspend fun textCapture(intent: Intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "Brown butter miso pasta https://www.instagram.com/reel/abc")): String =
        service.begin(intent, hints).captureId

    @Test
    fun saveNote_afterUpload_marksNoteNeedsSync() = runTest {
        val id = textCapture()
        dao.setSync(id, SyncStatus.UPLOADED, attempts = 1, lastError = null, uploadedAt = "2026-10-27T12:43:00+05:30")
        val before = scheduler.nowCount
        val vm = DetailViewModel(id, dao, service)

        vm.saveNote("make this sunday, double the miso").join()

        val row = dao.get(id)!!
        assertEquals("make this sunday, double the miso", row.note)
        assertTrue(row.noteNeedsSync)
        assertEquals(SyncStatus.UPLOADED, row.syncStatus) // the capture itself stays on the desktop
        assertEquals(before + 1, scheduler.nowCount)
    }

    @Test
    fun saveNote_skipsUnchangedAndBlankButSavesAClear() = runTest {
        val id = textCapture()
        val vm = DetailViewModel(id, dao, service)
        val start = scheduler.nowCount

        vm.saveNote("   ").join() // nothing to save: there was no note
        assertEquals(null, dao.get(id)!!.note)
        assertFalse(dao.get(id)!!.noteNeedsSync)

        vm.saveNote("double the miso ").join()
        assertEquals("double the miso", dao.get(id)!!.note)
        vm.saveNote("double the miso").join() // unchanged: no second upload
        assertEquals(start + 1, scheduler.nowCount)

        dao.clearNoteNeedsSync(id)
        vm.saveNote("").join() // clearing an existing note is a change the desktop must hear about
        assertEquals("", dao.get(id)!!.note)
        assertTrue(dao.get(id)!!.noteNeedsSync)
        assertEquals(start + 2, scheduler.nowCount)
    }

    @Test
    fun rawJson_includesEveryExtraKey() = runTest {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Brown butter miso pasta https://www.instagram.com/reel/abc")
            .putExtra(Intent.EXTRA_SUBJECT, "@slowkitchen")
            .putExtra("com.instagram.share.ORIGIN", "reel_viewer")
            .putExtra("android.intent.extra.SHARED_AT", 1_761_549_060_000L)
            .putExtra("some.flag", true)
            .putExtra("some.list", arrayOf("a", "b"))
            .putExtra("some.empty", null as String?)
        val id = textCapture(intent)
        val vm = DetailViewModel(id, dao, service)

        val raw = vm.rawJson.first { it.isNotEmpty() }

        val keys = listOf(
            Intent.EXTRA_TEXT, Intent.EXTRA_SUBJECT, "com.instagram.share.ORIGIN",
            "android.intent.extra.SHARED_AT", "some.flag", "some.list", "some.empty",
        )
        val extras = Json.parseToJsonElement(raw).jsonObject.getValue("extras").jsonObject
        keys.forEach { k -> assertTrue("extras lacks $k: $raw", extras.containsKey(k)) }
        // The full record, as the desktop gets it, pretty-printed.
        val record = Json.parseToJsonElement(raw).jsonObject
        assertEquals(id, record.getValue("id").jsonPrimitive.content)
        listOf("schema_version", "intent", "source_hints", "device", "files", "note", "note_updated_at")
            .forEach { assertTrue("record lacks $it", record.containsKey(it)) }
        assertTrue("not pretty-printed: $raw", raw.contains("\n    \""))
        assertEquals(keys.size, vm.rawFieldCount.first { it > 0 })
    }
}
