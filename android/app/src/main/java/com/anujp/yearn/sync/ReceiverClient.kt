package com.anujp.yearn.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.boolean
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

sealed class ReceiverError : Exception() {
    /** An [IOException] or a 5xx (including 507 disk_full): worth retrying later. */
    class Unreachable(override val cause: Throwable) : ReceiverError() {
        override val message: String get() = cause.message ?: cause.javaClass.simpleName
    }

    /** 401: the pairing token is wrong. */
    object Unauthorized : ReceiverError() {
        private fun readResolve(): Any = Unauthorized
        override val message: String get() = "unauthorized"
    }

    /** 422 or any other 4xx. */
    class Rejected(val error: String, val index: Int?) : ReceiverError() {
        override val message: String get() = if (index != null) "$error (file $index)" else error
    }

    /** 404 on the note PUT: the desktop has no stored capture with that id. */
    object NotStored : ReceiverError() {
        private fun readResolve(): Any = NotStored
        override val message: String get() = "not_stored"
    }
}

data class RemoteStatus(val stored: Boolean, val staged: Map<Int, String>)

/** Phone side of the receiver's wire contract v1. Streams file bodies; never buffers a whole file. */
class ReceiverClient(private val baseUrl: HttpUrl, private val token: String, private val http: OkHttpClient) {
    private val jsonType = "application/json".toMediaType()

    companion object {
        /**
         * The receiver re-hashes and fsyncs before answering a commit, so reads need real headroom.
         * Cleartext is only safe inside the tailnet: [TailnetDns] refuses every other address (names and IP
         * literals alike), no proxy is used, and redirects are never followed, so the Bearer token cannot be
         * sent anywhere else.
         */
        fun httpClient(): OkHttpClient {
            val dns = TailnetDns()
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .dns(dns)
                .addInterceptor(TailnetDns.LiteralGuard(dns))
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }

        /** Trims the token; null (treated as "not set up") if it is blank or not a legal header value. */
        fun create(baseUrl: HttpUrl, token: String, http: OkHttpClient = httpClient()): ReceiverClient? {
            val t = token.trim()
            if (t.isEmpty() || t.any { it.code !in 0x21..0x7e }) return null
            return ReceiverClient(baseUrl, t, http)
        }
    }

    suspend fun health() {
        call(request("v1", "health").get().build())
    }

    suspend fun status(id: String): RemoteStatus {
        val body = call(request("v1", "captures", id).get().build())
        // A 2xx that is not the receiver's answer means something else is listening there: wait and retry.
        return try {
            val o = Json.parseToJsonElement(body).jsonObject
            val stored = (o["stored"] as JsonPrimitive).also { require(!it.isString) }.boolean
            val staged = (o["staged"] as JsonObject).entries.associate { (k, v) ->
                val p = v as JsonPrimitive
                require(p.isString)
                k.toInt() to p.content
            }
            RemoteStatus(stored, staged)
        } catch (e: Exception) {
            throw notTheReceiver("status", e)
        }
    }

    suspend fun putFile(id: String, index: Int, file: File, sha256: String, mimeType: String? = null) {
        val req = request("v1", "captures", id, "files", index.toString())
            .header("X-Sha256", sha256)
            .put(file.asRequestBody(mimeType?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()))
            .build()
        call(req)
    }

    /** Returns the stored path reported by the desktop. */
    suspend fun commit(id: String, record: JsonObject): String {
        val req = request("v1", "captures", id, "commit")
            .post(record.toString().toRequestBody(jsonType))
            .build()
        val body = call(req)
        // Only an explicit stored:true with a path confirms the desktop has it; anything else is retried.
        return try {
            val o = Json.parseToJsonElement(body).jsonObject
            val stored = o["stored"] as JsonPrimitive
            val path = o["path"] as JsonPrimitive
            require(!stored.isString && stored.boolean && path.isString)
            path.content
        } catch (e: Exception) {
            throw notTheReceiver("commit", e)
        }
    }

    private fun notTheReceiver(what: String, e: Exception) =
        ReceiverError.Unreachable(IOException("unexpected $what response", e))

    suspend fun putNote(id: String, note: String, noteUpdatedAt: String) {
        val payload = JsonObject(mapOf("note" to JsonPrimitive(note), "note_updated_at" to JsonPrimitive(noteUpdatedAt)))
        val req = request("v1", "captures", id, "note").put(payload.toString().toRequestBody(jsonType)).build()
        call(req, notFoundIsNotStored = true)
    }

    private fun request(vararg segments: String): Request.Builder {
        val url = baseUrl.newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()
        return Request.Builder().url(url).header("Authorization", "Bearer $token")
    }

    /** Runs the call on IO, maps every failure to a [ReceiverError], and returns the body text. */
    private suspend fun call(req: Request, notFoundIsNotStored: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val call = http.newCall(req)
            val handle = coroutineContext.job.invokeOnCompletion { if (it != null) call.cancel() }
            try {
                call.execute().use { resp ->
                    val text = resp.body.string()
                    val code = resp.code
                    when {
                        resp.isSuccessful -> text
                        code == 401 -> throw ReceiverError.Unauthorized
                        // Redirects are never followed; the receiver never sends one, so whatever did is not it.
                        code in 300..399 -> throw ReceiverError.Unreachable(IOException("HTTP $code redirect not followed"))
                        code >= 500 -> throw ReceiverError.Unreachable(IOException("HTTP $code ${errorOf(text) ?: ""}".trim()))
                        code == 404 && notFoundIsNotStored -> throw ReceiverError.NotStored
                        else -> throw ReceiverError.Rejected(errorOf(text) ?: "http_$code", indexOf(text))
                    }
                }
            } catch (e: IOException) {
                throw ReceiverError.Unreachable(e)
            } finally {
                handle.dispose()
            }
        }

    private fun parse(text: String): JsonObject? =
        runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()

    private fun errorOf(text: String): String? = parse(text)?.get("error")?.jsonPrimitive?.contentOrNull

    private fun indexOf(text: String): Int? = parse(text)?.get("index")?.jsonPrimitive?.intOrNull
}
