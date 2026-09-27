@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.forgebuild.taskflow.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import com.forgebuild.taskflow.util.CrashLogger
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.MotionTokens
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.settings.GeminiKeyStore
import com.forgebuild.taskflow.settings.NotifType
import com.forgebuild.taskflow.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Pass 6 full redesign of Settings — rethought from scratch, not a patch.
 *
 * Structure: one continuous scroll of EXPRESSIVE SECTION CARDS, each a large-tier
 * tonal container with an icon header rail. Settings are grouped by intent rather
 * than by data type:
 *   1. "Intelligence" — Gemini API key ring (failover-ordered, encrypted on-device)
 *   2. "Your rhythm" — theme + retention cadence
 *   3. "Alerts" — the notification preference matrix with animated sub-controls
 *   4. "Reliability" — exact alarms, battery exemption, background service status
 * Every interactive element rides the official expressive MotionScheme: section
 * cards spring open their detail regions, switches/inputs animate state with the
 * default effects spec, and destructive/primary actions use expressive shapes.
 */
@Composable
fun SettingsScreen(
    vm: TaskViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = vm.settings
    val keyStore = remember { GeminiKeyStore.get(context) }
    val keys by keyStore.keys.collectAsState()
    val theme by vm.themeMode.collectAsState()
    val retentionDays by vm.retentionDays.collectAsState()
    val pomodoroEnabled by vm.pomodoroEnabled.collectAsState()
    val pomodoroWork by vm.pomodoroWorkMinutes.collectAsState()
    val pomodoroBreak by vm.pomodoroBreakMinutes.collectAsState()

    var newKey by remember { mutableStateOf("") }
    var isBatteryExempt by remember { mutableStateOf(PermissionWiring.isBatteryOptimizationExempt(context)) }
    // Pass 7 FIX: live exact-alarm grant status (the flow previously never surfaced it).
    var alarmGranted by remember { mutableStateOf(PermissionWiring.canScheduleExactAlarms(context)) }

    // Re-read both special-permission statuses whenever Settings resumes, so returning
    // from the OS screens flips the status pills instead of leaving a stale state.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                alarmGranted = PermissionWiring.canScheduleExactAlarms(context)
                isBatteryExempt = PermissionWiring.isBatteryOptimizationExempt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    @Suppress("UNCHECKED_CAST")
    val effectsSpec = MotionTokens.defaultEffects as
        androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.graphics.Color>

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Tune TaskFlow to how you work",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(EngineIcons.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md)
        ) {
            Spacer(Modifier.height(SpacingTokens.Spacing.xxs))

            // ── 1. INTELLIGENCE — Gemini key ring ────────────────────────────
            SettingsSection(
                icon = EngineIcons.SmartToy,
                title = "Intelligence",
                subtitle = "Gemini keys are encrypted on-device and sent only to Google. Order = failover priority."
            ) {
                if (keys.isEmpty()) {
                    Text(
                        "No keys yet — add one below to wake the AI agent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                keys.forEachIndexed { i, k ->
                    // Each key chip springs and colour-shifts with selection order changes.
                    val chipColor by animateColorAsState(
                        targetValue = if (i == 0) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = effectsSpec,
                        label = "keyChip$i"
                    )
                    Surface(
                        shape = MaterialTheme.shapes.largeIncreased,
                        color = chipColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = SpacingTokens.Spacing.sm, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (i == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    "${i + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (i == 0) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(Modifier.width(SpacingTokens.Spacing.sm))
                            Text(
                                "••••${k.takeLast(4)}" + if (i == 0) "  · primary" else "",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { keyStore.moveUp(k) }, enabled = i > 0) {
                                Icon(EngineIcons.PriorityHigh, "Move up",
                                    tint = if (i > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            }
                            IconButton(onClick = { keyStore.remove(k) }) {
                                Icon(EngineIcons.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(Modifier.height(SpacingTokens.Spacing.xs))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("New API key") },
                        modifier = Modifier.weight(1f),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = MaterialTheme.shapes.largeIncreased,
                        singleLine = true
                    )
                    // Add button springs in scale as soon as input is non-blank.
                    val ready = newKey.isNotBlank()
                    val addScale by animateFloatAsState(
                        targetValue = if (ready) 1f else 0.85f,
                        animationSpec = MotionTokens.defaultSpatial as
                            androidx.compose.animation.core.FiniteAnimationSpec<Float>,
                        label = "addScale"
                    )
                    IconButton(
                        onClick = {
                            if (ready) { keyStore.add(newKey.trim()); newKey = "" }
                        },
                        enabled = ready,
                        modifier = Modifier.scale(addScale),
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.outline
                        )
                    ) { Icon(EngineIcons.Add, "Add key") }
                }
            }

            // ── 2. YOUR RHYTHM — theme + retention ───────────────────────────
            SettingsSection(
                icon = EngineIcons.CalendarToday,
                title = "Your rhythm",
                subtitle = "How the app looks and how long history is kept."
            ) {
                Text("Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = theme == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = ThemeMode.entries.size)
                        ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }

                Spacer(Modifier.height(SpacingTokens.Spacing.md))

                Text(
                    "Keep completed & unfinished history for",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    listOf(1, 3, 7, 14, 30, 60).forEach { days ->
                        FilterChip(
                            selected = retentionDays == days,
                            onClick = { vm.setRetentionDays(days) },
                            label = { Text(if (days == 1) "1 day" else "$days days") }
                        )
                    }
                }
            }

            // ── 2b. POMODORO — work/break interval settings (Pass 7) ──────────
            SettingsSection(
                icon = EngineIcons.Timer,
                title = "Pomodoro",
                subtitle = "Split running timers into work intervals with short breaks."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Pomodoro timers", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "When you press play, the countdown alternates work and break intervals. Breaks are free — the countdown pauses and resumes by itself.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = pomodoroEnabled, onCheckedChange = { vm.setPomodoroEnabled(it) })
                }

                AnimatedVisibility(
                    visible = pomodoroEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                        Text("Work interval", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                            listOf(15, 20, 25, 30, 45, 60).forEach { m ->
                                FilterChip(
                                    selected = pomodoroWork == m,
                                    onClick = { vm.setPomodoroWorkMinutes(m) },
                                    label = { Text("${m}m") }
                                )
                            }
                        }
                        Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                        Text("Break interval", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                            listOf(5, 10, 15, 20).forEach { m ->
                                FilterChip(
                                    selected = pomodoroBreak == m,
                                    onClick = { vm.setPomodoroBreakMinutes(m) },
                                    label = { Text("${m}m") }
                                )
                            }
                        }
                        Spacer(Modifier.height(SpacingTokens.Spacing.xs))
                        Text(
                            "Applies when a task's duration fits at least one full work interval; shorter tasks run a normal countdown.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // ── 3. ALERTS — notification matrix ──────────────────────────────
            SettingsSection(
                icon = EngineIcons.Alarm,
                title = "Alerts",
                subtitle = "Exactly which moments TaskFlow may interrupt you for."
            ) {
                NotifType.entries.forEach { type ->
                    val label = when (type) {
                        NotifType.DUE -> "Fixed-time task due"
                        NotifType.OVERDUE -> "Overdue task alert"
                        NotifType.DAILY_AGENDA -> "Daily morning agenda"
                        NotifType.API_KEYS_FAILED -> "All Gemini keys failed"
                        NotifType.AGENT_CONFIRMATION -> "AI action confirmations"
                        NotifType.APPROACHING_DEADLINE -> "Approaching-deadline warning"
                        NotifType.RECURRING_INSTANCE -> "Recurring instance scheduled"
                    }
                    val enabled = runBlocking { settings.notifEnabled(type).first() }
                    var checked by remember(type) { mutableStateOf(enabled) }

                    // Row tint animates on toggle via the expressive effects spec.
                    val rowColor by animateColorAsState(
                        targetValue = if (checked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        animationSpec = effectsSpec,
                        label = "notifRow$type"
                    )
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = rowColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = SpacingTokens.Spacing.sm, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = checked,
                                onCheckedChange = {
                                    checked = it
                                    scope.launch { settings.setNotifEnabled(type, it) }
                                }
                            )
                        }
                    }

                    // Lead-time sub-panel springs open only while its toggle is on.
                    if (type == NotifType.APPROACHING_DEADLINE) {
                        AnimatedVisibility(
                            visible = checked,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            var lead by remember {
                                mutableFloatStateOf(runBlocking { settings.deadlineLeadMinutes.first() }.toFloat())
                            }
                            Column(Modifier.padding(horizontal = SpacingTokens.Spacing.sm, vertical = SpacingTokens.Spacing.xs)) {
                                Text(
                                    "Warn ${lead.toInt()} min before a due time",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = lead,
                                    onValueChange = { lead = it },
                                    onValueChangeFinished = { scope.launch { settings.setDeadlineLeadMinutes(lead.toInt()) } },
                                    valueRange = 5f..60f
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(SpacingTokens.Spacing.xs))
                }
            }

            // ── 4. RELIABILITY — alarms, battery, background ─────────────────
            SettingsSection(
                icon = EngineIcons.Bolt,
                title = "Reliability",
                subtitle = "Exact alarms + a battery exemption keep reminders alive through Doze."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Background service", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Persistent low-priority notification",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Always on",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(SpacingTokens.Spacing.sm))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            (context as? Activity)?.let { PermissionWiring.requestExactAlarm(it) }
                            alarmGranted = PermissionWiring.canScheduleExactAlarms(context)
                        },
                        shapes = ButtonDefaults.shapes()
                    ) { Text("Exact alarms") }
                    // Live grant status pill: flips to Granted as soon as the OS flow completes.
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (alarmGranted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            if (alarmGranted) "Granted" else "Not granted — tap to open the system setting",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (alarmGranted) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(SpacingTokens.Spacing.sm))

                Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)) {

                    val exemptColor by animateColorAsState(
                        targetValue = if (isBatteryExempt) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.primary,
                        animationSpec = effectsSpec,
                        label = "batteryBtn"
                    )
                    Button(
                        onClick = {
                            PermissionWiring.requestBatteryExemption(context)
                            isBatteryExempt = PermissionWiring.isBatteryOptimizationExempt(context)
                        },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = exemptColor,
                            contentColor = if (isBatteryExempt) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isBatteryExempt) {
                            Icon(EngineIcons.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(if (isBatteryExempt) "Battery exempt" else "Exempt battery")
                    }
                }
                Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                var showCrashLogDialog by remember { mutableStateOf(false) }
                var crashLogText by remember { mutableStateOf("") }
                OutlinedButton(
                    onClick = {
                        crashLogText = CrashLogger.getCrashLogs(context)
                        showCrashLogDialog = true
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(EngineIcons.Info, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Crash Diagnostics")
                }
                if (showCrashLogDialog) {
                    AlertDialog(
                        onDismissRequest = { showCrashLogDialog = false },
                        title = { Text("Crash Diagnostics") },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    crashLogText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showCrashLogDialog = false }) {
                                Text("Close")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                CrashLogger.clearCrashLogs(context)
                                crashLogText = "No crashes recorded."
                            }) {
                                Text("Clear Log")
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.xxl))
        }
    }
}

/** Expressive section card: icon rail header + large-tier tonal body. */
@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(SpacingTokens.Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(
                        icon, null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
                Spacer(Modifier.width(SpacingTokens.Spacing.sm))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(SpacingTokens.Spacing.sm))
            content()
        }
    }
}
