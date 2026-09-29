package com.forgebuild.app

import android.graphics.Bitmap

enum class LUTPrecision(val size: Int, val label: String, val pointCount: Int) {
    LOW(17, "17³ Low", 4913),
    STANDARD(33, "33³ Std", 35937),
    HIGH(65, "65³ High", 274625)
}

enum class ChannelType {
    RGB, RED, GREEN, BLUE
}

enum class ViewChannel {
    RGB, RED, GREEN, BLUE
}

enum class EffectType {
    AUTO_MATCH, LEVELS, CURVES
}

data class LevelsChannelConfig(
    val inputBlack: Int = 0,      // 0..255
    val inputWhite: Int = 255,    // 0..255
    val gamma: Float = 1.0f,      // 0.1..10.0
    val outputBlack: Int = 0,     // 0..255
    val outputWhite: Int = 255    // 0..255
)

data class LevelsData(
    val rgb: LevelsChannelConfig = LevelsChannelConfig(),
    val red: LevelsChannelConfig = LevelsChannelConfig(),
    val green: LevelsChannelConfig = LevelsChannelConfig(),
    val blue: LevelsChannelConfig = LevelsChannelConfig()
)

data class CurvePoint(
    val x: Float, // 0..255
    val y: Float  // 0..255
)

data class CurvesData(
    val rgb: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(255f, 255f)),
    val red: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(255f, 255f)),
    val green: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(255f, 255f)),
    val blue: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(255f, 255f))
)

data class AutoMatchData(
    val blackShift: Triple<Int, Int, Int> = Triple(0, 0, 0),
    val whiteShift: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
    val midGamma: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
    val saturationShift: Float = 0f,
    val hueShift: Float = 0f,
    val contrast: Float = 1f,
    val activeStage: Int = 6
)

data class EffectLayer(
    val id: String,
    val name: String,
    val type: EffectType,
    val enabled: Boolean = true,
    val opacity: Int = 100, // 0..100%
    val levelsData: LevelsData? = null,
    val curvesData: CurvesData? = null,
    val autoMatchData: AutoMatchData? = null
)

data class WorkflowStage(
    val id: Int,
    val name: String,
    val shortName: String,
    val description: String,
    val metrics: Map<String, String>,
    val durationMs: Long
)

data class ProgressionLog(
    val id: String,
    val timestamp: String,
    val stageId: Int,
    val level: String,
    val message: String
)

data class CandidateFrame(
    val timestamp: Float,
    val confidence: Float,
    val coarseScore: Int,
    val featureScore: Int,
    val spatialScore: Int
)

data class MediaSource(
    val name: String,
    val bitmap: Bitmap,
    val isDirectImage: Boolean = true
)
