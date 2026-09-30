@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.forgebuild.animelutmatch.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.forgebuild.animelutmatch.media.MediaSource
import com.forgebuild.animelutmatch.model.MatchSession
import com.forgebuild.animelutmatch.video.FrameMatcher
import com.forgebuild.animelutmatch.video.VideoFrameExtractor
import com.forgebuild.engine.security.ScreenSecurity
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.components.ExpressiveButton
import com.forgebuild.engine.ui.icons.EngineIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
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

/** Apply partial per-channel LUTs to a still (live staged preview while Auto Match runs). */
private fun applyLuts(src: Bitmap, luts: Array<FloatArray>): Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
    for (i in px.indices) {
        val c = px[i]
        val r = luts[0][c shr 16 and 0xff].toInt().coerceIn(0, 255)
        val g = luts[1][c shr 8 and 0xff].toInt().coerceIn(0, 255)
        val b = luts[2][c and 0xff].toInt().coerceIn(0, 255)
        px[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
}

@Composable
fun App(
    session: MatchSession,
    screen: Screen,
    onScreen: (Screen) -> Unit,
    refSource: MediaSource?,
    tgtSource: MediaSource?,
    onPickMedia: (Boolean) -> Unit,
    onNewProject: () -> Unit,
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
                Screen.Home -> HomeScreen(session, onPickMedia, refSource, tgtSource, onNewProject, onScreen)
                Screen.RefFrame -> FramePickScreen("Reference frame", refSource) { _, bmp ->
                    session.referenceFrame = bmp
                    session.logLine("Reference frame locked in (${bmp.width}×${bmp.height})")
                    session.touch(); onScreen(Screen.Home)
                }
                Screen.TgtFrame -> TargetFrameScreen(tgtSource, session, onScreen)
                Screen.Matching -> MatchingScreen((tgtSource as? MediaSource.Video)?.extractor, session, onScreen)
                Screen.Editor -> EditorScreen(session, onScreen, onExportCube, onExportHald)
                Screen.Preview -> PreviewScreen(session, tgtSource, onScreen)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    session: MatchSession,
    onPickMedia: (Boolean) -> Unit,
    refSource: MediaSource?,
    tgtSource: MediaSource?,
    onNewProject: () -> Unit,
    onScreen: (Screen) -> Unit,
) {
    // Reading revision keeps the frame status lines live as frames get locked in.
    val rev = session.revision
    val refFrameReady = rev >= 0 && session.referenceFrame != null
    val tgtFrameReady = session.targetFrame != null
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Color Match", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Copy the color grade from a reference edit onto your footage and export it as a .cube or HALD LUT. Video or still image on either side.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Project setup", style = MaterialTheme.typography.titleMedium)
                ExpressiveButton(onClick = { onPickMedia(true) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(EngineIcons.Add, null); Spacer(Modifier.width(8.dp))
                    Text(if (refSource != null) "Replace reference" else "Import reference video or image")
                }
                refSource?.let {
                    Text("Reference: ${it.describe()}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ExpressiveButton(
                    onClick = { onPickMedia(false) }, enabled = refSource != null, modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(EngineIcons.Add, null); Spacer(Modifier.width(8.dp))
                    Text(if (tgtSource != null) "Replace target" else "Import target video or image")
                }
                tgtSource?.let {
                    Text("Target: ${it.describe()}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (refSource != null || tgtSource != null) {
                    OutlinedButton(onClick = onNewProject, modifier = Modifier.fillMaxWidth()) {
                        Text("Start new project (clear everything)")
                    }
                }
            }
        }

        // Frames step — always reachable (v3 fix: frame picking / frame search used to be
        // unreachable once you left those screens, which made matching look broken).
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Frames", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = { onScreen(Screen.RefFrame) }, enabled = refSource != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (refFrameReady) "Change reference frame" else "Pick reference frame") }
                Text(
                    when {
                        refSource == null -> "Import a reference first."
                        refFrameReady -> "Reference frame locked in ✓"
                        else -> "No reference frame yet — Auto Match needs it."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (refFrameReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                OutlinedButton(
                    onClick = { onScreen(Screen.TgtFrame) }, enabled = tgtSource != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (tgtFrameReady) "Change target frame / re-run matching" else "Pick target frame or find matching frame") }
                Text(
                    when {
                        tgtSource == null -> "Import your footage or image first."
                        tgtFrameReady -> "Target frame locked in ✓"
                        else -> "No target frame yet."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (tgtFrameReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = { onScreen(Screen.Editor) },
                    enabled = refFrameReady && tgtFrameReady,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open color editor") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Workflow", style = MaterialTheme.typography.titleMedium)
                listOf(
                    "1. Import the edit or still that has the color correction you want",
                    "2. Pick the exact reference frame (or use the imported image as-is)",
                    "3. Import your footage/image; pick the matching frame or search a range",
                    "4. Auto Match (watch the live log), then refine with Levels / Curves",
                    "5. Preview, then export .cube / HALD LUT",
                ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun FramePickScreen(
    title: String,
    source: MediaSource?,
    onPicked: (Long, Bitmap) -> Unit,
) {
    when (source) {
        null -> Text("Nothing imported yet")
        is MediaSource.Image -> {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                    Image(source.bitmap.asImageBitmap(), null, modifier = Modifier.fillMaxHeight())
                }
                Text("Still image imported — it is used as the frame directly.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onPicked(0L, source.bitmap) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Use this image")
                }
            }
        }
        is MediaSource.Video -> VideoFramePick(title, source.extractor, onPicked)
    }
}

@Composable
private fun VideoFramePick(
    title: String,
    extractor: VideoFrameExtractor,
    onPicked: (Long, Bitmap) -> Unit,
) {
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
    source: MediaSource?,
    session: MatchSession,
    onScreen: (Screen) -> Unit,
) {
    when (source) {
        null -> Text("No target imported yet")
        is MediaSource.Image -> FramePickScreen("Target frame", source) { _, bmp ->
            session.targetFrame = bmp; session.targetCrop = null; session.matchConfidence = 1f
            session.logLine("Target image locked in (${bmp.width}×${bmp.height})")
            session.touch(); onScreen(Screen.Editor)
        }
        is MediaSource.Video -> {
            val extractor = source.extractor
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
                    VideoFramePick("Pick the exact corresponding frame", extractor) { ms, bmp ->
                        session.targetFrame = bmp; session.targetCrop = null; session.matchConfidence = 1f
                        session.logLine("Target frame picked at ${ms / 1000.0}s")
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
                        session.logLine("Frame search queued: ${startMs / 1000.0}s – ${endMs / 1000.0}s")
                        onScreen(Screen.Matching)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(EngineIcons.Search, null); Spacer(Modifier.width(8.dp)); Text("Find matching frame")
                    }
                }
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
    var accepting by remember { mutableStateOf(false) }
    // No reference frame = matching cannot run; say so instead of reporting a failed search.
    val ref = session.referenceFrame
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ref, extractor) {
        if (extractor == null || ref == null) { done = true; return@LaunchedEffect }
        val (s, e) = session.matchRange ?: (0L to extractor.durationMs)
        session.logLine("Frame matching started over ${s / 1000.0}s – ${e / 1000.0}s")
        val r = withContext(Dispatchers.Default) {
            FrameMatcher.findMatch(extractor, downscale(ref, 256), s, e,
                onProgress = { progress = it },
                onLog = { session.logLine(it) })
        }
        result = r
        if (r != null) preview = withContext(Dispatchers.Default) { extractor.scaledFrameAt(r.timeMs, 512) }
        done = true
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Frame matching", style = MaterialTheme.typography.titleMedium)
        if (ref == null) {
            Text("No reference frame is locked in yet.", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall)
            Text("Pick the reference frame first — matching compares your footage against it.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onScreen(Screen.RefFrame) }) { Text("Pick reference frame") }
        } else if (!done) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(downscale(ref, 512).asImageBitmap(), "Reference",
                        modifier = Modifier.weight(1f).height(150.dp))
                    preview?.let { Image(it.asImageBitmap(), "Matched frame", modifier = Modifier.weight(1f).height(150.dp)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = !accepting,
                        onClick = {
                            scope.launch {
                                accepting = true
                                val f = withContext(Dispatchers.Default) { extractor?.frameAt(r.timeMs) }
                                if (f != null) {
                                    // crop comes back normalized (0..1) — map straight onto the real frame
                                    val crop = r.crop?.let { c ->
                                        intArrayOf(
                                            (c[0] * f.width).toInt().coerceIn(0, f.width - 1),
                                            (c[1] * f.height).toInt().coerceIn(0, f.height - 1),
                                            (c[2] * f.width).toInt().coerceIn(1, f.width),
                                            (c[3] * f.height).toInt().coerceIn(1, f.height),
                                        )
                                    }
                                    session.targetFrame = f; session.targetCrop = crop; session.matchConfidence = r.confidence
                                    session.logLine("Match accepted at ${r.timeMs / 1000.0}s (${f.width}×${f.height})")
                                    session.touch(); onScreen(Screen.Editor)
                                } else {
                                    session.logLine("Could not decode the matched frame at full resolution")
                                }
                                accepting = false
                            }
                        },
                    ) { Text(if (accepting) "Loading…" else "Accept match") }
                    OutlinedButton(onClick = { onScreen(Screen.TgtFrame) }) { Text("Find another") }
                }
            }
        }
        LogCard(session)
    }
}

/** Live processing log — observable list, auto-follows the newest line. */
@Composable
private fun LogCard(session: MatchSession, maxLines: Int = 6) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Processing log", style = MaterialTheme.typography.titleSmall)
            val lines = session.log.toList().takeLast(maxLines)
            if (lines.isEmpty()) {
                Text("Nothing yet — imports, frame matching and Auto Match report here.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(session.log.size) {
                    if (session.log.isNotEmpty()) listState.animateScrollToItem(session.log.size - 1)
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(132.dp)) {
                    items(lines) {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
    // Reading revision here makes EVERY control live: any session.touch() recomposes the editor.
    val rev = session.revision
    var precision by remember { mutableStateOf(session.precision) }
    var channel by remember { mutableStateOf(0) }     // view channel 0..3
    var fxChannel by remember { mutableStateOf(0) }   // effect channel 0..3
    var selectedFx by remember { mutableStateOf<Int?>(null) }
    var autoBusy by remember { mutableStateOf(false) }
    var autoStage by remember { mutableStateOf<Bitmap?>(null) }
    var autoCaption by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Base still for real-time preview (cropped + downscaled; never touches the source media)
    val base = remember(session.targetFrame, session.targetCrop) {
        session.targetFrame?.let { downscale(applyCrop(it, session.targetCrop), 512) }
    }
    val rendered by produceState<Bitmap?>(null, base, rev) {
        value = base?.let { b -> withContext(Dispatchers.Default) { session.renderPreview(b) } }
    }

    val refFrame = session.referenceFrame
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Missing-frame guidance: the editor can't match anything without both frames, and the
        // user must be able to go and get them from here (v3 fix).
        if (refFrame == null || base == null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Frames missing", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        listOfNotNull(
                            if (refFrame == null) "reference frame" else null,
                            if (base == null) "target frame" else null,
                        ).joinToString(" and ") + " not locked in yet — Auto Match stays disabled until both are set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (refFrame == null) {
                            Button(onClick = { onScreen(Screen.RefFrame) }) { Text("Pick reference frame") }
                        }
                        if (base == null) {
                            Button(onClick = { onScreen(Screen.TgtFrame) }) { Text("Pick target frame") }
                        }
                    }
                }
            }
        }

        // Reference / Target comparison with channel-isolated view
        Text("Reference vs Target", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CH_NAMES.forEachIndexed { i, n ->
                FilterChip(selected = channel == i, onClick = { channel = i }, label = { Text(n) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Reference", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (refFrame != null) {
                    Image(channelView(downscale(refFrame, 512), channel).asImageBitmap(), "Reference frame",
                        modifier = Modifier.fillMaxWidth().height(160.dp))
                } else {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Text("No reference frame", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Target", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val shown = rendered ?: base
                if (shown != null) {
                    Image(channelView(shown, channel).asImageBitmap(), "Target frame",
                        modifier = Modifier.fillMaxWidth().height(160.dp))
                } else {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Text("No target frame", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
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

        // Auto Match — staged, visible, logged per channel (operator requirement)
        ExpressiveButton(
            onClick = {
                scope.launch {
                    autoBusy = true; autoStage = null
                    session.stack.removeAll { it is MatchSession.Effect.AutoMatch }
                    session.logLine("Auto Match started — analyzing reference vs target per channel")
                    val ref = session.referenceFrame?.let { downscale(it, 256) }
                    val tgt = base
                    if (ref == null || tgt == null) {
                        session.logLine("Auto Match aborted: need both a reference and a target frame")
                        autoBusy = false; return@launch
                    }
                    val sampled = withContext(Dispatchers.Default) {
                        val refPx = IntArray(ref.width * ref.height).also { ref.getPixels(it, 0, ref.width, 0, 0, ref.width, ref.height) }
                        val tgtScaled = Bitmap.createScaledBitmap(tgt, ref.width, ref.height, true)
                        val tgtPx = IntArray(tgtScaled.width * tgtScaled.height).also { tgtScaled.getPixels(it, 0, tgtScaled.width, 0, 0, tgtScaled.width, tgtScaled.height) }
                        ColorMath.samplePixels(refPx) to ColorMath.samplePixels(tgtPx)
                    }
                    session.logLine("Sampled ${sampled.first.first.size} pixels per channel (stride sampling)")
                    val chNames = listOf("Red", "Green", "Blue")
                    val luts = arrayOf(ColorMath.identityLut(), ColorMath.identityLut(), ColorMath.identityLut())
                    for (c in 0..2) {
                        autoCaption = "Analyzing ${chNames[c]} channel…"
                        val rep = withContext(Dispatchers.Default) {
                            val tgtCh = when (c) { 0 -> sampled.second.first; 1 -> sampled.second.second; else -> sampled.second.third }
                            val refCh = when (c) { 0 -> sampled.first.first; 1 -> sampled.first.second; else -> sampled.first.third }
                            ColorMath.channelCurve(tgtCh, refCh, chNames[c])
                        }
                        luts[c] = rep.lut
                        session.logLine(String.format(Locale.US,
                            "%s: black %.0f→%.0f · white %.0f→%.0f · gamma %.2f",
                            rep.name, rep.inBlack, rep.outBlack, rep.inWhite, rep.outWhite, rep.gamma))
                        // live stage: show the partial correction on the target still
                        autoStage = withContext(Dispatchers.Default) { applyLuts(tgt, luts) }
                        session.logLine("${chNames[c]} channel adjusted")
                        delay(350) // keep each stage perceptible instead of an instant jump
                    }
                    session.stack.add(0, MatchSession.Effect.AutoMatch(luts))
                    session.touch()
                    session.logLine("Auto Match applied — added to the correction stack")
                    autoCaption = ""; autoBusy = false
                }
            },
            busy = autoBusy, enabled = !autoBusy && session.referenceFrame != null && base != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(EngineIcons.Bolt, null); Spacer(Modifier.width(8.dp)); Text("Auto Match") }

        // Staged auto-match preview (visible per-channel adjustment before the result lands)
        autoStage?.let {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(autoCaption, style = MaterialTheme.typography.labelLarge)
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Image(it.asImageBitmap(), "Auto Match stage", modifier = Modifier.fillMaxHeight())
                }
            }
        }

        LogCard(session)

        // Correction stack (non-destructive: toggle / reorder / duplicate / delete; tap to edit)
        Text("Correction stack", style = MaterialTheme.typography.titleSmall)
        val stackSnapshot = session.stack.toList()
        stackSnapshot.forEachIndexed { idx, e ->
            val label = when (e) {
                is MatchSession.Effect.AutoMatch -> "Auto Match"
                is MatchSession.Effect.Levels -> "Levels ${idx + 1}"
                is MatchSession.Effect.Curves -> "Curves ${idx + 1}"
            }
            val isSelected = (selectedFx ?: (stackSnapshot.size - 1)) == idx
            Card(
                Modifier.fillMaxWidth().clickable { selectedFx = idx },
                colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                else CardDefaults.cardColors(),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = e.enabled, onCheckedChange = { e.enabled = it; session.touch() })
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { if (idx > 0) { session.stack.add(idx - 1, session.stack.removeAt(idx)); selectedFx = idx - 1; session.touch() } }) { Text("↑") }
                    IconButton(onClick = { if (idx < session.stack.size - 1) { session.stack.add(idx + 1, session.stack.removeAt(idx)); selectedFx = idx + 1; session.touch() } }) { Text("↓") }
                    IconButton(onClick = { session.stack.add(idx + 1, e.copyEffect()); session.touch() }) { Icon(EngineIcons.Add, "Duplicate") }
                    IconButton(onClick = {
                        session.stack.removeAt(idx)
                        selectedFx = null
                        session.logLine("Removed $label from the stack")
                        session.touch()
                    }) { Text("✕") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                session.stack.add(MatchSession.Effect.Levels()); selectedFx = session.stack.size - 1; session.touch()
            }) { Text("+ Levels") }
            OutlinedButton(onClick = {
                session.stack.add(MatchSession.Effect.Curves()); selectedFx = session.stack.size - 1; session.touch()
            }) { Text("+ Curves") }
        }

        // Editor for the tapped effect (defaults to the newest); channel selector applies to both
        val selIdx = selectedFx?.takeIf { it in stackSnapshot.indices } ?: (stackSnapshot.size - 1).takeIf { stackSnapshot.isNotEmpty() }
        val selected = selIdx?.let { stackSnapshot.getOrNull(it) }
        if (selected != null && selected !is MatchSession.Effect.AutoMatch) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CH_NAMES.forEachIndexed { i, n ->
                    FilterChip(selected = fxChannel == i, onClick = { fxChannel = i }, label = { Text(n) })
                }
            }
        }
        when (selected) {
            null -> Text("Add a Levels or Curves effect to edit it.", style = MaterialTheme.typography.bodySmall)
            is MatchSession.Effect.AutoMatch -> Text(
                "Auto Match is parameter-free. To refine the result, add Levels or Curves on top — or delete it and run Auto Match again.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is MatchSession.Effect.Levels -> LevelsEditor(selected.fx, fxChannel, session)
            is MatchSession.Effect.Curves -> CurvesEditor(selected.fx, fxChannel, session)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onScreen(Screen.Preview) }) { Text("Preview") }
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
                        fx.invalidate(); session.touch()
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
            val rev = session.revision // draw-lambda state read: any touch() invalidates the graph
            if (rev >= 0) {
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
private fun PreviewScreen(session: MatchSession, source: MediaSource?, onScreen: (Screen) -> Unit) {
    // Proxy preview: corrected thumbnail-strip flipbook for video; before/after still for images.
    var playing by remember { mutableStateOf(false) }
    var after by remember { mutableStateOf(true) }
    var pos by remember { mutableStateOf(0) }
    val frames = remember { mutableStateListOf<Pair<Bitmap, Bitmap>>() } // before, after
    LaunchedEffect(source, session.revision) {
        frames.clear()
        when (source) {
            is MediaSource.Video -> withContext(Dispatchers.Default) {
                val extractor = source.extractor
                val n = 24
                for (i in 0 until n) {
                    val t = extractor.durationMs * i / (n - 1)
                    val f = extractor.scaledFrameAt(t, 384) ?: continue
                    val corrected = session.renderPreview(f.copy(f.config ?: Bitmap.Config.ARGB_8888, false))
                    withContext(Dispatchers.Main) { frames.add(f to corrected) }
                }
            }
            is MediaSource.Image -> withContext(Dispatchers.Default) {
                val f = downscale(source.bitmap, 512)
                val corrected = session.renderPreview(f.copy(f.config ?: Bitmap.Config.ARGB_8888, false))
                withContext(Dispatchers.Main) { frames.add(f to corrected) }
            }
            null -> Unit
        }
    }
    LaunchedEffect(playing, frames.size) {
        while (playing && frames.size > 1) { delay(200); pos = (pos + 1) % frames.size }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (source is MediaSource.Image) "Image preview" else "Video preview (proxy)", style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
            if (frames.isEmpty()) EngineLinearWavyProgress()
            else Image((if (after) frames[pos].second else frames[pos].first).asImageBitmap(), null, modifier = Modifier.fillMaxHeight())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (frames.size > 1) {
                Button(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
            }
            FilterChip(selected = after, onClick = { after = !after }, enabled = frames.isNotEmpty(),
                label = { Text(if (after) "AFTER" else "BEFORE") })
        }
        if (frames.size > 1) {
            Slider(value = pos.toFloat(), onValueChange = { pos = it.toInt().coerceIn(0, frames.size - 1) },
                valueRange = 0f..(frames.size - 1).toFloat())
        }
        OutlinedButton(onClick = { onScreen(Screen.Editor) }) { Text("Back to editor") }
    }
}
