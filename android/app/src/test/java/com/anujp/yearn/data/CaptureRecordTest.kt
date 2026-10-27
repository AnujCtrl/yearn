package com.anujp.yearn.data

import android.net.Uri
import com.anujp.yearn.capture.ParsedShare
import com.anujp.yearn.capture.SharePreview
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CaptureRecordTest {
    private val parsed = ParsedShare(
        intent = buildJsonObject { put("action", "android.intent.action.SEND") },
        extras = buildJsonObject { put("android.intent.extra.TEXT", "hi") },
        clipData = null,
        sourceHints = buildJsonObject { put("referrer", "android-app://x") },
        streamUris = emptyList<Uri>(),
        preview = SharePreview(null, "hi", null, "x"),
    )

    private fun f(i: Int, state: FileState, sha: String? = null, err: String? = null) = FileEntity(
        "c", i, "content://x/$i", if (i == 2) null else "name$i", "image/png", 10L, null, sha, err, state,
    )

    @Test
    fun full_matchesReceiverContract() {
        val at = Timestamps.format(ZonedDateTime.of(2026, 10, 26, 10, 0, 5, 0, ZoneOffset.ofHoursMinutes(5, 30)))
        val base = CaptureRecord.base("c", at, parsed, buildJsonObject { put("model", "Pixel") })
        assertEquals(1, base["schema_version"]!!.jsonPrimitive.int)
        assertTrue(base["clip_data"] is JsonNull)

        val full = CaptureRecord.full(
            base,
            listOf(
                f(2, FileState.ERROR, err = "boom"),
                f(0, FileState.DONE, sha = "ab".repeat(32)),
                f(1, FileState.PENDING),
            ),
            note = null, noteUpdatedAt = null,
        )
        assertEquals(1, full["schema_version"]!!.jsonPrimitive.int)
        assertEquals("c", full["id"]!!.jsonPrimitive.content)
        assertTrue(Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d[+-]\d\d:\d\d""").matches(full["captured_at"]!!.jsonPrimitive.content))
        assertEquals("2026-10-26T10:00:05+05:30", full["captured_at"]!!.jsonPrimitive.content)
        assertTrue(full["note"] is JsonNull)
        assertTrue(full["note_updated_at"] is JsonNull)

        val files = full["files"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf(0, 1, 2), files.map { it["index"]!!.jsonPrimitive.int })
        for (e in files) {
            assertTrue(e["display_name"]!!.jsonPrimitive.isString)
            val hasSha = "sha256" in e
            val hasErr = "error" in e
            assertTrue(hasSha xor hasErr)
            if (hasSha) assertTrue(Regex("[0-9a-f]{64}").matches(e["sha256"]!!.jsonPrimitive.content))
            else assertTrue(e["error"]!!.jsonPrimitive.content.isNotEmpty())
        }
        assertEquals("Copy not finished", files[1]["error"]!!.jsonPrimitive.content)
        assertEquals("", files[2]["display_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun full_noteAndNullMimeSize() {
        val base = CaptureRecord.base("c", "2026-10-26T10:00:00+00:00", parsed, JsonObject(emptyMap()))
        val file = f(0, FileState.DONE, sha = "0".repeat(64)).copy(mimeType = null, size = null)
        val full = CaptureRecord.full(base, listOf(file), "n", "2026-10-26T11:00:00+00:00")
        val e = full["files"]!!.jsonArray[0].jsonObject
        assertTrue(e["mime_type"] is JsonNull)
        assertTrue(e["size"] is JsonNull)
        assertEquals("n", full["note"]!!.jsonPrimitive.content)
        assertFalse("error" in e)
    }
}
