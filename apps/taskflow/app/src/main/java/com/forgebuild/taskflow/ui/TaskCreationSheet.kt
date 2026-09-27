package com.forgebuild.taskflow.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.RecurrenceEngine
import com.forgebuild.taskflow.data.TaskType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Full up-front task creation sheet: the "+" action opens this BEFORE anything is saved.
 * Title, mandatory duration, fixed time, recurrence (+ optional expiration), info and the
 * resulting type are all configured here; the task is written only when the user confirms.
 * (Fields stay editable afterwards from the edit screen as before.)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskCreationSheet(
    vm: TaskViewModel,
    parentId: Long?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var durationMinutes by remember { mutableLongStateOf(30L) }
    var info by remember { mutableStateOf("") }
    var scheduledDate by remember { mutableStateOf<Long?>(null) }
    var fixedTime by remember { mutableStateOf<Long?>(null) }
    var recurrence by remember { mutableStateOf(Recurrence.NONE) }
    var weekdaysMask by remember { mutableIntStateOf(0) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fmt = remember { SimpleDateFormat("EEE, MMM d yyyy · HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("MMM d yyyy", Locale.getDefault()) }
    val dateOnlyFmt = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val timeOnlyFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val previewType = when {
        recurrence != Recurrence.NONE && fixedTime != null -> TaskType.RECURRING_FIXED
        recurrence != Recurrence.NONE -> TaskType.RECURRING_NO_TIME
        fixedTime != null -> TaskType.FIXED_TIME
        else -> TaskType.NORMAL
    }
    val (typeColor, typeLabel, typeIcon) = when (previewType) {
        TaskType.RECURRING_FIXED -> Triple(MaterialTheme.colorScheme.tertiary, "Recurring, scheduled", EngineIcons.Repeat)
        TaskType.RECURRING_NO_TIME -> Triple(MaterialTheme.colorScheme.primary, "Recurring, untimed", EngineIcons.Repeat)
        TaskType.FIXED_TIME -> Triple(MaterialTheme.colorScheme.secondary, "Scheduled (fixed time)", EngineIcons.Alarm)
        TaskType.NORMAL -> Triple(MaterialTheme.colorScheme.outlineVariant, "Normal task", EngineIcons.CheckCircle)
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)
        ) {
            Text("New task", style = MaterialTheme.typography.titleLarge)

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

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
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

            // Info / notes
            OutlinedTextField(
                value = info,
                onValueChange = { info = it },
                label = { Text("Info & notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = MaterialTheme.shapes.medium
            )

            // Type preview (color/type derived from the choices above)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = typeColor.copy(alpha = 0.18f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(14.dp))
                        Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = typeColor)
                    }
                }
                if (parentId != null) {
                    Text(
                        "Created as a sub-task of the current task.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.xs))

            // The task is written ONLY on this confirm — never bare-created first.
            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Title cannot be empty."
                        return@Button
                    }
                    errorMessage = null
                    vm.addTask(
                        title = title.trim(),
                        durationMinutes = durationMinutes.coerceAtLeast(1L),
                        fixedTime = fixedTime,
                        scheduledDate = scheduledDate,
                        recurrence = recurrence,
                        weekdaysMask = weekdaysMask,
                        info = info.trim(),
                        recurrenceEndDate = if (recurrence == Recurrence.NONE) null else endDate
                    )
                    onClose()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes()
            ) {
                Text("Create task")
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }

            Spacer(Modifier.height(SpacingTokens.Spacing.lg))
        }
    }
}
