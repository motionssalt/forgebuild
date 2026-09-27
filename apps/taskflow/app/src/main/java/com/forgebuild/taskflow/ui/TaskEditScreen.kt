package com.forgebuild.taskflow.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.components.ExpressiveLoading
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.RecurrenceEngine
import com.forgebuild.taskflow.data.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(
    vm: TaskViewModel,
    taskId: Long,
    onClose: () -> Unit
) {
    val task by produceState<Task?>(null, taskId) { value = vm.repo.get(taskId) }
    val subtasks by produceState<List<Task>>(emptyList(), taskId) { value = vm.repo.siblingsOf(taskId) }
    // Parent duration-cap error (live): cumulative direct children must stay ≤ parent duration.
    val childCapError by produceState<String?>(null, taskId, subtasks.size) {
        value = vm.repo.childDurationError(taskId, 0L)
    }
    val context = LocalContext.current

    val current = task
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
        return
    }

    var title by remember(current.id) { mutableStateOf(current.title) }
    var durationMinutes by remember(current.id) { mutableLongStateOf(current.durationMinutes) }
    var info by remember(current.id) { mutableStateOf(current.info) }
    var scheduledDate by remember(current.id) { mutableStateOf(current.scheduledDate) }
    var fixedTime by remember(current.id) { mutableStateOf(current.fixedTime) }
    var recurrence by remember(current.id) { mutableStateOf(current.recurrence) }
    var weekdaysMask by remember(current.id) { mutableIntStateOf(current.weekdaysMask) }
    var endDate by remember(current.id) { mutableStateOf(current.recurrenceEndDate) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var newSubtaskTitle by remember { mutableStateOf("") }
    var newSubtaskDuration by remember { mutableLongStateOf(15L) }

    val fmt = remember { SimpleDateFormat("EEE, MMM d yyyy · HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("MMM d yyyy", Locale.getDefault()) }
    val dateOnlyFmt = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val timeOnlyFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Task", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(EngineIcons.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(EngineIcons.Delete, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md)
        ) {
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(SpacingTokens.Spacing.md)
                    )
                }
            }

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            // Duration (mandatory)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Duration (mandatory)", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { durationMinutes = (durationMinutes - 15L).coerceAtLeast(5L) }) {
                        Text("-15m")
                    }
                    Text(
                        "${durationMinutes} min",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    OutlinedButton(onClick = { durationMinutes = durationMinutes + 15L }) {
                        Text("+15m")
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15L, 30L, 45L, 60L, 90L, 120L).forEach { d ->
                        val sel = durationMinutes == d
                        AssistChip(
                            onClick = { durationMinutes = d },
                            label = { Text("${d}m") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        )
                    }
                }
            }

            // Notes / Info
            OutlinedTextField(
                value = info,
                onValueChange = { info = it },
                label = { Text("Info & notes (hidden on list, peek with eye icon)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = MaterialTheme.shapes.medium
            )

            // Scheduled date (optional, defaults to Today)
            Text("Scheduled date", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    val cal = Calendar.getInstance()
                    val target = scheduledDate ?: fixedTime
                    if (target != null) cal.timeInMillis = target
                    DatePickerDialog(context, { _, y, m, d ->
                        val c = Calendar.getInstance()
                        c.set(y, m, d, 0, 0, 0)
                        c.set(Calendar.MILLISECOND, 0)
                        val pickedDay = c.timeInMillis
                        val todayStart = com.forgebuild.taskflow.data.DayAccounting.dayStart(System.currentTimeMillis())
                        scheduledDate = if (pickedDay == todayStart) null else pickedDay
                        fixedTime?.let { ft ->
                            val ftCal = Calendar.getInstance().apply { timeInMillis = ft }
                            c.set(Calendar.HOUR_OF_DAY, ftCal.get(Calendar.HOUR_OF_DAY))
                            c.set(Calendar.MINUTE, ftCal.get(Calendar.MINUTE))
                            fixedTime = c.timeInMillis
                        }
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }) {
                    Icon(EngineIcons.CalendarToday, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (scheduledDate == null) "Today" else dateOnlyFmt.format(Date(scheduledDate!!)))
                }
                if (scheduledDate != null) {
                    OutlinedButton(onClick = {
                        scheduledDate = null
                        fixedTime?.let { ft ->
                            val ftCal = Calendar.getInstance().apply { timeInMillis = ft }
                            val c = Calendar.getInstance()
                            c.set(Calendar.HOUR_OF_DAY, ftCal.get(Calendar.HOUR_OF_DAY))
                            c.set(Calendar.MINUTE, ftCal.get(Calendar.MINUTE))
                            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                            fixedTime = c.timeInMillis
                        }
                    }) { Text("Today") }
                }
            }

            // Fixed clock time (optional)
            Text("Fixed clock time (optional)", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    val cal = Calendar.getInstance()
                    fixedTime?.let { cal.timeInMillis = it }
                    TimePickerDialog(context, { _, h, min ->
                        val baseDay = scheduledDate ?: com.forgebuild.taskflow.data.DayAccounting.dayStart(System.currentTimeMillis())
                        val c = Calendar.getInstance().apply { timeInMillis = baseDay }
                        c.set(Calendar.HOUR_OF_DAY, h)
                        c.set(Calendar.MINUTE, min)
                        c.set(Calendar.SECOND, 0)
                        c.set(Calendar.MILLISECOND, 0)
                        fixedTime = c.timeInMillis
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }) {
                    Icon(EngineIcons.Alarm, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (fixedTime == null) "No fixed time (normal task)" else timeOnlyFmt.format(Date(fixedTime!!)))
                }
                if (fixedTime != null) {
                    OutlinedButton(onClick = { fixedTime = null }) { Text("Clear time") }
                }
            }
            // Recurrence
            Text("Repeat rule", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                Recurrence.entries.forEach { r ->
                    FilterChip(
                        selected = recurrence == r,
                        onClick = { recurrence = r },
                        label = { Text(r.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            if (recurrence == Recurrence.WEEKLY) {
                Text("Select active days:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    RecurrenceEngine.WEEKDAY_LABELS.forEachIndexed { i, label ->
                        val bit = 1 shl i
                        FilterChip(
                            selected = (weekdaysMask and bit) != 0,
                            onClick = { weekdaysMask = weekdaysMask xor bit },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Recurrence expiration (optional): repeat until <date>, then stop generating instances.
            if (recurrence != Recurrence.NONE) {
                Text("Until (optional — recurrence stops after this date)", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = {
                        val cal = Calendar.getInstance()
                        endDate?.let { cal.timeInMillis = it }
                        DatePickerDialog(context, { _, y, m, d ->
                            val c = Calendar.getInstance()
                            c.set(y, m, d, 23, 59, 59)
                            c.set(Calendar.MILLISECOND, 999)
                            endDate = c.timeInMillis
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }) {
                        Text(if (endDate == null) "No end date" else "Until ${dateFmt.format(Date(endDate!!))}")
                    }
                    if (endDate != null) {
                        OutlinedButton(onClick = { endDate = null }) { Text("Clear") }
                    }
                }
            }

            // Sub-tasks section
            Spacer(Modifier.height(SpacingTokens.Spacing.xs))
            Text("Sub-tasks (${subtasks.size})", style = MaterialTheme.typography.titleSmall)
            // Live budget: Σ direct children durations ≤ this task's duration (incl. overnight spans).
            val subUsed = subtasks.sumOf { it.durationMinutes.coerceAtLeast(1L) }
            Text(
                "Sub-task budget: ${subUsed}m of ${current.durationMinutes}m used",
                style = MaterialTheme.typography.labelMedium,
                color = if (subUsed > current.durationMinutes) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (subtasks.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(SpacingTokens.Spacing.sm), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        subtasks.forEach { st ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(EngineIcons.SubdirectoryArrowRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(st.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("${st.durationMinutes}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }

            // Add sub-task
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
            ) {
                OutlinedTextField(
                    value = newSubtaskTitle,
                    onValueChange = { newSubtaskTitle = it },
                    placeholder = { Text("Add a subtask…") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                IconButton(
                    onClick = {
                        if (newSubtaskTitle.isNotBlank()) {
                            // Pass 7 FIX: nest under THIS task (was defaulting to the nav
                            // level, creating a standalone root task instead of a child).
                            vm.addTask(
                                title = newSubtaskTitle,
                                durationMinutes = newSubtaskDuration,
                                parentId = current.id
                            )
                            newSubtaskTitle = ""
                        }
                    },
                    enabled = newSubtaskTitle.isNotBlank()
                ) {
                    Icon(EngineIcons.Add, "Add subtask", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.md))

            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Title cannot be empty."
                        return@Button
                    }
                    errorMessage = null
                    vm.saveEdit(
                        current.copy(
                            title = title.trim(),
                            durationMinutes = durationMinutes.coerceAtLeast(1L),
                            info = info.trim(),
                            fixedTime = fixedTime,
                            scheduledDate = scheduledDate,
                            recurrence = recurrence,
                            weekdaysMask = weekdaysMask,
                            recurrenceEndDate = if (recurrence == Recurrence.NONE) null else endDate
                        )
                    ) { success, err ->
                        if (success) {
                            onClose()
                        } else {
                            errorMessage = err
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes()
            ) {
                Text("Save Changes")
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.lg))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete task?") },
            text = { Text("This will permanently delete \"${current.title}\" and all of its sub-tasks.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(current)
                        showDeleteConfirm = false
                        onClose()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
