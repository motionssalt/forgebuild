package com.forgebuild.animelutmatch.color

import kotlin.math.ln
import kotlin.math.pow

/**
 * Automatic color transfer. Frames are sRGB-encoded; matching is performed per RGB channel in
 * that space — the same space After Effects Levels operates in. The model per channel is a
 * percentile black/white-point linear remap (2%..98% to reject outlier noise/compression) plus a
 * gamma refinement so the target mid-tone median lands exactly on the reference mid-tone.
 *
 * v2 fix: the gamma exponent is now derived correctly. AE Levels applies pow(x, 1/gamma), so the
 * exponent k that maps the linearly-remapped target median onto the reference median satisfies
 * (linMed/255)^k = outMed/255, i.e. k = ln(outMed/255) / ln(linMed/255), applied directly as
 * pow(t, k). The v1 code fed (outMed − linMed)/255 into the log (which goes negative and clamps
 * whenever the reference is darker than the target) and then double-inverted the exponent — this
 * is what produced the over-bright, washed-out "ugly" output.
 */
object ColorMath {

    /** Per-channel outcome, including the numbers the live log surfaces to the user. */
    data class ChannelReport(
        val name: String,
        val inBlack: Float, val inWhite: Float,   // target percentiles  (input points)
        val outBlack: Float, val outWhite: Float, // reference percentiles (output points)
        val gamma: Float,                         // AE-convention gamma (>1 brightens)
        val lut: FloatArray,                      // 256-entry channel map
    )

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

    fun identityLut(): FloatArray = FloatArray(256) { it.toFloat() }

    /**
     * Build one channel's 256-entry correction from before (target) and after (reference) values.
     * Degenerate (flat) channels fall back to identity so a single-color frame can never blow up.
     */
    fun channelCurve(before: IntArray, after: IntArray, name: String = ""): ChannelReport {
        if (before.isEmpty() || after.isEmpty()) {
            return ChannelReport(name, 0f, 255f, 0f, 255f, 1f, identityLut())
        }
        val bs = before.sortedArray(); val af = after.sortedArray()
        val inLo = percentile(bs, 0.02f).toFloat(); val inHi = percentile(bs, 0.98f).toFloat()
        val outLo = percentile(af, 0.02f).toFloat(); val outHi = percentile(af, 0.98f).toFloat()
        val inMed = percentile(bs, 0.5f).toFloat(); val outMed = percentile(af, 0.5f).toFloat()
        if (inHi - inLo < 1f || outHi - outLo < 1f) {
            return ChannelReport(name, inLo, inHi, outLo, outHi, 1f, identityLut())
        }
        fun lin(x: Float): Float {
            val t = ((x - inLo) / (inHi - inLo)).coerceIn(0f, 1f)
            return outLo + t * (outHi - outLo)
        }
        // AE-style gamma: exponent k with (linMed/255)^k == outMed/255; report as AE gamma = 1/k.
        val linMedN = (lin(inMed) / 255f).coerceIn(0.02f, 0.98f)
        val outMedN = (outMed / 255f).coerceIn(0.02f, 0.98f)
        val k = (ln(outMedN) / ln(linMedN)).coerceIn(0.1f, 10f)
        val lut = FloatArray(256) { i ->
            val t = (lin(i.toFloat()) / 255f).coerceIn(0f, 1f)
            (255f * t.pow(k)).coerceIn(0f, 255f)
        }
        return ChannelReport(name, inLo, inHi, outLo, outHi, 1f / k, lut)
    }
}
