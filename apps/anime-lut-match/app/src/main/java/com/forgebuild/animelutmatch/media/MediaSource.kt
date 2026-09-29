package com.forgebuild.animelutmatch.media

import android.graphics.Bitmap
import com.forgebuild.animelutmatch.video.VideoFrameExtractor

/**
 * Imported media for either side of the match: a video (frame extractor) or a still image
 * (already-decoded bitmap). Added in v2 per operator request — reference AND target can each
 * be a video or an image in any combination.
 */
sealed interface MediaSource {
    class Video(val extractor: VideoFrameExtractor) : MediaSource
    class Image(val bitmap: Bitmap) : MediaSource

    fun describe(): String = when (this) {
        is Video -> "video · ${extractor.durationMs / 1000.0}s · ${extractor.width}×${extractor.height}"
        is Image -> "image · ${bitmap.width}×${bitmap.height}"
    }

    fun release() {
        if (this is Video) extractor.release()
    }
}
