package com.anujp.yearn.sync

import com.anujp.yearn.data.CaptureDao
import com.anujp.yearn.data.CaptureEntity
import com.anujp.yearn.data.CaptureRecord
import com.anujp.yearn.data.FileState
import com.anujp.yearn.data.SyncStatus
import com.anujp.yearn.data.Timestamps
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.ZonedDateTime

data class UploadOutcome(val uploaded: Int, val retry: Boolean)

/**
 * Uploads pending captures oldest first. Every step is idempotent against the receiver, so a run can be
 * interrupted anywhere and simply repeated.
 */
class CaptureUploader(
    private val dao: CaptureDao,
    private val client: suspend () -> ReceiverClient?,
    private val clock: () -> ZonedDateTime,
) {
    private enum class Next { CONTINUE, CONTINUE_RETRY, STOP_RETRY, STOP_NO_RETRY }

    suspend fun uploadPending(): UploadOutcome {
        val pending = dao.pendingUploads()
        if (pending.isEmpty()) return UploadOutcome(0, false)
        val c = client()
        if (c == null) {
            for (p in pending) {
                // A capture the desktop already has keeps its Uploaded status; only its note is waiting.
                if (p.syncStatus != SyncStatus.UPLOADED) {
                    dao.setSync(p.id, SyncStatus.WAITING, p.attempts, NOT_SET_UP, null)
                }
            }
            return UploadOutcome(0, false)
        }
        var uploaded = 0
        var retry = false
        for (p in pending) {
            val result = try {
                process(c, p)
            } catch (e: ReceiverError) {
                // Unexpected escape from process(): treat like the matching error on this capture.
                null to handle(e, p, dao.get(p.id) ?: p, reupload = false)
            }
            if (result.first == true) uploaded++
            when (result.second) {
                Next.CONTINUE -> Unit
                Next.CONTINUE_RETRY -> retry = true
                Next.STOP_RETRY -> return UploadOutcome(uploaded, true)
                Next.STOP_NO_RETRY -> return UploadOutcome(uploaded, false)
            }
        }
        return UploadOutcome(uploaded, retry)
    }

    /** Returns (counted as uploaded, what the run does next). */
    private suspend fun process(c: ReceiverClient, p: CaptureEntity): Pair<Boolean?, Next> {
        var cur = dao.get(p.id) ?: return null to Next.CONTINUE
        val wasUploaded = cur.syncStatus == SyncStatus.UPLOADED
        var reupload = false
        try {
            if (!wasUploaded) {
                val missing = hasMissingFile(cur.id)
                if (missing) {
                    dao.setSync(cur.id, SyncStatus.FAILED, cur.attempts, FILE_MISSING, null)
                    return null to Next.CONTINUE
                }
                dao.setSync(cur.id, SyncStatus.UPLOADING, cur.attempts, null, null)
            }
            val status = c.status(cur.id)
            if (status.stored) {
                if (cur.noteNeedsSync) {
                    try {
                        sendNote(c, cur)
                        if (!wasUploaded) markUploaded(cur.id, cur.attempts)
                        return true to Next.CONTINUE
                    } catch (e: ReceiverError.NotStored) {
                        // The desktop lost it: fall through and send everything again.
                        reupload = true
                        dao.setSync(cur.id, SyncStatus.UPLOADING, cur.attempts, null, null)
                    }
                } else {
                    if (!wasUploaded) markUploaded(cur.id, cur.attempts)
                    return !wasUploaded to Next.CONTINUE
                }
            }
            if (wasUploaded && !reupload) return null to Next.CONTINUE
            // Not stored on the desktop: upload what it is missing, then commit.
            if (wasUploaded) {
                val missing = hasMissingFile(cur.id)
                if (missing) {
                    dao.setSync(cur.id, SyncStatus.FAILED, cur.attempts, FILE_MISSING, null)
                    return null to Next.CONTINUE
                }
            }
            val staged = if (status.stored) emptyMap() else status.staged
            val files = dao.files(cur.id)
            for (f in files) {
                val sha = f.sha256
                if (f.state != FileState.DONE || sha == null) continue
                if (staged[f.index] == sha) continue
                c.putFile(cur.id, f.index, File(f.localPath!!), sha, f.mimeType)
            }
            cur = dao.get(cur.id) ?: return null to Next.CONTINUE
            val record = CaptureRecord.full(
                Json.parseToJsonElement(cur.baseRecordJson).jsonObject, dao.files(cur.id), cur.note, cur.noteUpdatedAt,
            )
            c.commit(cur.id, record)
            dao.setSync(cur.id, SyncStatus.UPLOADED, cur.attempts, null, Timestamps.format(clock()))
            clearNoteIfUnchanged(cur.id, cur.note, cur.noteUpdatedAt)
            return true to Next.CONTINUE
        } catch (e: ReceiverError) {
            return null to handle(e, p, dao.get(p.id) ?: cur, reupload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Bad header value, SQLite trouble, anything unforeseen: never leave the capture stuck in Uploading.
            val next = runCatching { handle(ReceiverError.Unreachable(e), p, dao.get(p.id) ?: cur, reupload) }
            if (next.isFailure) next.exceptionOrNull().let { if (it is CancellationException) throw it }
            return null to Next.CONTINUE_RETRY
        }
    }

    private suspend fun hasMissingFile(id: String): Boolean =
        dao.files(id).any { it.state == FileState.DONE && it.localPath?.let { f -> File(f).exists() } != true }

    private suspend fun sendNote(c: ReceiverClient, cur: CaptureEntity) {
        c.putNote(cur.id, cur.note.orEmpty(), cur.noteUpdatedAt.orEmpty())
        clearNoteIfUnchanged(cur.id, cur.note, cur.noteUpdatedAt)
    }

    private suspend fun markUploaded(id: String, attempts: Int) =
        dao.setSync(id, SyncStatus.UPLOADED, attempts, null, Timestamps.format(clock()))

    /** A note edited while the upload was in flight must still sync later. */
    private suspend fun clearNoteIfUnchanged(id: String, note: String?, noteUpdatedAt: String?) {
        val now = dao.get(id) ?: return
        if (now.note == note && now.noteUpdatedAt == noteUpdatedAt) dao.clearNoteNeedsSync(id)
    }

    private suspend fun handle(e: ReceiverError, p: CaptureEntity, cur: CaptureEntity, reupload: Boolean): Next {
        // A capture the desktop already confirmed only has a note pending: its status stays Uploaded.
        val keepStatus = p.syncStatus == SyncStatus.UPLOADED && !reupload
        val transient = e is ReceiverError.Unreachable || (e is ReceiverError.Rejected && e.error == HASH_MISMATCH)
        return when {
            transient -> {
                if (!keepStatus) dao.setSync(cur.id, SyncStatus.WAITING, cur.attempts + 1, UNREACHABLE, null)
                // A corrupt local file must not block every later capture; an unreachable desktop would.
                if (e is ReceiverError.Rejected) Next.CONTINUE_RETRY else Next.STOP_RETRY
            }
            e is ReceiverError.Unauthorized -> {
                if (!keepStatus) dao.setSync(cur.id, SyncStatus.FAILED, cur.attempts, UNAUTHORIZED, null)
                Next.STOP_NO_RETRY
            }
            else -> { // Rejected (NotStored cannot reach here outside the note path)
                if (!keepStatus) dao.setSync(cur.id, SyncStatus.FAILED, cur.attempts, REJECTED, null)
                Next.CONTINUE
            }
        }
    }

    private companion object {
        const val HASH_MISMATCH = "hash_mismatch"
        const val NOT_SET_UP = "Desktop not set up yet"
        const val UNREACHABLE = "Couldn't reach your desktop"
        const val REJECTED = "Desktop rejected the file"
        const val UNAUTHORIZED = "Desktop didn't accept the pairing token"
        const val FILE_MISSING = "File missing on this phone"
    }
}
