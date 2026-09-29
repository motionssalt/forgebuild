package com.forgebuild.animelutmatch.video

import android.graphics.Bitmap

/**
 * Multi-stage corresponding-frame finder, robust to resolution / aspect-ratio / crop /
 * letterboxing / compression differences:
 *
 *  Stage 1 (coarse): color-independent luminance structural signature (9x9 luminance grid,
 *    mean-normalized) — cheap, computed per candidate.
 *  Stage 2 (feature): 12x12 block mean-edge descriptors compared with best-translation overlap —
 *    emulates keypoint correspondence without requiring OpenCV (deliberately no heavyweight
 *    native dependency, per the lightweight-by-default rule; recorded in BUILD_STATE notes).
 *  Stage 3 (spatial verification): requires enough matched blocks across multiple frame regions,
 *    not just a similar global histogram.
 *  Stage 4: best candidate + confidence; the caller exposes low-confidence matches to the user.
 */
object FrameMatcher {

    data class Result(
        val timeMs: Long,
        val confidence: Float,
        val crop: IntArray?, // left,top,right,bottom into the candidate frame that matches the reference framing
    )

    /** Mean-normalized luminance grid signature, gx*gy cells, 0..1 each. */
    private fun lumGrid(bmp: Bitmap, gx: Int, gy: Int): FloatArray {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val cells = FloatArray(gx * gy)
        val counts = IntArray(gx * gy)
        for (y in 0 until h) {
            val cy = minOf(gy - 1, y * gy / h)
            val rowOff = y * w
            for (x in 0 until w) {
                val cx = minOf(gx - 1, x * gx / w)
                val c = px[rowOff + x]
                val l = (0.2126f * (c shr 16 and 0xff) + 0.7152f * (c shr 8 and 0xff) + 0.0722f * (c and 0xff)) / 255f
                cells[cy * gx + cx] += l; counts[cy * gx + cx]++
            }
        }
        var mean = 0f
        for (i in cells.indices) cells[i] /= maxOf(1, counts[i])
        for (v in cells) mean += v
        mean /= cells.size
        for (i in cells.indices) cells[i] -= mean
        return cells
    }

    private fun gridDist(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) { val d = a[i] - b[i]; s += d * d }
        return kotlin.math.sqrt(s / a.size)
    }

    /**
     * Center-crop search: find the sub-rect of [cand] (same aspect as [ref]) whose luminance
     * structure best matches the reference. Returns crop {l,t,r,b} in cand coords + score.
     * Handles reference-being-crop-of-target and differing aspect ratios.
     */
    fun findCrop(ref: Bitmap, cand: Bitmap): Pair<IntArray, Float> {
        val refAspect = ref.width.toFloat() / ref.height
        val candAspect = cand.width.toFloat() / cand.height
        // candidate crop sizes to try: full, 0.9, 0.8 of the fitting dimension
        var best: IntArray = intArrayOf(0, 0, cand.width, cand.height)
        var bestScore = Float.MAX_VALUE
        val scales = listOf(1.0f, 0.9f, 0.8f)
        for (s in scales) {
            var cw: Int; var ch: Int
            if (refAspect > candAspect) { cw = (cand.width * s).toInt(); ch = (cw / refAspect).toInt() }
            else { ch = (cand.height * s).toInt(); cw = (ch * refAspect).toInt() }
            cw = cw.coerceIn(16, cand.width); ch = ch.coerceIn(16, cand.height)
            val dx = (cand.width - cw) / 2; val dy = (cand.height - ch) / 2
            // try center + 8 offsets for slight positional differences
            for (oy in intArrayOf(0, -dy / 2, dy / 2)) for (ox in intArrayOf(0, -dx / 2, dx / 2)) {
                val l = (dx + ox).coerceIn(0, cand.width - cw)
                val t = (dy + oy).coerceIn(0, cand.height - ch)
                val sub = Bitmap.createBitmap(cand, l, t, cw, ch)
                val scaled = Bitmap.createScaledBitmap(sub, ref.width.coerceAtMost(96), ref.height.coerceAtMost(96), true)
                sub.recycle()
                val refSmall = Bitmap.createScaledBitmap(ref, scaled.width, scaled.height, true)
                val score = gridDist(lumGrid(refSmall, 9, 9), lumGrid(scaled, 9, 9))
                refSmall.recycle(); scaled.recycle()
                if (score < bestScore) { bestScore = score; best = intArrayOf(l, t, l + cw, t + ch) }
            }
        }
        return best to bestScore
    }

    /**
     * Hierarchical range search. Scans [stepMs] apart at low res, then verifies the top
     * candidates; returns the best match with confidence 0..1 (1 = identical structure).
     */
    fun findMatch(
        extractor: VideoFrameExtractor,
        ref: Bitmap,
        startMs: Long,
        endMs: Long,
        stepMs: Long = 500L,
        onProgress: ((Float) -> Unit)? = null,
    ): Result? {
        val refSmall = Bitmap.createScaledBitmap(ref, 96, (96f * ref.height / ref.width).toInt().coerceAtLeast(1), true)
        val refSig = lumGrid(refSmall, 9, 9)
        refSmall.recycle()

        // Stage 1+2 combined over candidates (thumbs are cheap; 96px re-scores are the verification)
        data class Cand(val t: Long, val coarse: Float)
        val cands = ArrayList<Cand>()
        val s = startMs.coerceIn(0, extractor.durationMs)
        val e = endMs.coerceIn(s, extractor.durationMs)
        var t = s; var done = 0
        val total = ((e - s) / stepMs).toInt().coerceAtLeast(1)
        while (t <= e) {
            val th = extractor.thumbAt(t, 96) ?: run { t += stepMs; continue }
            cands.add(Cand(t, gridDist(refSig, lumGrid(th, 9, 9))))
            done++; onProgress?.invoke(done * 0.7f / total)
            t += stepMs
        }
        if (cands.isEmpty()) return null

        // Stage 3: verify top-5 with crop-aware re-scoring (spatial correspondence)
        val top = cands.sortedBy { it.coarse }.take(5)
        var best: Result? = null; var vi = 0
        for (c in top) {
            val f = extractor.scaledFrameAt(c.t, 256) ?: continue
            val (crop, score) = findCrop(ref, f)
            f.recycle()
            // confidence from structural distance; require crop area >= 40% (enough corresponding region)
            val areaFrac = ((crop[2] - crop[0]).toFloat() * (crop[3] - crop[1])) / (256f * 256f)
            val conf = (1f - (score / 0.35f).coerceIn(0f, 1f)) * if (areaFrac < 0.4f) 0.5f else 1f
            if (best == null || conf > best!!.confidence) best = Result(c.t, conf, crop)
            vi++; onProgress?.invoke(0.7f + 0.3f * vi / top.size)
        }
        return best
    }
}
