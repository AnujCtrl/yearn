package com.anujp.yearn.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus { WAITING, UPLOADING, UPLOADED, FAILED }

enum class FileState { PENDING, DONE, ERROR }

/** The failure reason a wrong pairing token leaves; fixing the token or address retries exactly these. */
const val TOKEN_REJECTED_REASON = "Desktop didn't accept the pairing token"

const val INTERRUPTED_COPY_ERROR = "Copy interrupted: yearn was closed before this file finished copying"

@Entity("captures")
data class CaptureEntity(
    @PrimaryKey val id: String,
    val capturedAt: String,
    val capturedAtMillis: Long,
    val baseRecordJson: String,
    val sourceLabel: String,
    val previewTitle: String?,
    val previewText: String?,
    val previewUrl: String?,
    val thumbPath: String?,
    val note: String?,
    val noteUpdatedAt: String?,
    val noteNeedsSync: Boolean,
    val filesComplete: Boolean,
    val syncStatus: SyncStatus,
    val attempts: Int,
    val lastError: String?,
    val uploadedAt: String?,
)

@Entity("files", primaryKeys = ["captureId", "index"])
data class FileEntity(
    val captureId: String,
    val index: Int,
    val sourceUri: String,
    val displayName: String?,
    val mimeType: String?,
    val size: Long?,
    val localPath: String?,
    val sha256: String?,
    val error: String?,
    val state: FileState,
)
