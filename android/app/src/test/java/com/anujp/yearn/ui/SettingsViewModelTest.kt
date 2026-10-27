package com.anujp.yearn.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.anujp.yearn.FakeSyncScheduler
import com.anujp.yearn.data.CaptureEntity
import com.anujp.yearn.data.SyncStatus
import com.anujp.yearn.data.YearnDatabase
import com.anujp.yearn.sync.SyncScheduler
import com.anujp.yearn.sync.ReceiverClient
import com.anujp.yearn.sync.SettingsStore
import com.anujp.yearn.sync.XorBox
import com.anujp.yearn.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val scheduler = FakeSyncScheduler()
    private lateinit var db: YearnDatabase
    private val dao get() = db.captureDao()
    private val http = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.start()
        db = Room.inMemoryDatabaseBuilder(context, YearnDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        db.close()
        Dispatchers.resetMain()
    }

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun testConnection_mapsErrorsToCopy() = runTest {
        var client: ReceiverClient? = null
        val vm = SettingsViewModel(SettingsStore(context, XorBox()), dao = dao, clientFactory = { client }, power = { true }, scheduler = scheduler)

        // No token yet: the app's factory gives no client.
        vm.testConnection().join()
        assertEquals("Desktop not set up yet", vm.connection.value)

        client = ReceiverClient.create(server.url("/"), "tok", http)
        server.enqueue(json(200, """{"ok":true}"""))
        vm.testConnection().join()
        assertEquals("Reachable", vm.connection.value)
        assertEquals("/v1/health", server.takeRequest().path)

        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        vm.testConnection().join()
        assertEquals("Desktop didn't accept the pairing token", vm.connection.value)

        server.enqueue(json(503, """{"error":"busy"}"""))
        vm.testConnection().join()
        assertEquals("Couldn't reach your desktop", vm.connection.value)

        server.enqueue(json(404, """{"error":"not_found"}"""))
        vm.testConnection().join()
        assertEquals("Couldn't reach your desktop", vm.connection.value)

        // Nothing listening at all.
        val deadPort = ServerSocket(0).use { it.localPort }
        client = ReceiverClient.create(server.url("/").newBuilder().port(deadPort).build(), "tok", http)
        vm.testConnection().join()
        assertEquals("Couldn't reach your desktop", vm.connection.value)
        assertFalse(vm.testing.value)
    }

    @Test
    fun setToken_masksSavesAndEnqueues() = runTest {
        val store = SettingsStore(context, XorBox())
        val vm = SettingsViewModel(store, dao = dao, clientFactory = { null }, power = { true }, scheduler = scheduler)
        assertEquals(false, vm.hasToken.first { it != null })
        assertNull(vm.tokenMask.value)

        vm.setToken("  abcdefgh1234wxyz \n").join()

        assertEquals("abcdefgh1234wxyz", store.token())
        assertEquals("••••wxyz", vm.tokenMask.value)
        assertEquals(true, vm.hasToken.value)
        assertEquals(1, scheduler.nowCount)

        vm.setToken("   ").join() // blank never replaces a saved token
        assertEquals("abcdefgh1234wxyz", store.token())
        assertEquals(1, scheduler.nowCount)
    }

    @Test
    fun setAddress_normalizesSavesAndEnqueues() = runTest {
        val store = SettingsStore(context, XorBox())
        val vm = SettingsViewModel(store, dao = dao, clientFactory = { null }, power = { true }, scheduler = scheduler)

        vm.setAddress("desk.tail1234.ts.net:8787 ").join()
        assertEquals("http://desk.tail1234.ts.net:8787", store.baseUrl.first())
        assertEquals(1, scheduler.nowCount)
        assertNull(vm.addressError.value)

        vm.setAddress("http://bad host").join()
        assertEquals("http://desk.tail1234.ts.net:8787", store.baseUrl.first())
        assertEquals("That isn't an address yearn can reach", vm.addressError.value)
        assertEquals(1, scheduler.nowCount)
    }

    @Test
    fun batteryUnrestricted_followsPowerOnRefresh() = runTest {
        var unrestricted = false
        val vm = SettingsViewModel(SettingsStore(context, XorBox()), dao = dao, clientFactory = { null }, power = { unrestricted }, scheduler = scheduler)
        assertFalse(vm.batteryUnrestricted.value)
        unrestricted = true
        vm.refreshBattery()
        assertTrue(vm.batteryUnrestricted.value)
    }

    private fun failed(id: String, millis: Long, reason: String) = CaptureEntity(
        id = id, capturedAt = "2026-10-26T10:00:00+05:30", capturedAtMillis = millis, baseRecordJson = "{}",
        sourceLabel = "Chrome", previewTitle = null, previewText = null, previewUrl = null, thumbPath = null,
        note = null, noteUpdatedAt = null, noteNeedsSync = false, filesComplete = true,
        syncStatus = SyncStatus.FAILED, attempts = 4, lastError = reason, uploadedAt = null,
    )

    private fun statusOf(id: String): String =
        db.openHelper.readableDatabase.query("SELECT syncStatus FROM captures WHERE id = ?", arrayOf<Any>(id)).use {
            it.moveToFirst()
            it.getString(0)
        }

    /** Records the token-failed row's status at the moment a sync is enqueued. */
    private inner class RecordingScheduler : SyncScheduler {
        val seen = mutableListOf<String>()
        override fun enqueueNow() { seen += statusOf("tok") }
        override fun ensureDaily() = Unit
    }

    private suspend fun seedFailures() {
        dao.insertCapture(failed("tok", 1000, "Desktop didn't accept the pairing token"), emptyList())
        dao.insertCapture(failed("rej", 2000, "Desktop rejected the file"), emptyList())
    }

    private suspend fun assertTokenFailureRetried(rec: RecordingScheduler) {
        val tok = dao.get("tok")!!
        assertEquals(SyncStatus.WAITING, tok.syncStatus)
        assertEquals(0, tok.attempts)
        assertNull(tok.lastError)
        assertEquals(SyncStatus.FAILED, dao.get("rej")!!.syncStatus)
        assertEquals(listOf("WAITING"), rec.seen) // reset before the sync was asked for
    }

    @Test
    fun setToken_retriesCapturesTheOldTokenFailed() = runTest {
        seedFailures()
        val rec = RecordingScheduler()
        val vm = SettingsViewModel(SettingsStore(context, XorBox()), dao = dao, clientFactory = { null }, power = { true }, scheduler = rec)
        vm.setToken("abcdefgh1234wxyz").join()
        assertTokenFailureRetried(rec)
    }

    @Test
    fun setAddress_retriesCapturesTheOldTokenFailed() = runTest {
        seedFailures()
        val rec = RecordingScheduler()
        val vm = SettingsViewModel(SettingsStore(context, XorBox()), dao = dao, clientFactory = { null }, power = { true }, scheduler = rec)
        vm.setAddress("desk.tail1234.ts.net:8787").join()
        assertTokenFailureRetried(rec)
    }
}
