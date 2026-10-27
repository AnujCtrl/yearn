package com.anujp.yearn.capture

import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.anujp.yearn.TestYearnApp
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = TestYearnApp::class)
class ShareActivityTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestYearnApp>()

    private fun shareIntent(): Intent =
        Intent(app, ShareActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "look at this https://example.com/a")

    private fun streamIntent(): Intent =
        Intent(app, ShareActivity::class.java).setAction(Intent.ACTION_SEND).setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test.provider/img/1"))

    /** Room and the copier run on real background threads; pump the main looper until [done]. */
    private fun idleUntil(what: String, timeoutMs: Long = 5_000, done: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (done()) return
            if (System.currentTimeMillis() > end) fail("timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    private fun ActivityScenario<ShareActivity>.state(): SheetState {
        var s: SheetState? = null
        onActivity { s = it.viewModel.state.value }
        return s!!
    }

    @Test
    fun textShare_createsOneCaptureAndFinishes() {
        val scenario = ActivityScenario.launch<ShareActivity>(shareIntent())
        lateinit var activity: ShareActivity
        scenario.onActivity { activity = it }
        idleUntil("Saved") { activity.viewModel.state.value is SheetState.Saved }
        assertFalse(activity.isFinishing)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))

        assertEquals(1, app.captureCount())
        assertTrue(activity.isFinishing)
        assertEquals(SheetState.Finished, activity.viewModel.state.value)
    }

    @Test
    fun launchedFromHistory_capturesNothing() {
        val intent = shareIntent().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        val scenario = ActivityScenario.launch<ShareActivity>(intent)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))

        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        assertEquals(0, app.captureCount())
    }

    @Test
    fun recreate_keepsSingleCapture() {
        val gate = CompletableDeferred<Unit>()
        app.copier.gate = gate
        val scenario = ActivityScenario.launch<ShareActivity>(streamIntent())
        idleUntil("Saving") { scenario.state() is SheetState.Saving }
        assertEquals(1, app.captureCount())

        scenario.recreate()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(scenario.state() is SheetState.Saving)

        gate.complete(Unit)
        idleUntil("Saved") { scenario.state() is SheetState.Saved }
        assertEquals(1, app.captureCount())
        assertEquals(listOf(0), app.copier.calls.toList())
    }

    @Test
    fun backWhileSaving_doesNotFinish() {
        val gate = CompletableDeferred<Unit>()
        app.copier.gate = gate
        val scenario = ActivityScenario.launch<ShareActivity>(streamIntent())
        idleUntil("Saving") { scenario.state() is SheetState.Saving }

        lateinit var activity: ShareActivity
        scenario.onActivity {
            activity = it
            it.onBackPressedDispatcher.onBackPressed()
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))

        assertFalse(activity.isFinishing)
        val state = activity.viewModel.state.value
        assertTrue("state was $state", state is SheetState.Saving)
        assertTrue((state as SheetState.Saving).stillSaving)

        gate.complete(Unit)
        idleUntil("Saved") { activity.viewModel.state.value is SheetState.Saved }
    }
}
