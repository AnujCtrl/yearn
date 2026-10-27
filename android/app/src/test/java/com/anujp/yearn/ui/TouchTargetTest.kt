package com.anujp.yearn.ui

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.anujp.yearn.data.CaptureEntity
import com.anujp.yearn.data.SyncStatus
import com.anujp.yearn.ui.detail.DetailScreen
import com.anujp.yearn.ui.glass.YearnTheme
import com.anujp.yearn.ui.home.SaveRow
import com.anujp.yearn.ui.home.SavesList
import com.anujp.yearn.ui.home.SavesTwoPane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Retry and Copy are small text capsules; their touch areas must still be at least 48 dp tall. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class TouchTargetTest {
    private val now = ZonedDateTime.parse("2026-10-27T12:50:00+05:30[Asia/Kolkata]")
    private val reason = "Desktop rejected the file"
    private val failedRow = SaveRow("c1", "A failed save", "Chrome · 5 min", null, "Failed", reason, false, "Chrome", true)

    private val failedCapture = CaptureEntity(
        id = "c1", capturedAt = now.toOffsetDateTime().toString(), capturedAtMillis = now.toInstant().toEpochMilli(),
        baseRecordJson = "{}", sourceLabel = "Chrome", previewTitle = "A failed save", previewText = null,
        previewUrl = "https://example.com/a", thumbPath = null, note = null, noteUpdatedAt = null,
        noteNeedsSync = false, filesComplete = true, syncStatus = SyncStatus.FAILED, attempts = 0,
        lastError = reason, uploadedAt = null,
    )

    private fun idle() = repeat(30) { shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS) }

    private fun composeRoot(v: View): View? {
        if (v is RootForTest) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) composeRoot(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun nodes(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap { nodes(it) }

    private fun show(content: @Composable () -> Unit): View {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { YearnTheme(dark = false) { content() } }
        idle()
        return composeRoot(activity.window.decorView)!!
    }

    private fun all(root: View) = nodes((root as RootForTest).semanticsOwner.rootSemanticsNode)

    private fun described(root: View, label: String): SemanticsNode =
        all(root).first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true }

    private fun texts(root: View): List<String> =
        all(root).flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }

    /** The touch node's own size (boundsInRoot would also cut it to every ancestor's layout bounds). */
    private fun assertTall(root: View, label: String) {
        val minPx = 48 * root.resources.displayMetrics.density
        val h = described(root, label).size.height.toFloat()
        assertTrue("$label touch area is ${h}px, needs >= ${minPx}px", h >= minPx - 0.5f)
    }

    private fun tap(view: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0))
        idle()
    }

    @Test
    fun phoneList_retryHasA48dpTarget() {
        var retried = 0
        var opened = 0
        val root = show {
            SavesList(listOf(failedRow), 0, "", onSync = {}, onRetry = { retried++ }, onOpenSettings = {}, onOpen = { opened++ })
        }
        assertTall(root, "Retry upload")
        // The subtitle line itself stays text-high: the row is no taller than a thumbnail plus padding.
        val rowNode = all(root).first { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "A failed save" } == true }
        val density = root.resources.displayMetrics.density
        assertTrue("row grew to ${rowNode.boundsInRoot.height / density}dp", rowNode.boundsInRoot.height <= (52 + 20 + 1) * density)
        // A tap 20 dp below the word Retry's centre, outside the text but inside the target, retries.
        val b = described(root, "Retry upload").boundsInRoot
        tap(root, b.center.x, b.center.y + 20 * density)
        assertEquals(1, retried)
        assertEquals(0, opened)
    }

    @Test
    fun detail_retryHasA48dpTarget() {
        var retried = 0
        val root = show {
            DetailScreen(failedCapture, emptyList(), "{}", 1, pane = false, onBack = {}, onRetry = { retried++ }, onSaveNote = {}, now = { now })
        }
        assertTall(root, "Retry upload")
        val density = root.resources.displayMetrics.density
        val b = described(root, "Retry upload").boundsInRoot
        tap(root, b.center.x, b.center.y + 20 * density) // below the capsule, inside the target
        assertEquals(1, retried)
    }

    /** The inner screen's pane, tall enough that the opened raw block gets laid out in full. */
    @Test
    @Config(qualifiers = "w900dp-h2400dp-mdpi")
    fun detail_copyHasA48dpTarget() {
        val root = show {
            DetailScreen(failedCapture, emptyList(), "{\n  \"id\": \"c1\"\n}", 1, pane = true, onBack = {}, onRetry = {}, onSaveNote = {}, now = { now })
        }
        val rawRow = all(root).first { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Raw share" } == true &&
                n.config.getOrNull(SemanticsActions.OnClick) != null
        }
        rawRow.config[SemanticsActions.OnClick].action!!.invoke()
        idle()
        (root as RootForTest).measureAndLayoutForTest() // the opened block's first layout pass
        assertTall(root, "Copy raw share")
    }

    @Test
    fun sidebar_failedRowShowsTheReason() {
        var retried = 0
        var selected = 0
        val root = show {
            SavesTwoPane(listOf(failedRow), "c1", onSelect = { selected++ }, onSync = {}, onRetry = { retried++ }, onOpenSettings = {})
        }
        val t = texts(root)
        assertTrue("sidebar texts: $t", t.any { it.startsWith("Chrome · Failed: $reason") })
        assertTall(root, "Retry upload")
        val density = root.resources.displayMetrics.density
        val b = described(root, "Retry upload").boundsInRoot
        tap(root, b.center.x, b.center.y + 20 * density)
        assertEquals(1, retried)
        assertEquals(0, selected)
    }
}
