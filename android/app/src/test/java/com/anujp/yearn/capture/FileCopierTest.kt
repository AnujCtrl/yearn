package com.anujp.yearn.capture

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Deterministic byte pattern of [total] bytes, optionally failing once [failAt] bytes were served. */
private class PatternStream(
    private val total: Long,
    private val failAt: Long = -1,
    private val failure: () -> Throwable = { IOException("boom") },
) : InputStream() {
    private var pos = 0L

    private fun byteAt(i: Long): Byte = ((i * 31 + 7) and 0xff).toByte()

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (failAt in 0..pos) throw failure()
        if (pos >= total) return -1
        var limit = minOf(len.toLong(), total - pos)
        if (failAt >= 0) limit = minOf(limit, failAt - pos).coerceAtLeast(0)
        if (limit == 0L) throw failure()
        for (k in 0 until limit.toInt()) b[off + k] = byteAt(pos + k)
        pos += limit
        return limit.toInt()
    }

    companion object {
        fun sha256(total: Long): String {
            val md = MessageDigest.getInstance("SHA-256")
            val s = PatternStream(total)
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf, 0, buf.size)
                if (n < 0) break
                md.update(buf, 0, n)
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
class FileCopierTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var resolver: ContentResolver
    private lateinit var capturesDir: File

    @Before
    fun setUp() {
        resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        capturesDir = tmp.newFolder("captures")
    }

    @Test
    fun copiesFiveMegabytesWithCorrectHash() = runTest {
        val size = 5L * 1024 * 1024
        val uri = Uri.parse("content://test.provider/video/1")
        shadowOf(resolver).registerInputStream(uri, PatternStream(size))
        val seen = mutableListOf<Long>()

        val result = FileCopier(resolver, capturesDir).copy("cap1", 0, uri) { seen += it }

        assertTrue(result is CopyResult.Done)
        result as CopyResult.Done
        assertEquals(size, result.size)
        assertEquals(PatternStream.sha256(size), result.sha256)
        assertEquals(File(capturesDir, "cap1/0").absolutePath, result.localPath)
        assertEquals(size, File(result.localPath).length())
        assertFalse(File(capturesDir, "cap1/0.part").exists())
        assertEquals(size, seen.last())
        assertEquals(seen.sorted(), seen)
    }

    @Test
    fun failedCopy_leavesNoPartFile() = runTest {
        val uri = Uri.parse("content://test.provider/video/2")
        shadowOf(resolver).registerInputStream(uri, PatternStream(1_000_000, failAt = 300_000))

        val result = FileCopier(resolver, capturesDir).copy("cap2", 1, uri)

        assertTrue(result is CopyResult.Failed)
        assertTrue((result as CopyResult.Failed).error.startsWith("Couldn't read this file: "))
        val dir = File(capturesDir, "cap2")
        assertEquals(emptyList<String>(), dir.list()?.toList() ?: emptyList<String>())
    }

    @Test
    fun securityException_isRecordedAsFailure() = runTest {
        // Robolectric has no provider here; a registered stream whose read throws SecurityException.
        val uri = Uri.parse("content://test.provider/video/3")
        shadowOf(resolver).registerInputStream(
            uri,
            PatternStream(100, failAt = 0, failure = { SecurityException("denied") }),
        )

        val result = FileCopier(resolver, capturesDir).copy("cap3", 0, uri)

        assertEquals(CopyResult.Failed(null, "No permission to read this file"), result)
        assertEquals(emptyList<String>(), File(capturesDir, "cap3").list()?.toList() ?: emptyList<String>())
    }

    @Test
    fun thumbnails_nonMediaReturnsNull() {
        val src = tmp.newFile("notes.txt").also { it.writeText("hello") }
        val out = File(tmp.root, "thumb.jpg")

        assertNull(Thumbnails.make(src, "text/plain", out))
        assertNull(Thumbnails.make(src, null, out))
        assertFalse(out.exists())
        assertFalse(File(tmp.root, "thumb.jpg.tmp").exists())
    }
}
