package com.anujp.yearn.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CaptureDao {
    @Insert
    protected abstract suspend fun insertCaptureRow(c: CaptureEntity)

    @Insert
    protected abstract suspend fun insertFileRows(files: List<FileEntity>)

    @Transaction
    open suspend fun insertCapture(c: CaptureEntity, files: List<FileEntity>) {
        insertCaptureRow(c)
        insertFileRows(files)
    }

    @Query("SELECT * FROM captures ORDER BY capturedAtMillis DESC")
    abstract fun observeAll(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id")
    abstract fun observe(id: String): Flow<CaptureEntity?>

    @Query("SELECT * FROM captures WHERE id = :id")
    abstract suspend fun get(id: String): CaptureEntity?

    @Query("SELECT * FROM files WHERE captureId = :id ORDER BY `index`")
    abstract suspend fun files(id: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE captureId = :id ORDER BY `index`")
    abstract fun observeFiles(id: String): Flow<List<FileEntity>>

    @Update
    abstract suspend fun updateFile(f: FileEntity)

    @Query("UPDATE captures SET filesComplete = 1, thumbPath = :thumbPath WHERE id = :id")
    abstract suspend fun setFilesComplete(id: String, thumbPath: String?)

    @Query(
        "UPDATE captures SET syncStatus = :status, attempts = :attempts, lastError = :lastError, " +
            "uploadedAt = :uploadedAt WHERE id = :id",
    )
    abstract suspend fun setSync(id: String, status: SyncStatus, attempts: Int, lastError: String?, uploadedAt: String?)

    @Query("UPDATE captures SET note = :note, noteUpdatedAt = :noteUpdatedAt, noteNeedsSync = 1 WHERE id = :id")
    abstract suspend fun setNote(id: String, note: String, noteUpdatedAt: String)

    @Query("UPDATE captures SET noteNeedsSync = 0 WHERE id = :id")
    abstract suspend fun clearNoteNeedsSync(id: String)

    @Query(
        "SELECT * FROM captures WHERE filesComplete = 1 AND " +
            "(syncStatus IN ('WAITING', 'UPLOADING') OR noteNeedsSync = 1) ORDER BY capturedAtMillis ASC",
    )
    abstract suspend fun pendingUploads(): List<CaptureEntity>

    @Query("UPDATE files SET state = 'ERROR', error = :error WHERE state = 'PENDING'")
    protected abstract suspend fun failPendingFiles(error: String): Int

    @Query(
        "UPDATE captures SET filesComplete = 1 WHERE filesComplete = 0 AND " +
            "NOT EXISTS (SELECT 1 FROM files WHERE files.captureId = captures.id AND files.state = 'PENDING')",
    )
    protected abstract suspend fun completeCapturesWithoutPending(): Int

    /** Process-start recovery: copies that never finished become errors; returns how many files. */
    @Transaction
    open suspend fun markInterruptedCopies(): Int {
        val n = failPendingFiles(INTERRUPTED_COPY_ERROR)
        completeCapturesWithoutPending()
        return n
    }
}
