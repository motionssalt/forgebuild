package com.forgebuild.taskflow.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.data.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unfinished view — SEPARATE from Completed. Holds fixed-time tasks whose time passed
 * without completion. They leave the active list (no carry-over, no due-now pin) and can
 * be reviewed here, restored to active (as an untimed task) or deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnfinishedTasksScreen(
    vm: TaskViewModel,
    onBack: () -> Unit
) {
    val missedList by vm.unfinishedTasks.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Unfinished Tasks", style = MaterialTheme.typography.titleLarge)
                        Text("${missedList.size} missed", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(EngineIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (missedList.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(EngineIcons.Delete, contentDescription = "Clear all", tint = MaterialTheme.colorScheme.error)
                        }
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
                .padding(horizontal = SpacingTokens.Spacing.md)
        ) {
            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(SpacingTokens.Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)
                ) {
                    Icon(EngineIcons.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Text(
                        "Fixed-time tasks whose time passed without completion land here — they no longer carry over or stay pinned in the active list. Restore one to make it active again (as an untimed task).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            if (missedList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            EngineIcons.Alarm, null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(SpacingTokens.Spacing.xxxl * 1.5f)
                        )
                        Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                        Text("Nothing missed", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Missed fixed-time tasks will show up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val uniqueMissed = remember(missedList) { missedList.distinctBy { it.id } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                ) {
                    items(uniqueMissed, key = { it.id }) { task ->
                        MissedTaskItem(
                            task = task,
                            whenText = task.fixedTime?.let { "Was due ${dateFormat.format(Date(it))}" } ?: "Missed",
                            onRestore = { vm.restoreMissed(task) },
                            onDelete = { vm.delete(task) }
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear unfinished tasks?") },
            text = { Text("This will permanently delete all ${missedList.size} missed tasks.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearMissed()
                        showClearDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MissedTaskItem(
    task: Task,
    whenText: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingTokens.Spacing.sm, vertical = SpacingTokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                EngineIcons.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = SpacingTokens.Spacing.xs)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = whenText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "• ${task.durationMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            TextButton(onClick = onRestore) { Text("Restore") }
            IconButton(onClick = onDelete) {
                Icon(
                    EngineIcons.Delete,
                    contentDescription = "Delete permanently",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
