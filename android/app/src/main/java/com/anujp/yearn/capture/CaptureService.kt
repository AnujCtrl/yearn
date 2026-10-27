package com.anujp.yearn.capture

import android.content.Context
import android.content.Intent
import android.os.Build
import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.CaptureEntity
import com.anujp.yearn.data.CaptureRecord
import com.anujp.yearn.data.FileEntity
import com.anujp.yearn.data.FileState
import com.anujp.yearn.data.SyncStatus
import com.anujp.yearn.data.Timestamps
import com.anujp.yearn.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CaptureProgress(
    val captureId: String,
    val preview: SharePreview,
    val thumbPath: String?,
    val filesTotal: Int,
    val filesDone: Int,
    val bytesCopied: Long,
    val bytesTotal: Long?,
    val complete: Boolean,
)

class CaptureFailed(cause: Throwable) : Exception(cause)

fun deviceJson(context: Context): JsonObject {
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
    return JsonObject(
        linkedMapOf(
            "app_version" to (version?.let { JsonPrimitive(it) } ?: JsonNull),
            "android_version" to JsonPrimitive(Build.VERSION.RELEASE),
            "sdk" to JsonPrimitive(Build.VERSION.SDK_INT),
            "model" to JsonPrimitive(Build.MODEL),
        ),
    )
}

/**
 * Persist first: [begin] commits the capture row (and a PENDING row per file) before any file
 * byte is copied, so a share survives the process dying at any later point.
 */
class CaptureService(
    private val dao: CaptureDao,
    private val copier: Copier,
    private val scheduler: SyncScheduler,
    private val thumbsDir: File,
    private val device: JsonObject,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val beforeFirstCapture: suspend () -> Unit = {},
    private val thumbnailer: (File, String?, File) -> File? = Thumbnails::make,
    /** Outlives any one screen: the service is an app-wide singleton. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val copyLocks = ConcurrentHashMap<String, Mutex>()
    private val gate = Mutex()
    private var gateDone = false

    /**
     * Commits the capture row under [id]. The caller picks the id up front (and saves it) so a share
     * restored after process death is begun again under the same id; a row that already exists is
     * returned as it is, never inserted twice.
     */
    suspend fun begin(intent: Intent, hints: SourceHints, id: String = newId()): CaptureProgress {
        try {
            awaitStartupRecovery()
            dao.get(id)?.let { return progressOf(it) }
            val parsed = RawShareParser.parse(intent, hints)
            val at = clock()
            val streams = parsed.streamUris
            val complete = streams.isEmpty()
            val base = CaptureRecord.base(id, Timestamps.format(at), parsed, device)
            val capture = CaptureEntity(
                id = id,
                capturedAt = Timestamps.format(at),
                capturedAtMillis = at.toInstant().toEpochMilli(),
                baseRecordJson = base.toString(),
                sourceLabel = parsed.preview.sourceLabel,
                previewTitle = parsed.preview.title,
                previewText = parsed.preview.text,
                previewUrl = parsed.preview.url,
                thumbPath = null,
                note = null,
                noteUpdatedAt = null,
                noteNeedsSync = false,
                filesComplete = complete,
                syncStatus = SyncStatus.WAITING,
                attempts = 0,
                lastError = null,
                uploadedAt = null,
            )
            val files = streams.mapIndexed { i, uri ->
                FileEntity(
                    captureId = id, index = i, sourceUri = uri.toString(), displayName = null,
                    mimeType = null, size = null, localPath = null, sha256 = null, error = null,
                    state = FileState.PENDING,
                )
            }
            dao.insertCapture(capture, files)
            // The row is committed: a scheduler failure must not make the caller begin (and insert) again.
            if (complete) runCatching { scheduler.enqueueNow() }
            return CaptureProgress(id, parsed.preview, null, streams.size, 0, 0L, null, complete)
        } catch (e: CancellationException) {
            // Only our own cancellation propagates; Room reports a closed database as a foreign one.
            currentCoroutineContext().ensureActive()
            throw CaptureFailed(e)
        } catch (e: Exception) {
            throw CaptureFailed(e)
        }
    }

    /**
     * Serialized per capture: an overlapping call waits, then sees the capture complete and returns,
     * so files are copied once and the scheduler is enqueued once. Runs on IO, safe from Main.
     */
    suspend fun copyFiles(captureId: String, onProgress: (CaptureProgress) -> Unit) {
        val lock = copyLocks.computeIfAbsent(captureId) { Mutex() }
        lock.withLock { withContext(Dispatchers.IO) { copyFilesLocked(captureId, onProgress) } }
    }

    private suspend fun copyFilesLocked(captureId: String, onProgress: (CaptureProgress) -> Unit) {
        val capture = dao.get(captureId) ?: return
        if (capture.filesComplete) return
        val preview = SharePreview(capture.previewTitle, capture.previewText, capture.previewUrl, capture.sourceLabel)
        val all = dao.files(captureId)
        var done = all.count { it.state != FileState.PENDING }
        var bytes = 0L
        fun report(thumb: String?, complete: Boolean) =
            onProgress(CaptureProgress(captureId, preview, thumb, all.size, done, bytes, null, complete))

        for (file in all.filter { it.state == FileState.PENDING }.sortedBy { it.index }) {
            val before = bytes
            val result = copier.copy(captureId, file.index, android.net.Uri.parse(file.sourceUri)) { n ->
                bytes = before + n
                report(null, false)
            }
            when (result) {
                is CopyResult.Done -> {
                    bytes = before + result.size
                    dao.updateFile(
                        file.copy(
                            localPath = result.localPath, displayName = result.displayName,
                            mimeType = result.mimeType, size = result.size, sha256 = result.sha256,
                            error = null, state = FileState.DONE,
                        ),
                    )
                }
                is CopyResult.Failed -> dao.updateFile(
                    file.copy(displayName = result.displayName, error = result.error, state = FileState.ERROR),
                )
            }
            done++
            report(null, false)
        }

        val thumbOut = File(thumbsDir, "$captureId.jpg")
        var thumbPath: String? = null
        for (f in dao.files(captureId).filter { it.state == FileState.DONE }) {
            val made = runCatching {
                thumbsDir.mkdirs()
                thumbnailer(File(f.localPath!!), f.mimeType, thumbOut)
            }.getOrNull()
            if (made != null) {
                thumbPath = made.path
                break
            }
        }
        dao.setFilesComplete(captureId, thumbPath)
        scheduler.enqueueNow()
        report(thumbPath, true)
    }

    private suspend fun progressOf(c: CaptureEntity): CaptureProgress {
        val files = dao.files(c.id)
        return CaptureProgress(
            c.id, SharePreview(c.previewTitle, c.previewText, c.previewUrl, c.sourceLabel), c.thumbPath,
            files.size, files.count { it.state != FileState.PENDING }, 0L, null, c.filesComplete,
        )
    }

    /** True once the capture row is committed. */
    suspend fun exists(captureId: String): Boolean = dao.get(captureId) != null

    /**
     * The share sheet went away for good while files were still copying, with the process alive: the
     * sender's read grant goes with it. Files not yet copied become interrupted-copy errors and the
     * capture is queued for upload, as startup recovery would do after a process death. Runs in the
     * service's own scope, so it outlives the sheet's ViewModel; waits for the cancelled copy to unwind.
     */
    fun interruptCopy(captureId: String): Job = scope.launch(NonCancellable) {
        try {
            copyLocks.computeIfAbsent(captureId) { Mutex() }.withLock {
                val c = dao.get(captureId) ?: return@withLock
                if (c.filesComplete) return@withLock
                dao.markInterruptedCopy(captureId)
                runCatching { scheduler.enqueueNow() }
            }
        } catch (_: Exception) {
            // Startup recovery catches whatever this could not.
        }
    }

    suspend fun saveNote(captureId: String, note: String) {
        dao.setNote(captureId, note, Timestamps.format(clock()))
        scheduler.enqueueNow()
    }

    /** Runs the startup-recovery hook once, before the first insert of this process. */
    private suspend fun awaitStartupRecovery() {
        if (gateDone) return
        gate.withLock {
            if (!gateDone) {
                beforeFirstCapture()
                gateDone = true
            }
        }
    }
}
