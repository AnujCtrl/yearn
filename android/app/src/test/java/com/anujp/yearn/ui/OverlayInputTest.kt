package com.anujp.yearn.ui

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.anujp.yearn.data.CaptureEntity
import com.anujp.yearn.data.SyncStatus
import com.anujp.yearn.ui.detail.DetailScreen
import com.anujp.yearn.ui.home.SaveRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * On the phone the list stays composed under the detail: touches on the detail's media area (which
 * has nothing clickable for a link-only save) must not reach the hidden rows, and TalkBack must not
 * find them either.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class OverlayInputTest {
    private val now = ZonedDateTime.parse("2026-10-27T12:50:00+05:30[Asia/Kolkata]")

    private fun row(i: Int) = SaveRow("c$i", "Save number $i", "example.com · 5 min", null, "Uploaded", null, false, "example.com", true)

    /** A link-only save: no files, no thumbnail, so the media area is just the glow. */
    private fun capture(id: String) = CaptureEntity(
        id = id, capturedAt = now.toOffsetDateTime().toString(), capturedAtMillis = now.toInstant().toEpochMilli(),
        baseRecordJson = "{}", sourceLabel = "example.com", previewTitle = "Title of $id", previewText = null,
        previewUrl = "https://example.com/$id", thumbPath = null, note = null, noteUpdatedAt = null,
        noteNeedsSync = false, filesComplete = true, syncStatus = SyncStatus.UPLOADED, attempts = 0,
        lastError = null, uploadedAt = null,
    )

    private fun idle() = repeat(30) { shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS) }

    private fun composeRoot(v: View): View? {
        if (v is RootForTest) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) composeRoot(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun nodes(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap { nodes(it) }

    private fun texts(root: View): List<String> =
        nodes((root as RootForTest).semanticsOwner.rootSemanticsNode)
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }

    private fun tap(view: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0))
        idle()
    }

    @Test
    fun tapOnDetailMedia_neverReachesTheHiddenList() {
        val rows = (1..6).map(::row)
        var shown: String? = null
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            YearnRoot(
                rows, 0, "", needsSetup = false, onSync = {}, onRetry = {},
                detail = { id, pane, start, back ->
                    shown = id
                    DetailScreen(capture(id), emptyList(), "{}", 0, pane, back, {}, {}, paneStart = start, now = { now })
                },
                settings = {},
            )
        }
        idle()
        val root = composeRoot(activity.window.decorView)!!

        // Open the last save with a real tap on its row.
        val last = nodes((root as RootForTest).semanticsOwner.rootSemanticsNode)
            .first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "Save number 6" } == true }
        val b = last.boundsInRoot
        tap(root, b.center.x, b.center.y)
        assertEquals("c6", shown)

        // A tap in the media area, where a list row sits underneath.
        tap(root, root.width * 0.5f, root.height * 0.2f)
        assertEquals("the tap went through to a hidden row", "c6", shown)

        // TalkBack sees the detail, not the rows under it.
        val visible = texts(root)
        assertTrue(visible.contains("Title of c6"))
        assertFalse("hidden rows are in the semantics tree: $visible", visible.any { it.startsWith("Save number") })
    }
}
