@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.forgebuild.taskflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.forgebuild.taskflow.data.TimerEngine
import com.forgebuild.engine.ui.components.EngineCircularWavyProgress
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.data.Task
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * TaskFlow home — rebuilt from scratch for the Material 3 Expressive reimagining.
 *
 * New structure (no carry-over from the previous design):
 *  - MediumTopAppBar with a single overflow menu (Unfinished / Completed / Settings)
 *    instead of a row of icon buttons.
 *  - A hero "Today" panel built around the OFFICIAL expressive wavy progress
 *    indicators (circular + linear), replacing the old static progress bar card.
 *  - Full-bleed expressive task rows (see [TaskRowItem]) with a per-row overflow menu.
 *  - A stacked FAB column: voice note, AI chat, and the primary "New task" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    vm: TaskViewModel,
    onEditTask: (Long) -> Unit,
    onOpenUnfinished: () -> Unit,
    onOpenCompleted: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateTask: () -> Unit,
    onOpenChat: (startListening: Boolean) -> Unit
) {
    val tasks by vm.tasks.collectAsState()
    val breadcrumbs by vm.breadcrumbs.collectAsState()
    val timeRange by vm.timeRange.collectAsState()
    val allocatedMinutes by vm.allocatedMinutesToday.collectAsState()
    val remainingMinutes by vm.remainingMinutesToday.collectAsState()
    val usedDayMinutes by vm.usedDayMinutes.collectAsState()

    var peekInfo by remember { mutableStateOf<Task?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var extendTarget by remember { mutableStateOf<Task?>(null) }
    var dingedIds by remember { mutableStateOf(setOf<Long>()) }

    // 1s ticker: drives live countdown text, the end-of-due-now transition, and
    // the kitchen-timer ding the moment a foreground countdown finishes.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(1000); nowTick = System.currentTimeMillis() }
    }
    LaunchedEffect(tasks, nowTick) {
        tasks.firstOrNull {
            TimerEngine.stateOf(it, nowTick) == TimerEngine.TimerState.FINISHED && it.id !in dingedIds
        }?.let { t ->
            dingedIds = dingedIds + t.id
            com.forgebuild.taskflow.notify.TimerSounds.playCompletion()
        }
        // Clear ding-memory once a task leaves the finished state (extended/completed).
        dingedIds = dingedIds.filter { id ->
            tasks.firstOrNull { it.id == id }?.let {
                TimerEngine.stateOf(it, nowTick) == TimerEngine.TimerState.FINISHED
            } == true
        }.toSet()
    }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.userMessage.collectLatest { msg -> snackbarHostState.showSnackbar(msg) }
    }

    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text("TaskFlow", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = breadcrumbs.lastOrNull()?.second ?: "Your day, organised",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (breadcrumbs.isNotEmpty()) {
                        IconButton(onClick = { vm.navigateUp() }) {
                            Icon(EngineIcons.ArrowBack, contentDescription = "Up")
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(EngineIcons.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Unfinished") },
                                leadingIcon = { Icon(EngineIcons.Alarm, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; onOpenUnfinished() }
                            )
                            DropdownMenuItem(
                                text = { Text("Completed") },
                                leadingIcon = { Icon(EngineIcons.CheckCircle, null) },
                                onClick = { menuOpen = false; onOpenCompleted() }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(EngineIcons.Settings, null) },
                                onClick = { menuOpen = false; onOpenSettings() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)
            ) {
                SmallFloatingActionButton(
                    onClick = { onOpenChat(true) },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) { Icon(EngineIcons.Mic, contentDescription = "Voice note to AI") }

                SmallFloatingActionButton(
                    onClick = { onOpenChat(false) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) { Icon(EngineIcons.SmartToy, contentDescription = "TaskFlow AI chat") }

                ExtendedFloatingActionButton(
                    onClick = onCreateTask,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(EngineIcons.Add, contentDescription = null) },
                    text = { Text("New task") }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = SpacingTokens.Spacing.md)
        ) {
            // Pass 6 fix: real breathing room between the app-bar header and the
            // content below it — the page no longer reads as cramped under the title.
            Spacer(Modifier.height(SpacingTokens.Spacing.md))

            // Breadcrumb trail (only inside sub-task levels).
            AnimatedVisibility(
                visible = breadcrumbs.isNotEmpty(),
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.navigateToBreadcrumb(-1) }) {
                        Text("Tasks", style = MaterialTheme.typography.labelLarge)
                    }
                    breadcrumbs.forEachIndexed { i, (_, title) ->
                        Icon(
                            EngineIcons.KeyboardArrowRight, null,
                            Modifier
                                .alpha(0.6f)
                                .size(16.dp)
                        )
                        TextButton(onClick = { vm.navigateToBreadcrumb(i) }) {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // HERO: today's time budget on the official expressive wavy indicators.
            TodayHero(
                allocatedMinutes = allocatedMinutes,
                remainingMinutes = remainingMinutes,
                usedDayMinutes = usedDayMinutes
            )

            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TimeRangeView.entries.forEachIndexed { index, range ->
                    SegmentedButton(
                        selected = timeRange == range,
                        onClick = { vm.setTimeRange(range) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TimeRangeView.entries.size)
                    ) {
                        Text(range.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = MaterialTheme.shapes.extraExtraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Icon(
                                EngineIcons.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(SpacingTokens.Spacing.lg)
                                    .size(SpacingTokens.Spacing.xxxl)
                            )
                        }
                        Spacer(Modifier.height(SpacingTokens.Spacing.md))
                        Text(
                            "Nothing here yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
                        Text(
                            "Tap New task below, or ask the AI to plan it for you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val uniqueTasks = remember(tasks) { tasks.distinctBy { it.id } }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                ) {
                    itemsIndexed(uniqueTasks, key = { _, task -> task.id }) { index, task ->
                        val isDragging = dragIndex == index
                        TaskRowItem(
                            task = task,
                            nowTick = nowTick,
                            onTimerToggle = { vm.timerToggle(task) },
                            onTimerExtend = { extendTarget = task },
                            onTimerComplete = { vm.timerComplete(task) },
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                    val s = if (isDragging) 1.03f else 1f
                                    scaleX = s
                                    scaleY = s
                                },
                            onToggle = { vm.toggleComplete(task) },
                            onOpen = { vm.openTask(task.id) },
                            onEdit = { onEditTask(task.id) },
                            onDelete = { vm.delete(task) },
                            onPeekInfo = { peekInfo = task },
                            onDragStart = { dragIndex = index; dragOffset = 0f },
                            onDragBy = { dragOffset += it },
                            onDragEnd = {
                                val itemH = 76 * 3
                                val delta = (dragOffset / itemH).roundToInt()
                                if (delta != 0) vm.moveRelative(task, delta)
                                dragIndex = null
                                dragOffset = 0f
                            }
                        )
                    }
                    item(key = "list_bottom_spacer") { Spacer(Modifier.height(120.dp)) }
                }
            }
        }
    }

    // Time's-up / extend dialog: add minutes or hours, or mark the task complete.
    extendTarget?.let { t ->
        var hoursTxt by remember(t.id) { mutableStateOf("") }
        var minsTxt by remember(t.id) { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { extendTarget = null },
            title = {
                Column {
                    Text(
                        if (TimerEngine.stateOf(t, nowTick) == TimerEngine.TimerState.FINISHED) "Time's up"
                        else "Extend timer",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        t.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "Add more time — same units used across the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)) {
                        androidx.compose.material3.OutlinedTextField(
                            value = hoursTxt, onValueChange = { hoursTxt = it.filter(Char::isDigit).take(2) },
                            label = { Text("Hours") }, singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = minsTxt, onValueChange = { minsTxt = it.filter(Char::isDigit).take(3) },
                            label = { Text("Minutes") }, singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                        listOf(5, 10, 15, 30).forEach { m ->
                            androidx.compose.material3.FilterChip(
                                selected = false,
                                onClick = { vm.timerExtend(t, m.toLong()); extendTarget = null },
                                label = { Text("+${m}m") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val total = (hoursTxt.toLongOrNull() ?: 0L) * 60L + (minsTxt.toLongOrNull() ?: 0L)
                    if (total > 0L) vm.timerExtend(t, total)
                    extendTarget = null
                }) { Text("Add time") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.timerComplete(t)
                    extendTarget = null
                }) { Text("Mark complete", color = MaterialTheme.colorScheme.primary) }
            }
        )
    }

    peekInfo?.let { t ->
        ModalBottomSheet(onDismissRequest = { peekInfo = null }) {
            Column(Modifier.padding(SpacingTokens.Spacing.lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        t.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    DurationPill(minutes = t.durationMinutes)
                }
                Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                if (t.info.isNotBlank()) {
                    Text(t.info, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(
                        "No additional info provided for this task.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.height(SpacingTokens.Spacing.md))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        val id = t.id
                        peekInfo = null
                        onEditTask(id)
                    }) { Text("Edit full task") }
                }
            }
        }
    }
}

/**
 * Hero "time remaining today" panel. Uses the OFFICIAL Material 3 Expressive
 * wavy progress indicators (circular + linear) — no static progress bar.
 */
@Composable
private fun TodayHero(allocatedMinutes: Long, remainingMinutes: Long, usedDayMinutes: Long) {
    val total = (allocatedMinutes + remainingMinutes).coerceAtLeast(1L)
    val booked = (allocatedMinutes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    // Pass 7: horizontal bar is now a DISTINCT metric — the whole 24h day vs. how much
    // of it is used (elapsed time + time still booked by tasks). The circular indicator
    // keeps its original meaning (remaining-today share already booked).
    val dayUsed = (usedDayMinutes.toFloat() / 1440f).coerceIn(0f, 1f)

    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(SpacingTokens.Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    EngineCircularWavyProgress(
                        progress = { booked },
                        modifier = Modifier.size(96.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(booked * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "booked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(SpacingTokens.Spacing.lg))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${remainingMinutes / 60}h ${remainingMinutes % 60}m left",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${allocatedMinutes / 60}h ${allocatedMinutes % 60}m scheduled across your tasks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.md))

            EngineLinearWavyProgress(
                progress = { dayUsed },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
            Text(
                "Day used: ${usedDayMinutes / 60}h ${usedDayMinutes % 60}m of 24h (elapsed + booked)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
