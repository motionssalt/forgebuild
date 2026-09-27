@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.forgebuild.taskflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.MotionTokens
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.data.DayAccounting
import com.forgebuild.taskflow.data.Task
import com.forgebuild.taskflow.data.TaskType
import com.forgebuild.taskflow.data.TimerEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small rounded duration chip shared by rows and detail sheets. */
@Composable
fun DurationPill(minutes: Long) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = "${minutes}m",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(
                horizontal = SpacingTokens.Spacing.xs,
                vertical = SpacingTokens.Spacing.xxs / 2
            )
        )
    }
}

/** mm:ss (or h:mm:ss) countdown text. */
private fun fmtCountdown(ms: Long): String {
    val totalSec = (ms + 999L) / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Expressive task row: full-bleed container on the largeIncreased shape tier,
 * type-dot + per-type container tint, loud pulsing DUE NOW state that persists for
 * the task's whole span, a subtle "time passed" state once the span elapses
 * uncompleted, and an inline countdown timer control (play / pause / extend / done).
 */
@Composable
fun TaskRowItem(
    task: Task,
    modifier: Modifier = Modifier,
    nowTick: Long = System.currentTimeMillis(),
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPeekInfo: () -> Unit,
    onTimerToggle: () -> Unit = {},
    onTimerExtend: () -> Unit = {},
    onTimerComplete: () -> Unit = {},
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val rowScope = rememberCoroutineScope()
    var isCompleting by remember(task.id, task.completed) { mutableStateOf(false) }

    fun handleToggle() {
        if (task.completed) {
            onToggle()
        } else if (!isCompleting) {
            isCompleting = true
            rowScope.launch {
                delay(420)
                onToggle()
            }
        }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault()) }
    var rowMenuOpen by remember { mutableStateOf(false) }

    val timerState = TimerEngine.stateOf(task, nowTick)
    val timerRemaining = TimerEngine.remainingMs(task, nowTick)

    // Pass 6: subtle "time's passed but not done" window — the fixed-time span
    // (start .. start+duration) fully elapsed, task still active & uncompleted.
    // Distinct from BOTH the loud due-now alert and the normal state.
    val overdueWindow = !task.dueNow && !task.completed && task.fixedTime != null &&
        (task.fixedTime + task.durationMinutes.coerceAtLeast(1L) * 60_000L) <= nowTick

    val overnightLabel: String? = remember(task.fixedTime, task.durationMinutes) {
        val ft = task.fixedTime ?: return@remember null
        if (!DayAccounting.crossesMidnight(task)) return@remember null
        val end = ft + task.durationMinutes.coerceAtLeast(1L) * 60_000L
        val days = ((DayAccounting.dayStart(end - 1L) - DayAccounting.dayStart(ft)) / 86_400_000L).toInt()
        "${timeFmt.format(Date(ft))} → ${timeFmt.format(Date(end))}" + if (days > 0) " +${days}d" else ""
    }

    val infiniteTransition = rememberInfiniteTransition(label = "dueNowPulse")
    val dueNowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dueNowAlpha"
    )

    // 4-type colour language; Pass 6 dark-mode fix: recurring containers were too
    // bright/harsh in dark theme — tone their alpha down there, mirroring how the
    // other type tints already sit quietly against dark surfaces.
    val dark = isSystemInDarkTheme()
    val typeColor = when (task.taskType) {
        TaskType.RECURRING_FIXED -> MaterialTheme.colorScheme.tertiary
        TaskType.RECURRING_NO_TIME -> MaterialTheme.colorScheme.primary
        TaskType.FIXED_TIME -> MaterialTheme.colorScheme.secondary
        TaskType.NORMAL -> MaterialTheme.colorScheme.outline
    }
    val targetContainer = when {
        isCompleting -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (dark) 0.85f else 0.95f)
        task.dueNow -> MaterialTheme.colorScheme.errorContainer
        timerState == TimerEngine.TimerState.FINISHED ->
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = if (dark) 0.34f else 0.55f)
        overdueWindow -> MaterialTheme.colorScheme.surfaceContainerHigh
        task.taskType == TaskType.RECURRING_FIXED ->
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = if (dark) 0.20f else 0.40f)
        task.taskType == TaskType.RECURRING_NO_TIME ->
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (dark) 0.16f else 0.32f)
        task.taskType == TaskType.FIXED_TIME ->
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (dark) 0.24f else 0.40f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    val completingAlpha by animateFloatAsState(
        targetValue = if (isCompleting) 0.45f else 1f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "completingAlpha"
    )
    val completingScale by animateFloatAsState(
        targetValue = if (isCompleting) 0.98f else 1f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "completingScale"
    )
    @Suppress("UNCHECKED_CAST")
    val effectsSpec = MotionTokens.defaultEffects as
        androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.graphics.Color>
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = effectsSpec,
        label = "rowContainer"
    )

    val dateOnlyFmt = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val isFutureDate = task.scheduledDate != null &&
        DayAccounting.dayStart(task.scheduledDate) != DayAccounting.dayStart(nowTick)

    val badgeText = when (task.taskType) {
        TaskType.RECURRING_FIXED -> {
            val base = "Recurring · ${task.recurrence.name.lowercase().replaceFirstChar { it.uppercase() }}"
            val time = task.fixedTime?.let { timeFmt.format(Date(it)) }
            base + (time?.let { " · $it" } ?: "") + (overnightLabel?.let { " · $it" } ?: "")
        }
        TaskType.RECURRING_NO_TIME ->
            "Recurring · ${task.recurrence.name.lowercase().replaceFirstChar { it.uppercase() }}"
        TaskType.FIXED_TIME ->
            task.fixedTime?.let { dateFmt.format(Date(it)) + (overnightLabel?.let { o -> " → $o" } ?: "") } ?: "Scheduled"
        TaskType.NORMAL -> if (isFutureDate) dateOnlyFmt.format(Date(task.scheduledDate!!)) else ""
    }
    val badgeIcon = when (task.taskType) {
        TaskType.RECURRING_FIXED, TaskType.RECURRING_NO_TIME -> EngineIcons.Repeat
        TaskType.FIXED_TIME -> EngineIcons.Alarm
        TaskType.NORMAL -> if (isFutureDate) EngineIcons.CalendarToday else null
    }

    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = containerColor,
        modifier = modifier
            .graphicsLayer {
                alpha = completingAlpha
                scaleX = completingScale
                scaleY = completingScale
            }
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SpacingTokens.Spacing.xs, horizontal = SpacingTokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle (long-press to reorder by fractional rank).
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragBy(dragAmount.y)
                            }
                        )
                    }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    EngineIcons.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (task.dueNow) MaterialTheme.colorScheme.error else typeColor)
            )

            Checkbox(
                checked = task.completed || isCompleting,
                onCheckedChange = { handleToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (task.dueNow) FontWeight.Bold else FontWeight.Normal,
                            textDecoration = if (task.completed || isCompleting) TextDecoration.LineThrough else null
                        ),
                        color = when {
                            isCompleting -> MaterialTheme.colorScheme.onPrimaryContainer
                            task.dueNow -> MaterialTheme.colorScheme.onErrorContainer
                            overdueWindow -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .alpha(if (isCompleting) 0.7f else 1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    DurationPill(minutes = task.durationMinutes)
                    if (isCompleting) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    EngineIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "Completed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        task.dueNow -> {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.error.copy(alpha = dueNowAlpha)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        EngineIcons.PriorityHigh, null,
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        "DUE NOW",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        }
                        overdueWindow -> {
                            // Subtle elapsed-time state: quiet outline-toned chip, no pulse.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    EngineIcons.Timer, null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "Time passed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        badgeText.isNotBlank() -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (badgeIcon != null) {
                                    Icon(badgeIcon, null, tint = typeColor, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(3.dp))
                                }
                                Text(
                                    badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // ── Countdown timer control (Pass 6) ────────────────────────────
            when (timerState) {
                TimerEngine.TimerState.IDLE -> {
                    IconButton(onClick = onTimerToggle, modifier = Modifier.size(36.dp)) {
                        Icon(
                            EngineIcons.PlayArrow,
                            contentDescription = "Start timer",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                TimerEngine.TimerState.RUNNING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            fmtCountdown(timerRemaining),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onTimerToggle, modifier = Modifier.size(36.dp)) {
                            Icon(
                                EngineIcons.Pause,
                                contentDescription = "Pause timer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                TimerEngine.TimerState.PAUSED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            fmtCountdown(timerRemaining),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = onTimerToggle, modifier = Modifier.size(36.dp)) {
                            Icon(
                                EngineIcons.PlayArrow,
                                contentDescription = "Resume timer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                TimerEngine.TimerState.FINISHED -> {
                    // Pass 7 FIX: the extend affordance was a bare cropped text button — now a
                    // clean filled-tonal button with the proper MoreTime icon beside Done.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = onTimerExtend,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                EngineIcons.MoreTime, null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Extend", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = onTimerComplete) {
                            Text("Done", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Single overflow menu carries the secondary actions.
            Box {
                IconButton(onClick = { rowMenuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        EngineIcons.MoreVert,
                        contentDescription = "Task actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = rowMenuOpen, onDismissRequest = { rowMenuOpen = false }) {
                    if (timerState != TimerEngine.TimerState.IDLE) {
                        DropdownMenuItem(
                            text = { Text("Extend timer") },
                            leadingIcon = { Icon(EngineIcons.MoreTime, null) },
                            onClick = { rowMenuOpen = false; onTimerExtend() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Subtasks") },
                        leadingIcon = { Icon(EngineIcons.SubdirectoryArrowRight, null) },
                        onClick = { rowMenuOpen = false; onOpen() }
                    )
                    if (task.info.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("View notes") },
                            leadingIcon = { Icon(EngineIcons.Visibility, null) },
                            onClick = { rowMenuOpen = false; onPeekInfo() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(EngineIcons.Settings, null) },
                        onClick = { rowMenuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(EngineIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { rowMenuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}
