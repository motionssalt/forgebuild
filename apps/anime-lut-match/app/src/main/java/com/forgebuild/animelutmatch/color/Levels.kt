package com.forgebuild.animelutmatch.color

/** After Effects-style per-channel Levels: input black/white, output black/white, gamma. */
data class LevelsParams(
    val inBlack: Float = 0f,      // 0..255
    val inWhite: Float = 255f,    // 0..255
    val gamma: Float = 1f,        // 0.1..10
    val outBlack: Float = 0f,     // 0..255
    val outWhite: Float = 255f,   // 0..255
) {
    fun applyTo(v: Float): Float { // v 0..255
        var x = (v - inBlack) / (inWhite - inBlack).coerceAtLeast(1e-4f)
        x = x.coerceIn(0f, 1f)
        x = Math.pow(x.toDouble(), 1.0 / gamma.toDouble()).toFloat()
        return (outBlack + x * (outWhite - outBlack)).coerceIn(0f, 255f)
    }
}

/** Levels over channel selector: 0=RGB master, 1=Red, 2=Green, 3=Blue. */
class LevelsEffect {
    val channels = arrayOf(LevelsParams(), LevelsParams(), LevelsParams(), LevelsParams())
    fun map(r: Float, g: Float, b: Float): FloatArray {
        val m = channels[0]
        return floatArrayOf(
            channels[1].applyTo(m.applyTo(r)),
            channels[2].applyTo(m.applyTo(g)),
            channels[3].applyTo(m.applyTo(b)),
        )
    }
}
