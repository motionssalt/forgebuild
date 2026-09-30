package com.forgebuild.animelutmatch.model

import android.graphics.Bitmap
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import com.forgebuild.animelutmatch.color.CurvesEffect
import com.forgebuild.animelutmatch.color.LevelsEffect

/**
 * One color-match project session: the extracted reference + target frames (video frames OR
 * imported images), the non-destructive correction stack (Auto Match + any number of
 * Levels/Curves), the precision mode, and the live processing log.
 *
 * Everything the UI mutates is Compose-observable (SnapshotStateList / MutableState) so edits
 * re-render instantly, and background rendering always works on a snapshot — mutating the stack
 * (e.g. deleting an effect) while a preview render is in flight can never crash with a
 * ConcurrentModificationException.
 */
class MatchSession(
    var name: String = "anime-lut-match",
    var precision: Precision = Precision.STANDARD,
) {
    enum class Precision { LOW, STANDARD, HIGH }

    sealed class Effect {
        var enabled: Boolean = true
        abstract fun copyEffect(): Effect

        class AutoMatch(val luts: Array<FloatArray>) : Effect() {
            override fun copyEffect() = AutoMatch(luts.map { it.copyOf() }.toTypedArray())
            fun map(r: Float, g: Float, b: Float): FloatArray = floatArrayOf(
                luts[0][r.toInt().coerceIn(0, 255)],
                luts[1][g.toInt().coerceIn(0, 255)],
                luts[2][b.toInt().coerceIn(0, 255)],
            )
        }

        class Levels(val fx: LevelsEffect = LevelsEffect()) : Effect() {
            override fun copyEffect() = Levels(LevelsEffect().also { e ->
                fx.channels.forEachIndexed { i, p -> e.channels[i] = p.copy() }
            })
        }

        class Curves(val fx: CurvesEffect = CurvesEffect()) : Effect() {
            override fun copyEffect() = Curves(CurvesEffect().also { e ->
                fx.channels.forEachIndexed { i, pts -> e.channels[i].clear(); e.channels[i].addAll(pts) }
            })
        }
    }

    /**
     * Observable non-destructive stack. SnapshotStateList is safe to iterate during composition
     * and safe against concurrent mutation (snapshot isolation) — this is the fix for the
     * effect-delete crash.
     */
    val stack = mutableStateListOf<Effect>()

    var referenceFrame: Bitmap? = null
    var targetFrame: Bitmap? = null
    /** Crop rect (left,top,right,bottom) into the target frame matching the reference framing. */
    var targetCrop: IntArray? = null
    var matchConfidence: Float = 0f
    var matchRange: Pair<Long, Long>? = null

    /** Observable revision counter: every touch() re-renders previews and re-reads controls. */
    private val revisionState = mutableIntStateOf(0)
    val revision: Int get() = revisionState.intValue

    fun touch() { revisionState.intValue++ }

    /** Live processing log (auto mode, imports, matching), newest at end, capped, observable. */
    val log = mutableStateListOf<String>()

    fun logLine(msg: String) {
        while (log.size >= 200) log.removeAt(0)
        log.add(msg)
    }

    /** Enabled effects as a detached snapshot for background rendering. */
    fun enabledEffects(): List<Effect> = stack.filter { it.enabled }

    /** Bake the enabled stack at (r,g,b) 0..255 -> corrected rgb 0..255. */
    fun applyTo(r0: Float, g0: Float, b0: Float): FloatArray {
        var r = r0; var g = g0; var b = b0
        for (e in enabledEffects()) {
            val out = when (e) {
                is Effect.AutoMatch -> e.map(r, g, b)
                is Effect.Levels -> e.fx.map(r, g, b)
                is Effect.Curves -> e.fx.map(r, g, b)
            }
            r = out[0]; g = out[1]; b = out[2]
        }
        return floatArrayOf(r.coerceIn(0f, 255f), g.coerceIn(0f, 255f), b.coerceIn(0f, 255f))
    }

    /** Real-time still preview: apply the stack to a (downscaled) bitmap, pixel-parallel. */
    fun renderPreview(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val o = applyTo((c shr 16 and 0xff).toFloat(), (c shr 8 and 0xff).toFloat(), (c and 0xff).toFloat())
            px[i] = (0xff shl 24) or (o[0].toInt() shl 16) or (o[1].toInt() shl 8) or o[2].toInt()
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Full reset: "Start new project" drops both sides and every setting.
     */
    fun reset() {
        stack.clear()
        referenceFrame = null
        targetFrame = null
        targetCrop = null
        matchConfidence = 0f
        matchRange = null
        log.clear()
        precision = Precision.STANDARD
        touch()
    }

    /**
     * Scoped reset for a single import (v3 fix).
     *
     * Importing new media must clear the previous CORRECTION SETTINGS (no stale stack/crop — the
     * original operator requirement) but must NOT wipe the frame that was already locked in on the
     * OTHER side. v2 called the full reset() on every import, so importing the target erased the
     * reference frame: frame search then always failed ("unable to identify a matching frame"),
     * Auto Match stayed disabled, and the editor's reference pane was empty.
     */
    fun resetForImport(isReference: Boolean) {
        stack.clear()
        targetCrop = null
        matchConfidence = 0f
        matchRange = null
        precision = Precision.STANDARD
        if (isReference) referenceFrame = null else targetFrame = null
        touch()
    }
}
