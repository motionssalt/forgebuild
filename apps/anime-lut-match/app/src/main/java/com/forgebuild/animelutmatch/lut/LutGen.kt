package com.forgebuild.animelutmatch.lut

import android.graphics.Bitmap
import com.forgebuild.animelutmatch.model.MatchSession
import java.util.Locale

/** Bake the session's correction stack into .cube / HALD LUTs at the precision-selected sampling density. */
object LutGen {

    fun gridSize(precision: MatchSession.Precision): Int = when (precision) {
        MatchSession.Precision.LOW -> 17
        MatchSession.Precision.STANDARD -> 33
        MatchSession.Precision.HIGH -> 64
    }

    /** HALD CLUT level: identity image is L^2 x L^2 holding L^3 RGB triples. */
    fun haldLevel(precision: MatchSession.Precision): Int = when (precision) {
        MatchSession.Precision.LOW -> 8
        MatchSession.Precision.STANDARD -> 12
        MatchSession.Precision.HIGH -> 16
    }

    /** Standard .cube text. Working space documented in the header: sRGB gamma-encoded, 8-bit. */
    fun toCube(session: MatchSession): String {
        val n = gridSize(session.precision)
        val sb = StringBuilder(n * n * n * 22)
        sb.append("# Anime LUT Match .cube\n")
        sb.append("# Working color space: sRGB (gamma-encoded), 8-bit input/output.\n")
        sb.append("TITLE \"").append(session.name.replace("\"", "")).append("\"\n")
        sb.append("LUT_3D_SIZE ").append(n).append("\n\n")
        val f = "%.6f"
        for (bi in 0 until n) for (gi in 0 until n) for (ri in 0 until n) {
            val c = session.applyTo(
                ri * 255f / (n - 1), gi * 255f / (n - 1), bi * 255f / (n - 1)
            )
            sb.append(String.format(Locale.US, f, c[0] / 255f)).append(' ')
                .append(String.format(Locale.US, f, c[1] / 255f)).append(' ')
                .append(String.format(Locale.US, f, c[2] / 255f)).append('\n')
        }
        return sb.toString()
    }

    /** HALD CLUT image (PNG when exported). Index order matches the standard hald-clut layout. */
    fun toHald(session: MatchSession): Bitmap {
        val L = haldLevel(session.precision)
        val W = L * L
        val px = IntArray(W * W)
        for (by in 0 until L) for (gy in 0 until L) for (ry in 0 until L) {
            val c = session.applyTo(ry * 255f / (L - 1), gy * 255f / (L - 1), by * 255f / (L - 1))
            // standard hald layout: b outer (rows of blocks), g block row, r within block
            val x = (by * L + ry) % W
            val y = (by * L + gy) % W
            px[y * W + x] = (0xff shl 24) or
                (c[0].toInt().coerceIn(0, 255) shl 16) or
                (c[1].toInt().coerceIn(0, 255) shl 8) or c[2].toInt().coerceIn(0, 255)
        }
        val bmp = Bitmap.createBitmap(W, W, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, W, 0, 0, W, W)
        return bmp
    }
}
