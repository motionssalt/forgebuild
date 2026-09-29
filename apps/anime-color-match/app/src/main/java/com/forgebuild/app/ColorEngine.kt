package com.forgebuild.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object ColorEngine {

    fun createLevelsLUT(config: LevelsChannelConfig): IntArray {
        val lut = IntArray(256)
        val inBlack = config.inputBlack
        val inWhite = config.inputWhite
        val gamma = max(0.01f, config.gamma)
        val outBlack = config.outputBlack
        val outWhite = config.outputWhite
        val inRange = max(1, inWhite - inBlack).toFloat()
        val outRange = (outWhite - outBlack).toFloat()
        val invGamma = 1.0f / gamma

        for (i in 0..255) {
            var norm = (i - inBlack).toFloat() / inRange
            norm = max(0f, min(1f, norm))
            val gammaVal = norm.toDouble().pow(invGamma.toDouble()).toFloat()
            val result = (outBlack + gammaVal * outRange).roundToInt()
            lut[i] = max(0, min(255, result))
        }
        return lut
    }

    fun createCurvesLUT(points: List<CurvePoint>): IntArray {
        val sorted = points.sortedBy { it.x }.toMutableList()
        if (sorted.isEmpty()) {
            sorted.add(CurvePoint(0f, 0f))
            sorted.add(CurvePoint(255f, 255f))
        }
        if (sorted.first().x > 0f) sorted.add(0, CurvePoint(0f, sorted.first().y))
        if (sorted.last().x < 255f) sorted.add(CurvePoint(255f, sorted.last().y))

        val lut = IntArray(256)
        for (i in 0..255) {
            val x = i.toFloat()
            // Piecewise linear / Hermite interpolation
            var matchedY = x
            for (p in 0 until sorted.size - 1) {
                val p1 = sorted[p]
                val p2 = sorted[p + 1]
                if (x >= p1.x && x <= p2.x) {
                    val range = max(1f, p2.x - p1.x)
                    val t = (x - p1.x) / range
                    matchedY = p1.y + t * (p2.y - p1.y)
                    break
                }
            }
            lut[i] = max(0, min(255, matchedY.roundToInt()))
        }
        return lut
    }

    fun evaluateColor(
        r: Int,
        g: Int,
        b: Int,
        layers: List<EffectLayer>,
        stageLimit: Int = 6
    ): Triple<Int, Int, Int> {
        var curR = r.toFloat()
        var curG = g.toFloat()
        var curB = b.toFloat()

        for (layer in layers) {
            if (!layer.enabled || layer.opacity <= 0) continue
            val mix = layer.opacity / 100f
            val initR = curR
            val initG = curG
            val initB = curB

            when (layer.type) {
                EffectType.AUTO_MATCH -> {
                    val data = layer.autoMatchData ?: continue
                    val stage = min(stageLimit, data.activeStage)

                    var trR = curR / 255f
                    var trG = curG / 255f
                    var trB = curB / 255f

                    // Stage 2+: Black pedestal
                    if (stage >= 2) {
                        trR = max(0f, trR + data.blackShift.first / 255f)
                        trG = max(0f, trG + data.blackShift.second / 255f)
                        trB = max(0f, trB + data.blackShift.third / 255f)
                    }

                    // Stage 3+: White gain
                    if (stage >= 3) {
                        trR = min(1f, trR * data.whiteShift.first)
                        trG = min(1f, trG * data.whiteShift.second)
                        trB = min(1f, trB * data.whiteShift.third)
                    }

                    // Stage 4+: Gamma and contrast
                    if (stage >= 4) {
                        trR = trR.toDouble().pow(1.0 / max(0.1f, data.midGamma.first).toDouble()).toFloat()
                        trG = trG.toDouble().pow(1.0 / max(0.1f, data.midGamma.second).toDouble()).toFloat()
                        trB = trB.toDouble().pow(1.0 / max(0.1f, data.midGamma.third).toDouble()).toFloat()

                        if (data.contrast != 1f) {
                            trR = (trR - 0.5f) * data.contrast + 0.5f
                            trG = (trG - 0.5f) * data.contrast + 0.5f
                            trB = (trB - 0.5f) * data.contrast + 0.5f
                        }
                    }

                    // Stage 5+: Saturation and hue rotation
                    if (stage >= 5) {
                        val luma = 0.2126f * trR + 0.7152f * trG + 0.0722f * trB
                        val satFactor = 1f + data.saturationShift
                        trR = luma + (trR - luma) * satFactor
                        trG = luma + (trG - luma) * satFactor
                        trB = luma + (trB - luma) * satFactor

                        if (data.hueShift != 0f) {
                            val rad = Math.toRadians(data.hueShift.toDouble()).toFloat()
                            val cosA = cos(rad)
                            val sinA = sin(rad)
                            val nr = trR * (0.299f + 0.701f * cosA + 0.168f * sinA) +
                                    trG * (0.587f - 0.587f * cosA + 0.330f * sinA) +
                                    trB * (0.114f - 0.114f * cosA - 0.497f * sinA)
                            val ng = trR * (0.299f - 0.299f * cosA - 0.328f * sinA) +
                                    trG * (0.587f + 0.413f * cosA + 0.035f * sinA) +
                                    trB * (0.114f - 0.114f * cosA + 0.292f * sinA)
                            val nb = trR * (0.299f - 0.300f * cosA + 1.250f * sinA) +
                                    trG * (0.587f - 0.588f * cosA - 1.050f * sinA) +
                                    trB * (0.114f + 0.886f * cosA - 0.203f * sinA)
                            trR = nr
                            trG = ng
                            trB = nb
                        }
                    }

                    curR = initR + (max(0f, min(255f, trR * 255f)) - initR) * mix
                    curG = initG + (max(0f, min(255f, trG * 255f)) - initG) * mix
                    curB = initB + (max(0f, min(255f, trB * 255f)) - initB) * mix
                }
                EffectType.LEVELS -> {
                    val data = layer.levelsData ?: continue
                    val rgbLut = createLevelsLUT(data.rgb)
                    val rLut = createLevelsLUT(data.red)
                    val gLut = createLevelsLUT(data.green)
                    val bLut = createLevelsLUT(data.blue)

                    var nR = rLut[curR.roundToInt().coerceIn(0, 255)]
                    var nG = gLut[curG.roundToInt().coerceIn(0, 255)]
                    var nB = bLut[curB.roundToInt().coerceIn(0, 255)]

                    nR = rgbLut[nR]
                    nG = rgbLut[nG]
                    nB = rgbLut[nB]

                    curR = initR + (nR - initR) * mix
                    curG = initG + (nG - initG) * mix
                    curB = initB + (nB - initB) * mix
                }
                EffectType.CURVES -> {
                    val data = layer.curvesData ?: continue
                    val rgbLut = createCurvesLUT(data.rgb)
                    val rLut = createCurvesLUT(data.red)
                    val gLut = createCurvesLUT(data.green)
                    val bLut = createCurvesLUT(data.blue)

                    var nR = rLut[curR.roundToInt().coerceIn(0, 255)]
                    var nG = gLut[curG.roundToInt().coerceIn(0, 255)]
                    var nB = bLut[curB.roundToInt().coerceIn(0, 255)]

                    nR = rgbLut[nR]
                    nG = rgbLut[nG]
                    nB = rgbLut[nB]

                    curR = initR + (nR - initR) * mix
                    curG = initG + (nG - initG) * mix
                    curB = initB + (nB - initB) * mix
                }
            }
        }

        return Triple(
            curR.roundToInt().coerceIn(0, 255),
            curG.roundToInt().coerceIn(0, 255),
            curB.roundToInt().coerceIn(0, 255)
        )
    }

    fun applyColorGradeToBitmap(
        source: Bitmap,
        layers: List<EffectLayer>,
        viewChannel: ViewChannel = ViewChannel.RGB,
        stageLimit: Int = 6
    ): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = Color.alpha(c)
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)

            val (cr, cg, cb) = evaluateColor(r, g, b, layers, stageLimit)

            val finalColor = when (viewChannel) {
                ViewChannel.RGB -> Color.argb(a, cr, cg, cb)
                ViewChannel.RED -> Color.argb(a, cr, (cr * 0.15f).toInt(), (cr * 0.15f).toInt())
                ViewChannel.GREEN -> Color.argb(a, (cg * 0.15f).toInt(), cg, (cg * 0.15f).toInt())
                ViewChannel.BLUE -> Color.argb(a, (cb * 0.15f).toInt(), (cb * 0.15f).toInt(), cb)
            }
            pixels[i] = finalColor
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    fun generateCubeLUT(layers: List<EffectLayer>, precision: LUTPrecision): String {
        val size = precision.size
        val sb = StringBuilder()
        sb.append("# ChromaMatch Anime Android Studio - 3D Cube LUT\n")
        sb.append("# Exported for Adobe Premiere, After Effects, DaVinci Resolve\n")
        sb.append("TITLE \"Anime_Color_Match_${precision.label}\"\n")
        sb.append("LUT_3D_SIZE $size\n")
        sb.append("DOMAIN_MIN 0.0 0.0 0.0\n")
        sb.append("DOMAIN_MAX 1.0 1.0 1.0\n\n")

        val step = 1.0f / (size - 1)
        for (b in 0 until size) {
            val inB = (b * step * 255f).roundToInt()
            for (g in 0 until size) {
                val inG = (g * step * 255f).roundToInt()
                for (r in 0 until size) {
                    val inR = (r * step * 255f).roundToInt()
                    val (outR, outG, outB) = evaluateColor(inR, inG, inB, layers)
                    val fr = String.format(java.util.Locale.US, "%.6f", outR / 255f)
                    val fg = String.format(java.util.Locale.US, "%.6f", outG / 255f)
                    val fb = String.format(java.util.Locale.US, "%.6f", outB / 255f)
                    sb.append("$fr $fg $fb\n")
                }
            }
        }
        return sb.toString()
    }

    fun generateHaldBitmap(layers: List<EffectLayer>, haldLevel: Int = 8): Bitmap {
        val size = haldLevel * haldLevel // 64
        val dimension = haldLevel * haldLevel * haldLevel // 512
        val bitmap = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(dimension * dimension)
        val scale = 255f / (size - 1)
        val numValues = size * size * size

        var idx = 0
        for (y in 0 until dimension) {
            for (x in 0 until dimension) {
                val cellIndex = y * dimension + x
                if (cellIndex >= numValues) break

                val rIndex = cellIndex % size
                val gIndex = (cellIndex / size) % size
                val bIndex = cellIndex / (size * size)

                val inR = (rIndex * scale).roundToInt()
                val inG = (gIndex * scale).roundToInt()
                val inB = (bIndex * scale).roundToInt()

                val (outR, outG, outB) = evaluateColor(inR, inG, inB, layers)
                pixels[idx++] = Color.argb(255, outR, outG, outB)
            }
        }
        bitmap.setPixels(pixels, 0, dimension, 0, 0, dimension, dimension)
        return bitmap
    }

    fun createSampleAnimeReferenceBitmap(width: Int = 480, height: Int = 270): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Golden Hour sunset gradient
        val sky = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.parseColor("#151C48"), Color.parseColor("#5C225A"), Color.parseColor("#E35F29"), Color.parseColor("#FFCC44")),
            null, Shader.TileMode.CLAMP)
        paint.shader = sky
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Sun
        paint.shader = null
        paint.color = Color.parseColor("#FFEAA7")
        canvas.drawCircle(width * 0.7f, height * 0.65f, 40f, paint)

        // Clouds
        paint.color = Color.parseColor("#FF7B54")
        canvas.drawOval(width * 0.3f, height * 0.4f, width * 0.55f, height * 0.55f, paint)

        // Skyline
        paint.color = Color.parseColor("#0F142B")
        for (i in 0 until 12) {
            val bw = width / 12f
            val bh = (sin(i * 1.5) * 0.15 + 0.35).toFloat() * height
            canvas.drawRect(i * bw, height - bh, (i + 1) * bw, height.toFloat(), paint)
        }
        return bitmap
    }

    fun createSampleAnimeTargetBitmap(width: Int = 480, height: Int = 270): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Flat raw footage (low contrast, gray balance)
        val sky = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.parseColor("#5A627A"), Color.parseColor("#7D8597"), Color.parseColor("#B0B5BE")),
            null, Shader.TileMode.CLAMP)
        paint.shader = sky
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Sun
        paint.shader = null
        paint.color = Color.parseColor("#CCCCCC")
        canvas.drawCircle(width * 0.7f, height * 0.65f, 30f, paint)

        // Clouds
        paint.color = Color.parseColor("#8E939D")
        canvas.drawOval(width * 0.3f, height * 0.4f, width * 0.55f, height * 0.55f, paint)

        // Skyline
        paint.color = Color.parseColor("#444857")
        for (i in 0 until 12) {
            val bw = width / 12f
            val bh = (sin(i * 1.5) * 0.15 + 0.35).toFloat() * height
            canvas.drawRect(i * bw, height - bh, (i + 1) * bw, height.toFloat(), paint)
        }
        return bitmap
    }
}
