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
import kotlinx.serialization.json.booleanOrNull
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

    suspend fun health() {
        call(request("v1", "health").get().build())
    }

    suspend fun status(id: String): RemoteStatus {
        val body = call(request("v1", "captures", id).get().build())
        return try {
            val o = Json.parseToJsonElement(body).jsonObject
            val staged = o["staged"]?.jsonObject.orEmpty().entries.associate { (k, v) -> k.toInt() to v.jsonPrimitive.content }
            RemoteStatus(o["stored"]?.jsonPrimitive?.booleanOrNull ?: false, staged)
        } catch (e: Exception) {
            throw ReceiverError.Rejected("bad_response", null)
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
        return runCatching { Json.parseToJsonElement(body).jsonObject["path"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: ""
    }

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
