@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.forgebuild.animelutmatch.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.animelutmatch.color.ColorMath
import com.forgebuild.animelutmatch.color.CurvesEffect
import com.forgebuild.animelutmatch.model.MatchSession
import com.forgebuild.animelutmatch.video.FrameMatcher
import com.forgebuild.animelutmatch.video.VideoFrameExtractor
import com.forgebuild.engine.security.ScreenSecurity
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.components.ExpressiveButton
import com.forgebuild.engine.ui.icons.EngineIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

sealed interface Screen {
    data object Home : Screen
    data object RefFrame : Screen
    data object TgtFrame : Screen
    data object Matching : Screen
    data object Editor : Screen
    data object Preview : Screen
}

private val CH_NAMES = listOf("RGB", "R", "G", "B")

/** Channel-isolated grayscale view (AE-style channel viewing). ch: 0=full,1=R,2=G,3=B. */
private fun channelView(src: Bitmap, ch: Int): Bitmap {
    if (ch == 0) return src
    val w = src.width; val h = src.height
    val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
    val shift = when (ch) { 1 -> 16; 2 -> 8; else -> 0 }
    for (i in px.indices) { val v = px[i] shr shift and 0xff; px[i] = (0xff shl 24) or (v shl 16) or (v shl 8) or v }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
}

private fun downscale(b: Bitmap, maxDim: Int): Bitmap {
    val s = maxDim.toFloat() / maxOf(b.width, b.height)
    if (s >= 1f) return b
    return Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
}

private fun applyCrop(b: Bitmap, crop: IntArray?): Bitmap {
    if (crop == null) return b
    val l = crop[0].coerceIn(0, b.width - 1); val t = crop[1].coerceIn(0, b.height - 1)
    val w = (crop[2] - l).coerceIn(1, b.width - l); val h = (crop[3] - t).coerceIn(1, b.height - t)
    return Bitmap.createBitmap(b, l, t, w, h)
}

@Composable
fun App(
    session: MatchSession,
    screen: Screen,
    onScreen: (Screen) -> Unit,
    refExtractor: VideoFrameExtractor?,
    tgtExtractor: VideoFrameExtractor?,
    onPickVideo: (Boolean) -> Unit,
    onExportCube: () -> Unit,
    onExportHald: () -> Unit,
) {
    val activity = LocalContext.current as? Activity
    // Per-screen FLAG_SECURE: frame/editor screens show the user's imported media (sensitive).
    LaunchedEffect(screen) {
        activity?.let { ScreenSecurity.setSecure(it, screen != Screen.Home) }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Anime LUT Match", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (screen != Screen.Home) IconButton(onClick = { onScreen(Screen.Home) }) {
                        Icon(EngineIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (screen) {
                Screen.Home -> HomeScreen(onPickVideo, refExtractor != null, tgtExtractor != null, onScreen)
                Screen.RefFrame -> FramePickScreen("Reference frame", refExtractor, onScreen) { ms, bmp ->
                    session.referenceFrame = bmp; session.touch(); onScreen(Screen.Home)
                }
                Screen.TgtFrame -> TargetFrameScreen(tgtExtractor, session, onScreen)
                Screen.Matching -> MatchingScreen(tgtExtractor, session, onScreen)
                Screen.Editor -> EditorScreen(session, onScreen, onExportCube, onExportHald)
                Screen.Preview -> PreviewScreen(session, tgtExtractor, onScreen)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onPickVideo: (Boolean) -> Unit,
    hasRef: Boolean,
    hasTgt: Boolean,
    onScreen: (Screen) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Color Match", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Copy the color grade from a reference edit onto your footage and export it as a .cube or HALD LUT.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Project setup", style = MaterialTheme.typography.titleMedium)
                ExpressiveButton(onClick = { onPickVideo(true) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(EngineIcons.Add, null); Spacer(Modifier.width(8.dp))
                    Text(if (hasRef) "Replace reference video" else "Import reference video")
                }
                ExpressiveButton(
                    onClick = { onPickVideo(false) }, enabled = hasRef, modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(EngineIcons.Add, null); Spacer(Modifier.width(8.dp))
                    Text(if (hasTgt) "Replace target video" else "Import target video")
                }
                if (hasRef && hasTgt) {
                    Button(onClick = { onScreen(Screen.Editor) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open color editor")
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Workflow", style = MaterialTheme.typography.titleMedium)
                listOf(
                    "1. Import the edit that has the color correction you want",
                    "2. Pick the exact reference frame",
                    "3. Import your footage; pick the matching frame or search a range",
                    "4. Auto Match, then refine with Levels / Curves (stackable)",
                    "5. Preview, then export .cube / HALD LUT",
                ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun FramePickScreen(
    title: String,
    extractor: VideoFrameExtractor?,
    onScreen: (Screen) -> Unit,
    onPicked: (Long, Bitmap) -> Unit,
) {
    if (extractor == null) { Text("No video imported"); return }
    var timeMs by remember { mutableStateOf(extractor.durationMs / 2) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val thumbs = remember { mutableStateListOf<Bitmap>() }
    LaunchedEffect(extractor) {
        withContext(Dispatchers.Default) {
            val n = 10
            for (i in 0 until n) {
                extractor.thumbAt(extractor.durationMs * i / (n - 1).coerceAtLeast(1))?.let {
                    withContext(Dispatchers.Main) { thumbs.add(it) }
                }
            }
        }
    }
    LaunchedEffect(timeMs) {
        busy = true
        frame = withContext(Dispatchers.Default) { extractor.scaledFrameAt(timeMs, 720) }
        busy = false
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
            frame?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxHeight()) }
                ?: EngineLinearWavyProgress()
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(thumbs) { Image(it.asImageBitmap(), null, modifier = Modifier.height(56.dp)) }
        }
        Text("Frame at ${timeMs / 1000.0}s of ${extractor.durationMs / 1000.0}s", style = MaterialTheme.typography.labelLarge)
        Slider(value = timeMs.toFloat(), onValueChange = { timeMs = it.toLong() },
            valueRange = 0f..extractor.durationMs.toFloat().coerceAtLeast(1f))
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    val full = withContext(Dispatchers.Default) { extractor.frameAt(timeMs) }
                    busy = false
                    if (full != null) onPicked(timeMs, full)
                }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
        ) { Text("Use this frame") }
    }
}

@Composable
private fun TargetFrameScreen(
    extractor: VideoFrameExtractor?,
    session: MatchSession,
    onScreen: (Screen) -> Unit,
) {
    if (extractor == null) { Text("No video imported"); return }
    var mode by remember { mutableStateOf(0) } // 0 exact, 1 range
    var startMs by remember { mutableStateOf(0L) }
    var endMs by remember { mutableStateOf(extractor.durationMs) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Target frame", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Exact frame") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Search range") })
        }
        if (mode == 0) {
            FramePickScreen("Pick the exact corresponding frame", extractor, onScreen) { ms, bmp ->
                session.targetFrame = bmp; session.targetCrop = null; session.matchConfidence = 1f
                session.touch(); onScreen(Screen.Editor)
            }
        } else {
            Text("Search start: ${startMs / 1000.0}s", style = MaterialTheme.typography.labelLarge)
            Slider(value = startMs.toFloat(), onValueChange = { startMs = it.toLong().coerceAtMost(endMs) },
                valueRange = 0f..extractor.durationMs.toFloat().coerceAtLeast(1f))
            Text("Search end: ${endMs / 1000.0}s", style = MaterialTheme.typography.labelLarge)
            Slider(value = endMs.toFloat(), onValueChange = { endMs = it.toLong().coerceAtLeast(startMs) },
                valueRange = 0f..extractor.durationMs.toFloat().coerceAtLeast(1f))
            Button(onClick = {
                session.matchRange = startMs to endMs
                onScreen(Screen.Matching)
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(EngineIcons.Search, null); Spacer(Modifier.width(8.dp)); Text("Find matching frame")
            }
        }
    }
}

@Composable
private fun MatchingScreen(
    extractor: VideoFrameExtractor?,
    session: MatchSession,
    onScreen: (Screen) -> Unit,
) {
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<FrameMatcher.Result?>(null) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val ref = session.referenceFrame
        if (extractor == null || ref == null) { done = true; return@LaunchedEffect }
        val (s, e) = session.matchRange ?: (0L to extractor.durationMs)
        result = withContext(Dispatchers.Default) {
            FrameMatcher.findMatch(extractor, downscale(ref, 256), s, e) { progress = it }
        }
        done = true
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Frame matching", style = MaterialTheme.typography.titleMedium)
        if (!done) {
            EngineLinearWavyProgress(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("Scanning range… ${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        } else {
            val r = result
            if (r == null) {
                Text("Unable to confidently identify a matching frame.", color = MaterialTheme.colorScheme.error)
                Text("Try: expanding the search range, selecting the frame manually, or choosing a different reference frame.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onScreen(Screen.TgtFrame) }) { Text("Back") }
            } else {
                val conf = (r.confidence * 1000).roundToInt() / 10f
                if (r.confidence < 0.55f) {
                    Text("Low confidence match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                }
                Text("Best match: ${r.timeMs / 1000.0}s   Confidence: $conf%", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        val f = extractor?.frameAt(r.timeMs)
                        if (f != null) {
                            // crop is expressed on a 256px analysis frame; scale to the real frame
                            val crop = r.crop?.let { c ->
                                val sx = f.width / 256f
                                val sy = f.height / ((f.height * 256f / f.width).coerceAtLeast(1f))
                                intArrayOf((c[0] * sx).toInt(), (c[1] * sy).toInt(), (c[2] * sx).toInt(), (c[3] * sy).toInt())
                            }
                            session.targetFrame = f; session.targetCrop = crop; session.matchConfidence = r.confidence
                            session.touch(); onScreen(Screen.Editor)
                        }
                    }) { Text("Accept match") }
                    OutlinedButton(onClick = { onScreen(Screen.TgtFrame) }) { Text("Find another") }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(
    session: MatchSession,
    onScreen: (Screen) -> Unit,
    onExportCube: () -> Unit,
    onExportHald: () -> Unit,
) {
    var precision by remember { mutableStateOf(session.precision) }
    var channel by remember { mutableStateOf(0) }     // view channel 0..3
    var fxChannel by remember { mutableStateOf(0) }   // effect channel 0..3
    var fxTab by remember { mutableStateOf(0) }       // 0 Levels, 1 Curves
    var autoBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Base still for real-time preview (cropped + downscaled; never touches the source video)
    val base = remember(session.targetFrame, session.targetCrop) {
        session.targetFrame?.let { downscale(applyCrop(it, session.targetCrop), 512) }
    }
    val rendered by produceState<Bitmap?>(null, base, session.revision) {
        value = base?.let { b -> withContext(Dispatchers.Default) { session.renderPreview(b) } }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Reference / Target comparison with channel-isolated view
        Text("Reference vs Target", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CH_NAMES.forEachIndexed { i, n ->
                FilterChip(selected = channel == i, onClick = { channel = i }, label = { Text(n) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            session.referenceFrame?.let {
                Image(channelView(downscale(applyCrop(it, null), 512), channel).asImageBitmap(), "Reference",
                    modifier = Modifier.weight(1f).height(160.dp))
            }
            (rendered ?: base)?.let {
                Image(channelView(it, channel).asImageBitmap(), "Target",
                    modifier = Modifier.weight(1f).height(160.dp))
            }
        }

        Text("Precision", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MatchSession.Precision.entries.forEach { p ->
                FilterChip(selected = precision == p, onClick = { precision = p; session.precision = p; session.touch() },
                    label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        Text(
            when (precision) {
                MatchSession.Precision.LOW -> "17³ .cube / HALD level 8 — fastest, quick previews"
                MatchSession.Precision.STANDARD -> "33³ .cube / HALD level 12 — balanced accuracy and speed"
                MatchSession.Precision.HIGH -> "64³ .cube / HALD level 16 — most faithful, slower export"
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ExpressiveButton(
            onClick = {
                scope.launch {
                    autoBusy = true
                    withContext(Dispatchers.Default) {
                        val ref = session.referenceFrame?.let { downscale(it, 256) }
                        val tgt = base
                        if (ref != null && tgt != null) {
                            val refPx = IntArray(ref.width * ref.height).also { ref.getPixels(it, 0, ref.width, 0, 0, ref.width, ref.height) }
                            val tgtScaled = Bitmap.createScaledBitmap(tgt, ref.width, ref.height, true)
                            val tgtPx = IntArray(tgtScaled.width * tgtScaled.height).also { tgtScaled.getPixels(it, 0, tgtScaled.width, 0, 0, tgtScaled.width, tgtScaled.height) }
                            val luts = ColorMath.autoTransfer(ColorMath.samplePixels(refPx), ColorMath.samplePixels(tgtPx))
                            session.stack.removeAll { it is MatchSession.Effect.AutoMatch }
                            session.stack.add(0, MatchSession.Effect.AutoMatch(luts))
                            session.touch()
                        }
                    }
                    autoBusy = false
                }
            },
            busy = autoBusy, enabled = session.referenceFrame != null && base != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(EngineIcons.Bolt, null); Spacer(Modifier.width(8.dp)); Text("Auto Match") }

        // Correction stack (non-destructive: toggle / reorder / duplicate / delete)
        Text("Correction stack", style = MaterialTheme.typography.titleSmall)
        session.stack.forEachIndexed { idx, e ->
            val label = when (e) {
                is MatchSession.Effect.AutoMatch -> "Auto Match"
                is MatchSession.Effect.Levels -> "Levels ${idx + 1}"
                is MatchSession.Effect.Curves -> "Curves ${idx + 1}"
            }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = e.enabled, onCheckedChange = { e.enabled = it; session.touch() })
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { if (idx > 0) { session.stack.add(idx - 1, session.stack.removeAt(idx)); session.touch() } }) { Text("↑") }
                    IconButton(onClick = { if (idx < session.stack.size - 1) { session.stack.add(idx + 1, session.stack.removeAt(idx)); session.touch() } }) { Text("↓") }
                    IconButton(onClick = { session.stack.add(idx + 1, e.copyEffect()); session.touch() }) { Icon(EngineIcons.Add, "Duplicate") }
                    if (e !is MatchSession.Effect.AutoMatch) {
                        IconButton(onClick = { session.stack.removeAt(idx); session.touch() }) { Text("✕") }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { session.stack.add(MatchSession.Effect.Levels()); session.touch() }) { Text("+ Levels") }
            OutlinedButton(onClick = { session.stack.add(MatchSession.Effect.Curves()); session.touch() }) { Text("+ Curves") }
        }

        // Levels / Curves editor bound to the last effect of the selected type
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = fxTab == 0, onClick = { fxTab = 0 }, label = { Text("Levels") })
            FilterChip(selected = fxTab == 1, onClick = { fxTab = 1 }, label = { Text("Curves") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CH_NAMES.forEachIndexed { i, n ->
                FilterChip(selected = fxChannel == i, onClick = { fxChannel = i }, label = { Text(n) })
            }
        }
        if (fxTab == 0) {
            val fx = session.stack.filterIsInstance<MatchSession.Effect.Levels>().lastOrNull()?.fx
            if (fx == null) Text("Add a Levels effect to edit it.", style = MaterialTheme.typography.bodySmall)
            else LevelsEditor(fx, fxChannel, session)
        } else {
            val fx = session.stack.filterIsInstance<MatchSession.Effect.Curves>().lastOrNull()?.fx
            if (fx == null) Text("Add a Curves effect to edit it.", style = MaterialTheme.typography.bodySmall)
            else CurvesEditor(fx, fxChannel, session)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onScreen(Screen.Preview) }) { Text("Preview video") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onExportCube) { Text("Export .cube") }
            Button(onClick = onExportHald) { Text("Export HALD") }
            OutlinedButton(onClick = { onExportCube(); onExportHald() }) { Text("Both") }
        }
    }
}

@Composable
private fun LevelsEditor(fx: com.forgebuild.animelutmatch.color.LevelsEffect, channel: Int, session: MatchSession) {
    val p = fx.channels[channel]
    fun set(np: com.forgebuild.animelutmatch.color.LevelsParams) { fx.channels[channel] = np; session.touch() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            Triple("Input black", p.inBlack, 0f..254f) to { v: Float -> set(p.copy(inBlack = v.coerceAtMost(p.inWhite - 1))) },
            Triple("Input white", p.inWhite, 1f..255f) to { v: Float -> set(p.copy(inWhite = v.coerceAtLeast(p.inBlack + 1))) },
            Triple("Gamma", p.gamma, 0.2f..5f) to { v: Float -> set(p.copy(gamma = v)) },
            Triple("Output black", p.outBlack, 0f..255f) to { v: Float -> set(p.copy(outBlack = v)) },
            Triple("Output white", p.outWhite, 0f..255f) to { v: Float -> set(p.copy(outWhite = v)) },
        ).forEach { (spec, apply) ->
            Text("${spec.first}: ${(spec.second * 10).roundToInt() / 10f}", style = MaterialTheme.typography.labelMedium)
            Slider(value = spec.second, onValueChange = apply, valueRange = spec.third)
        }
        TextButton(onClick = {
            fx.channels[channel] = com.forgebuild.animelutmatch.color.LevelsParams(); session.touch()
        }) { Text("Reset channel") }
    }
}

@Composable
private fun CurvesEditor(fx: CurvesEffect, channel: Int, session: MatchSession) {
    val pts = fx.channels[channel]
    val undo = remember { mutableStateListOf<List<Pair<Float, Float>>>() }
    var dragIdx by remember { mutableStateOf(-1) }
    val size = 240.dp
    val grid = MaterialTheme.colorScheme.outlineVariant
    val curveColor = MaterialTheme.colorScheme.primary
    val diag = MaterialTheme.colorScheme.outline
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier.size(size).pointerInput(channel) {
                detectDragGestures(
                    onDragStart = { off ->
                        undo.add(pts.toList())
                        val x = off.x / this.size.width * 255f
                        val y = 255f - off.y / this.size.height * 255f
                        var best = -1; var bd = 30f * 30f
                        pts.forEachIndexed { i, p ->
                            val d = (p.first - x) * (p.first - x) + (p.second - y) * (p.second - y)
                            if (d < bd && i != 0 && i != pts.size - 1) { bd = d; best = i }
                        }
                        if (best < 0) {
                            pts.add(x to y); pts.sortBy { it.first }; best = pts.indexOfFirst { it.first == x }
                        }
                        dragIdx = best
                    },
                    onDrag = { change, _ ->
                        if (dragIdx in 1 until pts.size - 1) {
                            val nx = (change.position.x / this.size.width * 255f).coerceIn(pts[dragIdx - 1].first + 1f, pts[dragIdx + 1].first - 1f)
                            val ny = (255f - change.position.y / this.size.height * 255f).coerceIn(0f, 255f)
                            pts[dragIdx] = nx to ny
                            fx.invalidate(); session.touch()
                        }
                        change.consume()
                    },
                )
            },
        ) {
            val w = this.size.width; val h = this.size.height
            for (i in 0..4) {
                drawLine(grid, Offset(w * i / 4, 0f), Offset(w * i / 4, h))
                drawLine(grid, Offset(0f, h * i / 4), Offset(w, h * i / 4))
            }
            drawLine(diag, Offset(0f, h), Offset(w, 0f))
            val sorted = pts.sortedBy { it.first }
            for (i in 0 until sorted.size - 1) {
                drawLine(
                    curveColor,
                    Offset(sorted[i].first / 255f * w, h - sorted[i].second / 255f * h),
                    Offset(sorted[i + 1].first / 255f * w, h - sorted[i + 1].second / 255f * h),
                    strokeWidth = 4f,
                )
            }
            sorted.forEach { drawCircle(curveColor, 7f, Offset(it.first / 255f * w, h - it.second / 255f * h)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                if (undo.isNotEmpty()) { pts.clear(); pts.addAll(undo.removeLast()); fx.invalidate(); session.touch() }
            }) { Text("Undo") }
            TextButton(onClick = {
                undo.add(pts.toList())
                pts.clear(); pts.add(0f to 0f); pts.add(255f to 255f)
                fx.invalidate(); session.touch()
            }) { Text("Reset") }
        }
    }
}

@Composable
private fun PreviewScreen(session: MatchSession, extractor: VideoFrameExtractor?, onScreen: (Screen) -> Unit) {
    // Proxy video preview: corrected thumbnail strip flipbook (never re-decodes the whole video).
    var playing by remember { mutableStateOf(false) }
    var after by remember { mutableStateOf(true) }
    var pos by remember { mutableStateOf(0) }
    val frames = remember { mutableStateListOf<Pair<Bitmap, Bitmap>>() } // before, after
    LaunchedEffect(extractor, session.revision) {
        if (extractor == null) return@LaunchedEffect
        frames.clear()
        withContext(Dispatchers.Default) {
            val n = 24
            for (i in 0 until n) {
                val t = extractor.durationMs * i / (n - 1)
                val f = extractor.scaledFrameAt(t, 384) ?: continue
                val corrected = session.renderPreview(f.copy(f.config ?: Bitmap.Config.ARGB_8888, false))
                withContext(Dispatchers.Main) { frames.add(f to corrected) }
            }
        }
    }
    LaunchedEffect(playing, frames.size) {
        while (playing && frames.isNotEmpty()) { delay(200); pos = (pos + 1) % frames.size }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Video preview (proxy)", style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
            if (frames.isEmpty()) EngineLinearWavyProgress()
            else Image((if (after) frames[pos].second else frames[pos].first).asImageBitmap(), null, modifier = Modifier.fillMaxHeight())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { playing = !playing }, enabled = frames.isNotEmpty()) { Text(if (playing) "Pause" else "Play") }
            FilterChip(selected = after, onClick = { after = !after }, label = { Text(if (after) "AFTER" else "BEFORE") })
        }
        if (frames.isNotEmpty()) {
            Slider(value = pos.toFloat(), onValueChange = { pos = it.toInt().coerceIn(0, frames.size - 1) },
                valueRange = 0f..(frames.size - 1).toFloat())
        }
        OutlinedButton(onClick = { onScreen(Screen.Editor) }) { Text("Back to editor") }
    }
}
