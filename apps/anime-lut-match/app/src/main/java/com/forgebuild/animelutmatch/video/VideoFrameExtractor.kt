package com.forgebuild.animelutmatch.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight frame access: never decodes a whole video into RAM. Extracts single frames and
 * small thumbnails on demand (cached), releases memory aggressively via [release].
 */
class VideoFrameExtractor(context: Context, val uri: Uri) {
    private val retriever = MediaMetadataRetriever()
    val durationMs: Long
    val width: Int
    val height: Int

    init {
        retriever.setDataSource(context, uri)
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
    }

    private val thumbCache = ConcurrentHashMap<Long, Bitmap>()

    /** Full-resolution still at [timeMs] (closest-sync first, closest fallback). Not cached — caller owns it. */
    fun frameAt(timeMs: Long): Bitmap? {
        val t = timeMs.coerceIn(0, durationMs) * 1000
        return retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST)
    }

    /** Downscaled still for analysis (bounded memory for matching/color analysis). */
    fun scaledFrameAt(timeMs: Long, maxDim: Int = 512): Bitmap? {
        val f = frameAt(timeMs) ?: return null
        val scale = maxDim.toFloat() / maxOf(f.width, f.height)
        if (scale >= 1f) return f
        val out = Bitmap.createScaledBitmap(
            f, (f.width * scale).toInt().coerceAtLeast(1),
            (f.height * scale).toInt().coerceAtLeast(1), true
        )
        f.recycle()
        return out
    }

    /** Small thumbnail for the scrub strip. Cached. */
    fun thumbAt(timeMs: Long, w: Int = 160): Bitmap? {
        val t = timeMs.coerceIn(0, durationMs)
        return thumbCache[t] ?: run {
            val f = frameAt(t) ?: return null
            val h = (f.height.toFloat() / f.width * w).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(f, w, h, true).also { thumbCache[t] = it; if (f != it) f.recycle() }
        }
    }

    fun release() {
        thumbCache.values.forEach { it.recycle() }
        thumbCache.clear()
        try { retriever.release() } catch (_: Exception) { }
    }
}
