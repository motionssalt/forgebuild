package com.forgebuild.animelutmatch.color

import kotlin.math.roundToInt

/** After Effects-style Curves: sorted control points on a 0..255 grid, linear interp between points. */
data class Curve(val points: List<Pair<Float, Float>>) {
    fun sample(): FloatArray {
        val p = points.sortedBy { it.first }
        val out = FloatArray(256)
        for (i in 0..255) {
            val x = i.toFloat()
            var j = 0
            while (j < p.size - 1 && p[j + 1].first < x) j++
            val a = p[j]; val b = p[minOf(j + 1, p.size - 1)]
            val t = if (b.first - a.first < 1e-4f) 0f else ((x - a.first) / (b.first - a.first)).coerceIn(0f, 1f)
            out[i] = (a.second + t * (b.second - a.second)).coerceIn(0f, 255f)
        }
        return out
    }
}

/** Curves over channel selector 0=RGB,1=R,2=G,3=B (AE model: master curve then per-channel). */
class CurvesEffect {
    val channels = arrayOf(
        mutableListOf(0f to 0f, 255f to 255f), mutableListOf(0f to 0f, 255f to 255f),
        mutableListOf(0f to 0f, 255f to 255f), mutableListOf(0f to 0f, 255f to 255f),
    )
    private val lutCache = HashMap<Int, FloatArray>()

    fun invalidate() { lutCache.clear() }

    private fun lutFor(c: Int): FloatArray = lutCache.getOrPut(c) { Curve(channels[c]).sample() }

    fun map(r: Float, g: Float, b: Float): FloatArray {
        val m = lutFor(0)
        fun f(c: Int, v: Float): Float =
            lutFor(c)[m[v.roundToInt().coerceIn(0, 255)].roundToInt().coerceIn(0, 255)]
        return floatArrayOf(f(1, r), f(2, g), f(3, b))
    }
}
