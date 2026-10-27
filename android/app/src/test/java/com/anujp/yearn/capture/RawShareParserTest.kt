package com.anujp.yearn.capture

import android.content.ClipData
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RawShareParserTest {
    private val noHints = SourceHints(null, null, null, null, null, null)

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content

    @Test
    fun instagramLink_textPlainUrlOnly() {
        val u = "https://www.instagram.com/reel/abc/?igsh=x"
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, u)
        val p = RawShareParser.parse(i, noHints)
        assertEquals(u, p.preview.url)
        assertEquals("instagram.com", p.preview.sourceLabel)
        assertNull(p.preview.text)
        assertEquals("String", p.extras.getValue(Intent.EXTRA_TEXT).jsonObject.str("type"))
    }

    @Test
    fun xPost_wordsPlusUrl() {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Liquid glass in Compose https://x.com/shaderjune/status/1.")
        val p = RawShareParser.parse(i, noHints)
        assertEquals("https://x.com/shaderjune/status/1", p.preview.url)
        assertEquals("Liquid glass in Compose", p.preview.text)
    }

    @Test
    fun chrome_subjectBecomesTitle() {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "A page")
            .putExtra(Intent.EXTRA_TEXT, "https://example.com/a")
        assertEquals("A page", RawShareParser.parse(i, noHints).preview.title)
    }

    @Test
    fun galleryMultiple_dedupesStreamAndClipData() {
        val uris = arrayListOf(Uri.parse("content://m/1"), Uri.parse("content://m/2"), Uri.parse("content://m/3"))
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        val clip = ClipData.newRawUri("l", uris[0])
        clip.addItem(ClipData.Item(uris[1]))
        clip.addItem(ClipData.Item(uris[2]))
        i.clipData = clip
        val p = RawShareParser.parse(i, noHints)
        assertEquals(uris, p.streamUris)
        assertEquals("Shared files", p.preview.sourceLabel)
        assertEquals("ArrayList<Uri>", p.extras.getValue(Intent.EXTRA_STREAM).jsonObject.str("type"))
    }

    @Test
    fun styledText_keepsHtml() {
        val s = SpannableString("bold")
        s.setSpan(StyleSpan(Typeface.BOLD), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s)
        val e = RawShareParser.parse(i, noHints).extras.getValue(Intent.EXTRA_TEXT).jsonObject
        assertEquals("CharSequence", e.str("type"))
        assertTrue(e.str("html").contains("<b>"))
    }

    @Test
    fun nestedBundleAndUnknownParcelable() {
        val inner = Bundle().apply { putInt("n", 3); putParcelable("r", Rect(1, 2, 3, 4)) }
        val i = Intent(Intent.ACTION_SEND).setType("*/*").putExtra("b", inner)
        val b = RawShareParser.parse(i, noHints).extras.getValue("b").jsonObject
        assertEquals("Bundle", b.str("type"))
        val v = b.getValue("value").jsonObject
        assertEquals("Int", v.getValue("n").jsonObject.str("type"))
        val r = v.getValue("r").jsonObject
        assertEquals("unknown", r.str("type"))
        assertEquals("android.graphics.Rect", r.getValue("value").jsonObject.str("class_name"))
    }

    @Test
    fun emptyShare_stillParses() {
        val p = RawShareParser.parse(Intent(Intent.ACTION_SEND).setType("*/*"), noHints)
        assertTrue(p.extras.isEmpty())
        assertNull(p.clipData)
        assertEquals("Shared something", p.preview.sourceLabel)
    }
}
