@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.forgebuild.taskflow.ui

import android.Manifest
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.permissions.EnginePermission
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.components.EngineLoadingIndicator
import com.forgebuild.engine.ui.components.ExpressiveLoading
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.MotionTokens
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.ai.AgentOrchestrator
import com.forgebuild.taskflow.ai.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class VoiceStage { IDLE, RECORDING, SENDING }

/**
 * Pass 5 full redesign of the AI chat / voice surface (Material 3 Expressive).
 *
 * Everything on this screen is reconsidered, not patched:
 *  - Header: gradient-toned title bar with an AI avatar orb on the extraLarge shape tier.
 *  - Messages: asymmetric chat bubbles (user bubbles rounded on the trailing corner,
 *    AI replies anchored to an avatar rail), each message springing in via the official
 *    expressive MotionScheme as it appears.
 *  - Input bar: a floating tonal capsule — borderless text field plus expressive-shape
 *    mic and send buttons whose containers colour-shift between idle/ready states.
 *  - Voice: recording replaces the input bar with a full-width expressive capture pill
 *    (live scrolling WhatsApp-style waveform, real mic amplitude), with uncropped
 *    Cancel / Send controls riding the official expressive button shape-morphing.
 *  - States idle → recording → sending → responded animate through AnimatedContent
 *    with MotionTokens specs so every transition feels spring-physical, never cut.
 */
@Composable
fun ChatPanel(
    initialStartListening: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val orchestrator = remember { AgentOrchestrator.get(context) }
    val messages by orchestrator.messages.collectAsState()
    val busy by orchestrator.busy.collectAsState()
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var voiceStage by remember { mutableStateOf(VoiceStage.IDLE) }
    var micLevel by remember { mutableFloatStateOf(0f) }
    /** Rolling history of live amplitude samples — one entry per bar, newest last. */
    var waveformSamples by remember { mutableStateOf<List<Float>>(emptyList()) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    val listState = rememberLazyListState()

    fun stopRecorder(): File? {
        val f = audioFile
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        micLevel = 0f
        audioFile = null
        return f?.takeIf { it.exists() && it.length() > 0 }
    }

    fun sendRecording() {
        val f = stopRecorder()
        voiceStage = VoiceStage.IDLE
        if (f == null) return
        voiceStage = VoiceStage.SENDING
        scope.launch {
            try {
                val base64 = withContext(Dispatchers.IO) {
                    Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                }
                orchestrator.submitAudio("audio/mp4", base64)
            } catch (_: Exception) {
            } finally {
                voiceStage = VoiceStage.IDLE
                runCatching { f.delete() }
            }
        }
    }

    fun cancelRecording() {
        val f = stopRecorder()
        voiceStage = VoiceStage.IDLE
        runCatching { f?.delete() }
    }

    fun startRecording() {
        waveformSamples = emptyList()
        runCatching {
            val f = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            audioFile = f
            voiceStage = VoiceStage.RECORDING
        }.onFailure {
            recorder = null
            audioFile = null
            voiceStage = VoiceStage.IDLE
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }

    fun requestMic() {
        if (PermissionWiring.missing(context, EnginePermission.MICROPHONE).isEmpty()) {
            startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(initialStartListening) {
        if (initialStartListening) requestMic()
    }

    // Live mic sampling → scrolling waveform bars (WhatsApp voice-note behaviour).
    LaunchedEffect(voiceStage) {
        while (voiceStage == VoiceStage.RECORDING && isActive) {
            val maxAmp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            val level = (maxAmp / 32767f).coerceIn(0f, 1f)
            micLevel = level
            val shaped = kotlin.math.sqrt(level).coerceIn(0.06f, 1f)
            waveformSamples = (waveformSamples + shaped).takeLast(MAX_WAVEFORM_BARS)
            delay(90)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val f = runCatching {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                recorder = null
                audioFile
            }.getOrNull()
            runCatching { f?.delete() }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalBottomSheet(
        onDismissRequest = {
            cancelRecording()
            onClose()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = SpacingTokens.Spacing.md)
        ) {
            // ── Expressive header: avatar orb + title + Done ─────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            EngineIcons.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp).size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(SpacingTokens.Spacing.sm))
                    Column {
                        Text("TaskFlow AI", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (voiceStage) {
                                VoiceStage.RECORDING -> "Listening…"
                                VoiceStage.SENDING -> "Sending voice note…"
                                VoiceStage.IDLE -> if (busy) "Working on your tasks…" else "Ready to help"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (messages.isNotEmpty()) {
                        var showConfirmClear by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showConfirmClear = true }
                        ) {
                            Icon(EngineIcons.Delete, contentDescription = "Clear chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (showConfirmClear) {
                            AlertDialog(
                                onDismissRequest = { showConfirmClear = false },
                                title = { Text("Clear conversation?") },
                                text = { Text("All current messages in this conversation will be cleared.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        orchestrator.clearMessages()
                                        showConfirmClear = false
                                    }) {
                                        Text("Clear", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showConfirmClear = false }) { Text("Cancel") }
                                }
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            cancelRecording()
                            onClose()
                        },
                        shapes = ButtonDefaults.shapes()
                    ) { Text("Done") }
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            // ── Message list: bubbles spring in as they appear ───────────────
            val messageKeys = remember(messages) {
                TaskKeyUtils.buildUniqueMessageKeys(messages.map { it.id })
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
            ) {
                if (messages.isEmpty()) {
                    item(key = "empty_chat_guidance") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.largeIncreased,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = SpacingTokens.Spacing.sm)
                        ) {
                            Column(Modifier.padding(SpacingTokens.Spacing.md)) {
                                Text(
                                    "Ask me to plan, schedule or reorganise:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(SpacingTokens.Spacing.xs))
                                Text("• \"Create three tasks to do this week and scatter them\"", style = MaterialTheme.typography.bodySmall)
                                Text("• \"Set weekly groceries only on Tuesdays\"", style = MaterialTheme.typography.bodySmall)
                                Text("• \"Repeat every day until next Friday: water the plants\"", style = MaterialTheme.typography.bodySmall)
                                Text("• \"How much time do I have left today?\"", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                itemsIndexed(messages, key = { index, m -> messageKeys.getOrElse(index) { "msg_${m.id}_$index" } }) { _, m ->
                    // Spring-based entrance per message (official expressive motion spec).
                    @Suppress("UNCHECKED_CAST")
                    val spatial = MotionTokens.defaultSpatial as
                        androidx.compose.animation.core.FiniteAnimationSpec<Float>
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { visible = true }
                    val progress by animateFloatAsState(
                        targetValue = if (visible) 1f else 0f,
                        animationSpec = spatial,
                        label = "bubbleIn"
                    )
                    Box(
                        Modifier.graphicsLayer {
                            alpha = progress
                            translationY = (1f - progress) * 48f
                            scaleX = 0.92f + 0.08f * progress
                            scaleY = 0.92f + 0.08f * progress
                        }
                    ) { ChatBubble(m) }
                }

                if (busy) {
                    item(key = "busy_thinking_indicator") {
                        Row(
                            Modifier.padding(SpacingTokens.Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                        ) {
                            EngineLoadingIndicator()
                            Text(
                                "Thinking & updating tasks…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            // ── Composer: AnimatedContent swaps idle ↔ recording ↔ sending ───
            @Suppress("UNCHECKED_CAST")
            val swapSpatial = MotionTokens.fastSpatial as
                androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset>
            AnimatedContent(
                targetState = voiceStage,
                transitionSpec = {
                    (fadeIn(tween(160)) + slideInVertically(swapSpatial) { it / 6 })
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "composerSwap"
            ) { stage ->
                when (stage) {
                    VoiceStage.RECORDING -> RecordingPill(
                        samples = waveformSamples,
                        onSend = { sendRecording() },
                        onCancel = { cancelRecording() }
                    )
                    VoiceStage.SENDING -> SendingVoiceCard()
                    VoiceStage.IDLE -> ComposerBar(
                        input = input,
                        onInputChange = { input = it },
                        busy = busy,
                        onMic = { requestMic() },
                        onSend = {
                            val text = input.trim()
                            if (text.isNotEmpty() && !busy) {
                                input = ""
                                orchestrator.submit(text)
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.lg))
        }
    }
}

/** Capacity of the rolling waveform history (one amplitude sample per bar). */
private const val MAX_WAVEFORM_BARS = 48

/**
 * Floating composer capsule: borderless tonal text field + expressive mic/send
 * buttons. The send button animates to filled-primary (and morphs its shape on
 * press) the moment there is text; the mic sits in a tonal container otherwise.
 */
@Composable
private fun ComposerBar(
    input: String,
    onInputChange: (String) -> Unit,
    busy: Boolean,
    onMic: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = SpacingTokens.Spacing.md, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Message TaskFlow AI…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            val hasText = input.isNotBlank()
            // Mic: shown when idle-no-text; send takes over with an expressive colour shift.
            if (!hasText) {
                IconButton(
                    onClick = onMic,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(EngineIcons.Mic, contentDescription = "Record voice note")
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = !busy,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(EngineIcons.Send, contentDescription = "Send message")
                }
            }
        }
    }
}

/**
 * Recording pill — replaces the whole composer while capturing. The waveform
 * strip gets real vertical room and the Cancel/Send controls sit fully inside
 * the pill with natural sizing, so the Send button is never cropped (Pass 5 fix).
 */
@Composable
private fun RecordingPill(
    samples: List<Float>,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recPulseAlpha"
    )

    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.32f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .padding(horizontal = SpacingTokens.Spacing.md, vertical = SpacingTokens.Spacing.sm)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha))
                )
                Text(
                    "Recording…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
                    Text("Cancel")
                }
                // Uncropped: natural button sizing (no height cap), expressive press morph.
                androidx.compose.material3.Button(
                    onClick = onSend,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(EngineIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Send")
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            // Scrolling waveform: newest bar at the right edge, history sliding left.
            val barColor = MaterialTheme.colorScheme.primary
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                val barWidth = 4.dp.toPx()
                val gap = 3.dp.toPx()
                val step = barWidth + gap
                val maxBarH = size.height
                val minBarH = 3.dp.toPx()
                val centerY = size.height / 2f
                val capacity = (size.width / step).toInt().coerceAtLeast(1)
                val visible = samples.takeLast(capacity)
                // Pass 6 fix: place bars from BOTH edges of the canvas (not the sample
                // count) so the strip starts flush right AND travels the full width
                // to a flush left edge — symmetrical. `capacity` slots span the width;
                // the oldest bar lands at x=0 exactly when the strip is full.
                visible.forEachIndexed { index, amp ->
                    val fromRight = visible.size - 1 - index
                    val x = size.width - barWidth - step * fromRight
                    val h = (minBarH + (maxBarH - minBarH) * amp).coerceIn(minBarH, maxBarH)
                    val ageFade = 0.45f + 0.55f * (index + 1).toFloat() / visible.size
                    drawRoundRect(
                        color = barColor.copy(alpha = ageFade),
                        topLeft = androidx.compose.ui.geometry.Offset(x, centerY - h / 2f),
                        size = androidx.compose.ui.geometry.Size(barWidth, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}

/** Distinct sending state: official expressive loading indicator + status copy. */
@Composable
private fun SendingVoiceCard() {
    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .padding(horizontal = SpacingTokens.Spacing.md, vertical = SpacingTokens.Spacing.md)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)
        ) {
            EngineLoadingIndicator()
            Column(Modifier.weight(1f)) {
                Text(
                    "Sending voice note to Gemini…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Gemini is listening to your audio — no on-device transcription.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/**
 * Expressive asymmetric chat bubble: user messages hug the trailing edge with a
 * softened trailing corner; AI replies sit on an avatar rail; action cards use
 * the tertiary palette with a check badge.
 */
@Composable
private fun ChatBubble(m: ChatMessage) {
    val alignEnd = m.role == ChatMessage.Role.USER
    val shape = when (m.role) {
        ChatMessage.Role.USER -> RoundedCornerShape(
            topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 6.dp
        )
        ChatMessage.Role.MODEL -> RoundedCornerShape(
            topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 24.dp
        )
        ChatMessage.Role.ACTION -> MaterialTheme.shapes.largeIncreased
    }
    val color = when (m.role) {
        ChatMessage.Role.USER -> MaterialTheme.colorScheme.primaryContainer
        ChatMessage.Role.MODEL -> MaterialTheme.colorScheme.surfaceContainerHigh
        ChatMessage.Role.ACTION -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!alignEnd && m.role == ChatMessage.Role.MODEL) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        EngineIcons.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            shape = shape,
            color = color,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(Modifier.padding(SpacingTokens.Spacing.sm)) {
                if (m.role == ChatMessage.Role.ACTION) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            EngineIcons.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Executed action",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(m.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
