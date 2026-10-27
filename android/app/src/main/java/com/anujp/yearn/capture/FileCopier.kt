package com.anujp.yearn.capture

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

sealed interface CopyResult {
    data class Done(
        val localPath: String,
        val displayName: String?,
        val mimeType: String?,
        val size: Long,
        val sha256: String,
    ) : CopyResult

    data class Failed(val displayName: String?, val error: String) : CopyResult
}

interface Copier {
    suspend fun copy(captureId: String, index: Int, uri: Uri, onBytes: (Long) -> Unit = {}): CopyResult
}

/**
 * Streams a content URI into app storage through a fixed buffer, hashing as it reads,
 * so a whole file is never held in memory.
 */
class FileCopier(
    private val resolver: ContentResolver,
    private val capturesDir: File,
) : Copier {
    override suspend fun copy(
        captureId: String,
        index: Int,
        uri: Uri,
        onBytes: (Long) -> Unit,
    ): CopyResult = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
        val dir = File(capturesDir, captureId)
        val part = File(dir, "$index.part")
        val finalFile = File(dir, index.toString())
        try {
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                throw IOException("cannot create ${dir.path}")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val input = resolver.openInputStream(uri) ?: throw IOException("no stream for $uri")
            var total = 0L
            input.use { raw ->
                DigestInputStream(raw, digest).use { src ->
                    FileOutputStream(part).use { out ->
                        val buf = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = src.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            total += n
                            onBytes(total)
                        }
                        out.flush()
                        out.fd.sync()
                    }
                }
            }
            if (finalFile.exists()) finalFile.delete()
            if (!part.renameTo(finalFile)) throw IOException("cannot rename ${part.name}")
            CopyResult.Done(
                localPath = finalFile.absolutePath,
                displayName = displayName,
                mimeType = runCatching { resolver.getType(uri) }.getOrNull(),
                size = total,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            )
        } catch (e: CancellationException) {
            cleanup(part)
            throw e
        } catch (e: SecurityException) {
            cleanup(part)
            CopyResult.Failed(displayName, "No permission to read this file")
        } catch (e: Exception) {
            cleanup(part)
            CopyResult.Failed(displayName, "Couldn't read this file: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun cleanup(part: File) {
        runCatching { part.delete() }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && !c.isNull(i)) c.getString(i) else null
            } else {
                null
            }
        }
    }.getOrNull()

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
