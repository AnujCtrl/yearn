package com.anujp.yearn.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.CaptureEntity
import com.anujp.yearn.data.FileEntity
import com.anujp.yearn.data.FileState
import com.anujp.yearn.data.SyncStatus
import com.anujp.yearn.data.YearnDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Mimics the receiver's wire contract v1 with just enough state for requests to flow. */
class ReceiverDispatcher(private val token: String = "tok") : Dispatcher() {
    val staged = LinkedHashMap<String, MutableMap<Int, String>>()
    val storedIds = HashSet<String>()
    val log = java.util.Collections.synchronizedList(mutableListOf<String>())
    val commits = java.util.Collections.synchronizedList(mutableListOf<JsonObject>())
    val notes = java.util.Collections.synchronizedList(mutableListOf<JsonObject>())
    @Volatile var putHashMismatch = false
    @Volatile var failStatusFor: String? = null
    @Volatile var commitReject: String? = null
    @Volatile var onCommit: (() -> Unit)? = null

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    override fun dispatch(request: RecordedRequest): MockResponse {
        log += "${request.method} ${request.path}"
        if (request.getHeader("Authorization") != "Bearer $token") return json(401, """{"error":"unauthorized"}""")
        val parts = request.path!!.trim('/').split('/')
        if (parts == listOf("v1", "health")) return json(200, """{"service":"yearn-receiver","api":1}""")
        if (parts.size < 3 || parts[0] != "v1" || parts[1] != "captures") return json(404, """{"error":"not_found"}""")
        val id = parts[2]
        return when {
            parts.size == 3 && request.method == "GET" -> {
                if (id == failStatusFor) return json(503, """{"error":"busy"}""")
                val st = staged[id].orEmpty().entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
                val stored = id in storedIds
                json(200, """{"id":"$id","stored":$stored,"staged":{${if (stored) "" else st}}}""")
            }
            parts.size == 5 && parts[3] == "files" && request.method == "PUT" -> {
                val idx = parts[4].toInt()
                val claimed = request.getHeader("X-Sha256")!!
                val actual = sha256(request.body.readByteArray())
                if (id in storedIds) return json(200, """{"index":$idx,"sha256":"$claimed"}""")
                if (putHashMismatch || actual != claimed) return json(422, """{"error":"hash_mismatch","index":$idx}""")
                staged.getOrPut(id) { LinkedHashMap() }[idx] = claimed
                json(201, """{"index":$idx,"sha256":"$claimed"}""")
            }
            parts.size == 4 && parts[3] == "commit" && request.method == "POST" -> {
                onCommit?.invoke()
                commitReject?.let { return json(422, """{"error":"$it"}""") }
                commits += Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                storedIds += id
                json(200, """{"stored":true,"path":"captures/2026/10/$id"}""")
            }
            parts.size == 4 && parts[3] == "note" && request.method == "PUT" -> {
                if (id !in storedIds) return json(404, """{"error":"not_stored"}""")
                notes += Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                json(200, """{"ok":true}""")
            }
            else -> json(404, """{"error":"not_found"}""")
        }
    }

    companion object {
        fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    }
}

@RunWith(RobolectricTestRunner::class)
class CaptureUploaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: YearnDatabase
    private lateinit var dao: CaptureDao
    private lateinit var server: MockWebServer
    private lateinit var receiver: ReceiverDispatcher
    private val now = ZonedDateTime.of(2026, 10, 26, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30))
    private val id = "11111111-2222-4333-8444-555555555555"

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, YearnDatabase::class.java).allowMainThreadQueries().build()
        dao = db.captureDao()
        receiver = ReceiverDispatcher()
        server = MockWebServer().apply { dispatcher = receiver; start() }
    }

    @After
    fun tearDown() {
        db.close()
        runCatching { server.shutdown() }
    }

    private fun client(token: String = "tok") = ReceiverClient(server.url("/"), token, OkHttpClient())

    private fun uploader(c: ReceiverClient? = client()) = CaptureUploader(dao, { c }, { now })

    /** Streams [size] bytes to disk and hashes while writing, so no big array exists. */
    private fun makeFile(name: String, size: Int): Pair<File, String> {
        val f = tmp.newFile(name)
        val md = MessageDigest.getInstance("SHA-256")
        val chunk = ByteArray(8192) { (it * 31 + 7).toByte() }
        f.outputStream().use { out ->
            var left = size
            while (left > 0) {
                val n = minOf(left, chunk.size)
                out.write(chunk, 0, n)
                md.update(chunk, 0, n)
                left -= n
            }
        }
        return f to md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun capture(
        cid: String = id,
        millis: Long = 1000,
        noteNeedsSync: Boolean = false,
        note: String? = null,
        noteUpdatedAt: String? = null,
        status: SyncStatus = SyncStatus.WAITING,
        attempts: Int = 0,
    ) = CaptureEntity(
        id = cid, capturedAt = "2026-10-26T10:00:00+05:30", capturedAtMillis = millis,
        baseRecordJson = """{"schema_version":1,"id":"$cid","captured_at":"2026-10-26T10:00:00+05:30"}""",
        sourceLabel = "Chrome", previewTitle = null, previewText = null, previewUrl = null, thumbPath = null,
        note = note, noteUpdatedAt = noteUpdatedAt, noteNeedsSync = noteNeedsSync, filesComplete = true,
        syncStatus = status, attempts = attempts, lastError = null, uploadedAt = null,
    )

    private fun doneFile(index: Int, f: File, sha: String, cid: String = id) = FileEntity(
        captureId = cid, index = index, sourceUri = "content://x/$index", displayName = f.name,
        mimeType = "image/jpeg", size = f.length(), localPath = f.path, sha256 = sha, error = null,
        state = FileState.DONE,
    )

    private fun errorFile(index: Int) = FileEntity(
        captureId = id, index = index, sourceUri = "content://x/$index", displayName = "bad.bin",
        mimeType = null, size = null, localPath = null, sha256 = null, error = "Permission denied",
        state = FileState.ERROR,
    )

    @Test
    fun happyPath_statusPutCommit_endsUploaded() = runBlocking {
        val (f, sha) = makeFile("big.jpg", 3 * 1024 * 1024)
        dao.insertCapture(capture(), listOf(doneFile(0, f, sha)))
        val out = uploader().uploadPending()
        assertEquals(UploadOutcome(1, false), out)
        assertEquals(
            listOf("GET /v1/captures/$id", "PUT /v1/captures/$id/files/0", "POST /v1/captures/$id/commit"),
            receiver.log,
        )
        server.takeRequest() // GET
        val put = server.takeRequest()
        assertEquals(sha, put.getHeader("X-Sha256"))
        assertEquals("Bearer tok", put.getHeader("Authorization"))
        assertEquals(3L * 1024 * 1024, put.bodySize)
        val c = dao.get(id)!!
        assertEquals(SyncStatus.UPLOADED, c.syncStatus)
        assertEquals("2026-10-26T12:00:00+05:30", c.uploadedAt)
        assertNull(c.lastError)
        val rec = receiver.commits.single()
        assertEquals(sha, rec["files"]!!.jsonArray[0].jsonObject["sha256"]!!.jsonPrimitive.content)
    }

    @Test
    fun concurrentPasses_serialize_eachCaptureCheckedOnce() = runBlocking {
        val (f, sha) = makeFile("c.jpg", 100)
        dao.insertCapture(capture(), listOf(doneFile(0, f, sha)))
        val u = uploader()
        val outs = listOf(
            async(Dispatchers.IO) { u.uploadPending() },
            async(Dispatchers.IO) { u.uploadPending() },
        ).awaitAll()
        assertEquals(1, receiver.log.count { it == "GET /v1/captures/$id" })
        assertEquals(1, receiver.commits.size)
        assertEquals(1, outs.sumOf { it.uploaded })
        assertEquals(SyncStatus.UPLOADED, dao.get(id)!!.syncStatus)
    }

    @Test
    fun alreadyStored_skipsUploadAndMarksUploaded() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        receiver.storedIds += id
        dao.insertCapture(capture(), listOf(doneFile(0, f, sha)))
        assertEquals(UploadOutcome(1, false), uploader().uploadPending())
        assertEquals(listOf("GET /v1/captures/$id"), receiver.log)
        assertEquals(SyncStatus.UPLOADED, dao.get(id)!!.syncStatus)
    }

    @Test
    fun stagedWithSameHash_skipsPut() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        receiver.staged[id] = mutableMapOf(0 to sha)
        dao.insertCapture(capture(), listOf(doneFile(0, f, sha)))
        assertEquals(UploadOutcome(1, false), uploader().uploadPending())
        assertEquals(listOf("GET /v1/captures/$id", "POST /v1/captures/$id/commit"), receiver.log)
        assertEquals(SyncStatus.UPLOADED, dao.get(id)!!.syncStatus)
    }

    @Test
    fun unauthorized_marksFailedWithReason() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        dao.insertCapture(capture(), listOf(doneFile(0, f, sha)))
        val out = uploader(client("wrong")).uploadPending()
        assertEquals(UploadOutcome(0, false), out)
        val c = dao.get(id)!!
        assertEquals(SyncStatus.FAILED, c.syncStatus)
        assertEquals("Desktop didn't accept the pairing token", c.lastError)
    }

    @Test
    fun rejected_marksFailedWithReason() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        receiver.commitReject = "bad_record"
        dao.insertCapture(capture(), listOf(doneFile(0, f, sha)))
        val out = uploader().uploadPending()
        assertEquals(UploadOutcome(0, false), out)
        val c = dao.get(id)!!
        assertEquals(SyncStatus.FAILED, c.syncStatus)
        assertEquals("Desktop rejected the file", c.lastError)
    }

    @Test
    fun unreachable_staysWaitingAndAsksForRetry() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        dao.insertCapture(capture(attempts = 2), listOf(doneFile(0, f, sha)))
        val c0 = client()
        server.shutdown()
        val out = uploader(c0).uploadPending()
        assertEquals(UploadOutcome(0, true), out)
        val c = dao.get(id)!!
        assertEquals(SyncStatus.WAITING, c.syncStatus)
        assertEquals("Couldn't reach your desktop", c.lastError)
        assertEquals(3, c.attempts)
    }

    @Test
    fun noteEditedAfterUpload_sendsNoteOnly() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        receiver.storedIds += id
        dao.insertCapture(
            capture(noteNeedsSync = true, note = "hello", noteUpdatedAt = "2026-10-26T11:00:00+05:30", status = SyncStatus.UPLOADED),
            listOf(doneFile(0, f, sha)),
        )
        assertEquals(UploadOutcome(1, false), uploader().uploadPending())
        assertEquals(listOf("GET /v1/captures/$id", "PUT /v1/captures/$id/note"), receiver.log)
        val n = receiver.notes.single()
        assertEquals("hello", n["note"]!!.jsonPrimitive.content)
        assertEquals("2026-10-26T11:00:00+05:30", n["note_updated_at"]!!.jsonPrimitive.content)
        assertFalse(dao.get(id)!!.noteNeedsSync)
    }

    @Test
    fun fileWithCopyError_isListedNotUploaded() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        dao.insertCapture(capture(), listOf(doneFile(0, f, sha), errorFile(1)))
        assertEquals(UploadOutcome(1, false), uploader().uploadPending())
        assertEquals(
            listOf("GET /v1/captures/$id", "PUT /v1/captures/$id/files/0", "POST /v1/captures/$id/commit"),
            receiver.log,
        )
        val files = receiver.commits.single()["files"] as JsonArray
        assertEquals(2, files.size)
        assertEquals("Permission denied", files[1].jsonObject["error"]!!.jsonPrimitive.content)
        assertNotNull(files[0].jsonObject["sha256"])
    }

    @Test
    fun notConfigured_staysWaitingNoRetry() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        dao.insertCapture(capture(attempts = 1), listOf(doneFile(0, f, sha)))
        val out = uploader(null).uploadPending()
        assertEquals(UploadOutcome(0, false), out)
        val c = dao.get(id)!!
        assertEquals(SyncStatus.WAITING, c.syncStatus)
        assertEquals("Desktop not set up yet", c.lastError)
        assertEquals(1, c.attempts)
        assertTrue(receiver.log.isEmpty())
    }

    @Test
    fun httpClient_hasRequiredTimeouts() {
        val c = ReceiverClient.httpClient()
        assertEquals(10_000, c.connectTimeoutMillis)
        assertEquals(120_000, c.readTimeoutMillis)
        assertEquals(120_000, c.writeTimeoutMillis)
    }

    @Test
    fun create_rejectsBlankAndHeaderIllegalTokens() {
        assertNull(ReceiverClient.create(server.url("/"), "  \n"))
        assertNull(ReceiverClient.create(server.url("/"), "to\nken"))
        assertNull(ReceiverClient.create(server.url("/"), "tok\u00e9n"))
        assertNotNull(ReceiverClient.create(server.url("/"), " tok\n"))
    }

    @Test
    fun badTokenFromCreate_meansNotSetUp() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        dao.insertCapture(capture(), listOf(doneFile(0, f, sha)))
        val out = uploader(ReceiverClient.create(server.url("/"), "to\nken")).uploadPending()
        assertEquals(UploadOutcome(0, false), out)
        assertEquals("Desktop not set up yet", dao.get(id)!!.lastError)
        assertTrue(receiver.log.isEmpty())
    }

    @Test
    fun unexpectedException_goesWaitingAndNextCaptureStillRuns() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        val id2 = "22222222-2222-4333-8444-555555555555"
        dao.insertCapture(capture(millis = 1000), listOf(doneFile(0, f, sha)))
        dao.insertCapture(capture(cid = id2, millis = 2000), listOf(doneFile(0, f, sha, id2)))
        // A raw constructor with a newline in the token makes Request.Builder throw IllegalArgumentException.
        val out = uploader(ReceiverClient(server.url("/"), "to\nken", OkHttpClient())).uploadPending()
        assertEquals(UploadOutcome(0, true), out)
        for (i in listOf(id, id2)) {
            val c = dao.get(i)!!
            assertEquals(SyncStatus.WAITING, c.syncStatus)
            assertEquals("Couldn't reach your desktop", c.lastError)
            assertEquals(1, c.attempts)
        }
    }

    @Test
    fun hashMismatch_isTransientAndContinues() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        val id2 = "22222222-2222-4333-8444-555555555555"
        receiver.putHashMismatch = true
        dao.insertCapture(capture(millis = 1000), listOf(doneFile(0, f, sha)))
        dao.insertCapture(capture(cid = id2, millis = 2000), listOf(doneFile(0, f, sha, id2)))
        val out = uploader().uploadPending()
        assertEquals(UploadOutcome(0, true), out)
        assertTrue(receiver.log.any { it.contains(id2) })
        assertEquals(SyncStatus.WAITING, dao.get(id)!!.syncStatus)
        assertEquals(1, dao.get(id)!!.attempts)
    }

    @Test
    fun missingLocalFile_marksFailedWithoutRequests() = runBlocking {
        val (f, sha) = makeFile("gone.jpg", 100)
        dao.insertCapture(capture(), listOf(doneFile(0, f, sha)))
        assertTrue(f.delete())
        val out = uploader().uploadPending()
        assertEquals(UploadOutcome(0, false), out)
        val c = dao.get(id)!!
        assertEquals(SyncStatus.FAILED, c.syncStatus)
        assertEquals("File missing on this phone", c.lastError)
        assertTrue(receiver.log.isEmpty())
    }

    @Test
    fun unreachableOnFirst_stopsBeforeSecond() = runBlocking {
        val (f, sha) = makeFile("a.jpg", 100)
        val id2 = "22222222-2222-4333-8444-555555555555"
        receiver.failStatusFor = id
        dao.insertCapture(capture(millis = 1000), listOf(doneFile(0, f, sha)))
        dao.insertCapture(capture(cid = id2, millis = 2000), listOf(doneFile(0, f, sha, id2)))
        val out = uploader().uploadPending()
        assertEquals(UploadOutcome(0, true), out)
        assertTrue(receiver.log.none { it.contains(id2) })
        assertEquals(SyncStatus.WAITING, dao.get(id)!!.syncStatus)
        assertEquals(SyncStatus.WAITING, dao.get(id2)!!.syncStatus)
    }
}
