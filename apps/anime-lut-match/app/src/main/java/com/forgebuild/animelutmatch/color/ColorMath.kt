package com.forgebuild.animelutmatch.color

/**
 * Automatic color transfer. Frames are sRGB-encoded; matching is performed per RGB channel
 * in that space — the same space After Effects Levels operates in. The model per channel is:
 * percentile black/white-point linear remap (2%..98% to reject outlier noise/compression) plus a
 * gamma refinement so the mid-tone median lands on the reference mid-tone. This reproduces the
 * AE black-point / white-point / gamma channel workflow as a smooth nonlinear map — deliberately
 * NOT a histogram lookup, so spatially-different content cannot alias to a wrong grade.
 */
object ColorMath {

    private fun percentile(sorted: IntArray, p: Float): Int =
        sorted[(p * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)]

    /** Sample at most [limit] pixels on a stride. Returns (r,g,b) triples 0..255. */
    fun samplePixels(px: IntArray, limit: Int = 20000): Triple<IntArray, IntArray, IntArray> {
        val n = px.size
        val stride = maxOf(1, n / limit)
        val m = (n + stride - 1) / stride
        val r = IntArray(m); val g = IntArray(m); val b = IntArray(m)
        var i = 0; var k = 0
        while (i < n) {
            val c = px[i]
            r[k] = c shr 16 and 0xff; g[k] = c shr 8 and 0xff; b[k] = c and 0xff
            k++; i += stride
        }
        return Triple(r.copyOf(k), g.copyOf(k), b.copyOf(k))
    }

    /** Build one channel's 256-entry correction from before (target) and after (reference) values. */
    fun channelCurve(before: IntArray, after: IntArray): FloatArray {
        val bs = before.sortedArray(); val af = after.sortedArray()
        val inLo = percentile(bs, 0.02f).toFloat(); val inHi = percentile(bs, 0.98f).toFloat()
        val outLo = percentile(af, 0.02f).toFloat(); val outHi = percentile(af, 0.98f).toFloat()
        val inMed = percentile(bs, 0.5f).toFloat(); val outMed = percentile(af, 0.5f).toFloat()
        fun lin(x: Float): Float {
            val t = ((x - inLo) / (inHi - inLo).coerceAtLeast(1e-3f)).coerceIn(0f, 1f)
            return outLo + t * (outHi - outLo)
        }
        var gamma = 1f
        val linMed = lin(inMed)
        val tm = ((outMed - linMed) / 255f).coerceIn(0.01f, 0.99f)
        gamma = (Math.log(tm.toDouble()) / Math.log((linMed / 255f).coerceIn(0.01, 0.99))).toFloat().coerceIn(0.1f, 10f)
        val lut = FloatArray(256)
        for (i in 0..255) {
            val t = (lin(i.toFloat()) / 255f).coerceIn(0f, 1f)
            lut[i] = (255f * Math.pow(t.toDouble(), 1.0 / gamma).toFloat()).coerceIn(0f, 255f)
        }
        return lut
    }

    /** Full per-channel auto transfer: returns [rLut, gLut, bLut] mapping target->reference color. */
    fun autoTransfer(
        ref: Triple<IntArray, IntArray, IntArray>,
        tgt: Triple<IntArray, IntArray, IntArray>,
    ): Array<FloatArray> = arrayOf(
        channelCurve(tgt.first, ref.first),
        channelCurve(tgt.second, ref.second),
        channelCurve(tgt.third, ref.third),
    )
}
