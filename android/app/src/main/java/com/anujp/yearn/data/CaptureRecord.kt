package com.anujp.yearn.data

import com.anujp.yearn.capture.ParsedShare
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The versioned JSON record the desktop receiver validates (schema_version 1). */
object CaptureRecord {
    const val SCHEMA_VERSION = 1
    const val COPY_NOT_FINISHED = "Copy not finished"

    fun base(id: String, capturedAt: String, parsed: ParsedShare, device: JsonObject): JsonObject =
        JsonObject(
            linkedMapOf(
                "schema_version" to JsonPrimitive(SCHEMA_VERSION),
                "id" to JsonPrimitive(id),
                "captured_at" to JsonPrimitive(capturedAt),
                "intent" to parsed.intent,
                "extras" to parsed.extras,
                "clip_data" to (parsed.clipData ?: JsonNull),
                "source_hints" to parsed.sourceHints,
                "device" to device,
            ),
        )

    fun full(base: JsonObject, files: List<FileEntity>, note: String?, noteUpdatedAt: String?): JsonObject {
        val out = LinkedHashMap<String, JsonElement>(base)
        out["files"] = JsonArray(files.sortedBy { it.index }.map(::fileJson))
        out["note"] = note?.let { JsonPrimitive(it) } ?: JsonNull
        out["note_updated_at"] = noteUpdatedAt?.let { JsonPrimitive(it) } ?: JsonNull
        return JsonObject(out)
    }

    private fun fileJson(f: FileEntity): JsonObject {
        val m = LinkedHashMap<String, JsonElement>()
        m["index"] = JsonPrimitive(f.index)
        m["display_name"] = JsonPrimitive(f.displayName ?: "")
        m["mime_type"] = f.mimeType?.let { JsonPrimitive(it) } ?: JsonNull
        m["size"] = f.size?.let { JsonPrimitive(it) } ?: JsonNull
        when {
            f.state == FileState.DONE && f.sha256 != null -> m["sha256"] = JsonPrimitive(f.sha256)
            f.state == FileState.PENDING -> m["error"] = JsonPrimitive(COPY_NOT_FINISHED)
            else -> m["error"] = JsonPrimitive(f.error?.ifEmpty { null } ?: "Copy failed")
        }
        return JsonObject(m)
    }
}
