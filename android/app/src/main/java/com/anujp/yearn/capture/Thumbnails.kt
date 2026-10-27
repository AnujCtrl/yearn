package com.anujp.yearn.capture

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream

object Thumbnails {
    private const val MAX_EDGE = 512
    private const val JPEG_QUALITY = 85

    /** Writes a JPEG thumbnail (long edge <= 512) of an image or video to [out], or returns null. */
    fun make(source: File, mimeType: String?, out: File): File? {
        val tmp = File(out.path + ".tmp")
        return try {
            val bitmap = when {
                mimeType?.startsWith("image/") == true -> decodeImage(source)
                mimeType?.startsWith("video/") == true -> videoFrame(source)
                else -> return null
            } ?: return null
            try {
                FileOutputStream(tmp).use { fos ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)) {
                        throw IllegalStateException("JPEG encode failed")
                    }
                    fos.flush()
                }
            } finally {
                bitmap.recycle()
            }
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) throw IllegalStateException("rename failed")
            out
        } catch (e: Exception) {
            tmp.delete()
            null
        }
    }

    private fun decodeImage(source: File): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            val (w, h) = fit(info.size.width, info.size.height)
            decoder.setTargetSize(w, h)
        }

    private fun videoFrame(source: File): Bitmap? {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(source.absolutePath)
            val frame = mmr.getFrameAtTime(1_000_000) ?: return null
            val (w, h) = fit(frame.width, frame.height)
            if (w == frame.width && h == frame.height) return frame
            val scaled = Bitmap.createScaledBitmap(frame, w, h, true)
            if (scaled !== frame) frame.recycle()
            return scaled
        } finally {
            mmr.release()
        }
    }

    /** Scales to a long edge <= 512, keeping aspect ratio and never upscaling. */
    private fun fit(w: Int, h: Int): Pair<Int, Int> {
        val long = maxOf(w, h)
        if (long <= MAX_EDGE) return w to h
        val s = MAX_EDGE.toDouble() / long
        return maxOf(1, Math.round(w * s).toInt()) to maxOf(1, Math.round(h * s).toInt())
    }
}
