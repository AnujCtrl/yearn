package com.anujp.yearn.capture

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SharePreview(val title: String?, val text: String?, val url: String?, val sourceLabel: String)

data class ParsedShare(
    val intent: JsonObject,
    val extras: JsonObject,
    val clipData: JsonObject?,
    val sourceHints: JsonObject,
    val streamUris: List<Uri>,
    val preview: SharePreview,
)

/**
 * Turns a share intent into a lossless JSON description. Never throws: anything the app cannot
 * read is recorded as unreadable instead.
 */
object RawShareParser {
    private val URL_REGEX = Regex("""https?://[^\s<>"]+""")
    private const val TRAILING = ".,;:!?)]'\""

    fun parse(intent: Intent, hints: SourceHints): ParsedShare {
        val extrasBundle = runCatching { intent.extras }.getOrNull()
        val streamUris = collectStreamUris(intent, extrasBundle)
        return ParsedShare(
            intent = intentJson(intent),
            extras = extrasJson(intent, extrasBundle),
            clipData = clipJson(intent),
            sourceHints = hints.toJson(),
            streamUris = streamUris,
            preview = preview(intent, extrasBundle, hints, streamUris.isNotEmpty()),
        )
    }

    fun firstUrl(text: String): String? {
        val m = URL_REGEX.find(text) ?: return null
        return m.value.trimEnd { it in TRAILING }.ifEmpty { null }
    }

    private fun intentJson(intent: Intent): JsonObject = buildJsonObject {
        put("action", intent.action)
        put("type", intent.type)
        put("categories", JsonArray(intent.categories.orEmpty().map { JsonPrimitive(it) }))
        put("flags", intent.flags)
        put("data", intent.dataString)
    }

    private fun extrasJson(intent: Intent, bundle: Bundle?): JsonObject {
        if (bundle == null) {
            // intent.extras itself failed or there are none; distinguish by a second look.
            return runCatching { intent.extras }.fold(
                onSuccess = { JsonObject(emptyMap()) },
                onFailure = { unreadableTop(it) },
            )
        }
        return bundleJson(bundle)
    }

    private fun unreadableTop(t: Throwable) =
        JsonObject(mapOf("_unreadable" to JsonPrimitive(t.message ?: t.javaClass.name)))

    private fun bundleJson(bundle: Bundle): JsonObject {
        val keys = runCatching { bundle.keySet().toList() }.getOrElse { return unreadableTop(it) }
        val out = LinkedHashMap<String, JsonElement>()
        for (key in keys) {
            out[key] = try {
                @Suppress("DEPRECATION")
                typed(bundle.get(key))
            } catch (t: Throwable) {
                buildJsonObject {
                    put("type", "unreadable")
                    put("error", t.message ?: t.javaClass.name)
                }
            }
        }
        return JsonObject(out)
    }

    private fun typed(v: Any?): JsonObject = when {
        v == null -> entry("null", JsonNull)
        v is String -> entry("String", JsonPrimitive(v))
        v is CharSequence -> buildJsonObject {
            put("type", "CharSequence")
            put("value", v.toString())
            if (v is Spanned) put("html", Html.toHtml(v, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE))
        }
        v is Array<*> && v.javaClass.componentType == String::class.java ->
            entry("String[]", JsonArray(v.map { if (it == null) JsonNull else JsonPrimitive(it as String) }))
        v is ArrayList<*> && v.isNotEmpty() && v.all { it is String } ->
            entry("ArrayList<String>", JsonArray(v.map { JsonPrimitive(it as String) }))
        v is ArrayList<*> && v.isNotEmpty() && v.all { it is Uri } ->
            entry("ArrayList<Uri>", JsonArray(v.map { JsonPrimitive(it.toString()) }))
        v is Int -> entry("Int", JsonPrimitive(v))
        v is Long -> entry("Long", JsonPrimitive(v))
        v is Boolean -> entry("Boolean", JsonPrimitive(v))
        v is Double -> entry("Double", if (v.isFinite()) JsonPrimitive(v) else JsonPrimitive(v.toString()))
        v is Float -> entry("Float", if (v.isFinite()) JsonPrimitive(v) else JsonPrimitive(v.toString()))
        v is Uri -> entry("Uri", JsonPrimitive(v.toString()))
        v is Bundle -> entry("Bundle", bundleJson(v))
        else -> entry(
            "unknown",
            buildJsonObject {
                put("class_name", v.javaClass.name)
                put("to_string", runCatching { v.toString() }.getOrElse { "<toString failed: ${it.message}>" })
            },
        )
    }

    private fun entry(type: String, value: JsonElement) = JsonObject(
        mapOf("type" to JsonPrimitive(type), "value" to value)
    )

    private fun clipJson(intent: Intent): JsonObject? {
        val clip: ClipData = runCatching { intent.clipData }.getOrNull() ?: return null
        val d = clip.description
        val items = (0 until clip.itemCount).map { i ->
            val item = clip.getItemAt(i)
            buildJsonObject {
                put("text", runCatching { item.text?.toString() }.getOrNull())
                put("html_text", runCatching { item.htmlText }.getOrNull())
                put("uri", runCatching { item.uri?.toString() }.getOrNull())
                put("intent", runCatching { item.intent?.toUri(Intent.URI_INTENT_SCHEME) }.getOrNull())
            }
        }
        return buildJsonObject {
            put("description", buildJsonObject {
                put("mime_types", JsonArray((0 until d.mimeTypeCount).map { JsonPrimitive(d.getMimeType(it)) }))
                put("label", d.label?.toString())
            })
            put("items", JsonArray(items))
        }
    }

    private fun collectStreamUris(intent: Intent, bundle: Bundle?): List<Uri> {
        val out = LinkedHashSet<Uri>()
        @Suppress("DEPRECATION")
        val stream = runCatching { bundle?.get(Intent.EXTRA_STREAM) }.getOrNull()
        when (stream) {
            is Uri -> out.add(stream)
            is List<*> -> stream.filterIsInstance<Uri>().forEach { out.add(it) }
        }
        runCatching { intent.clipData }.getOrNull()?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { out.add(it) }
        }
        return out.toList()
    }

    private fun preview(intent: Intent, bundle: Bundle?, hints: SourceHints, hasStreams: Boolean): SharePreview {
        fun cs(key: String): String? = runCatching {
            @Suppress("DEPRECATION")
            (bundle?.get(key) as? CharSequence)?.toString()
        }.getOrNull()

        val title = cs(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() } ?: cs(Intent.EXTRA_TITLE)?.takeIf { it.isNotBlank() }
        val raw = cs(Intent.EXTRA_TEXT)
        val url = raw?.let { firstUrl(it) }
        val text = raw?.let { URL_REGEX.replaceFirst(it, "") }?.trim()?.ifEmpty { null }

        val label = url?.let { hostOf(it) }
            ?: hints.launchedFromPackage
            ?: hints.referrer?.let { referrerLabel(it) }
            ?: when {
                hasStreams -> "Shared files"
                text != null -> "Shared text"
                else -> "Shared something"
            }
        return SharePreview(title, text, url, label)
    }

    private fun hostOf(url: String): String? =
        runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.")?.ifEmpty { null }

    private fun referrerLabel(referrer: String): String? {
        val uri = runCatching { Uri.parse(referrer) }.getOrNull() ?: return null
        return uri.host?.ifEmpty { null }
    }
}
