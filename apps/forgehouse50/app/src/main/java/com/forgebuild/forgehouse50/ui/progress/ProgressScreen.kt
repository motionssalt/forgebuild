package com.forgebuild.forgehouse50.ui.progress

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.ProgressResponse
import com.forgebuild.forgehouse50.data.ReadingSession
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import com.forgebuild.forgehouse50.ui.formatDurationShort

/**
 * Progress: 50-day grid with completion + quiz state.
 *
 * Cache-First Architecture (Issue 1):
 * Renders immediately from LocalCacheStore on initial composition with
 * zero blank/spinner flash when cached progress exists. Background refresh
 * reconciles data and persists back to cache.
 *
 * Premium Visual Pass (Issue 2):
 * Elevated summary card with rounded corners (24dp), confident bold stats,
 * and elevated individual day cards (16dp) with clear completion badges.
 */
@Composable
fun ProgressScreen(repo: Repository, onOpenDay: (Int) -> Unit) {
    // 1) Instant synchronous cache hydration
    val cachedProgress = remember { repo.cache.getProgress() }
    val cachedPlan = remember { repo.cache.resolvePlan() }

    var progress by remember { mutableStateOf(cachedProgress) }
    var catchupStatus by remember { mutableStateOf(ReadingSession.statusLine(cachedPlan)) }

    // 2) Background refresh + reconciliation
    LaunchedEffect(Unit) {
        val fresh = repo.refreshProgress()
        if (fresh != null) {
            progress = fresh
            val t = repo.cache.getToday()
            val me = repo.cache.getMe()
            val plan = ReadingSession.resolve(fresh, t, me?.programme?.programme_end_date)
            catchupStatus = ReadingSession.statusLine(plan)
        }
    }

    val p = progress
    if (p == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Progress",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(16.dp))

        // Elevated Overview Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "${p.totals.reading_days_completed} of ${p.programme.total_days}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Reading days completed",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${p.totals.percent_completed}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { (p.totals.percent_completed / 100f).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "${p.totals.chapters_completed}/${p.programme.total_chapters} chapters · " +
                            "Streak ${p.totals.streak_current} (best ${p.totals.streak_longest}) · " +
                            formatDurationShort(p.totals.reading_seconds_total) + " read",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (catchupStatus.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (catchupStatus.startsWith("You need"))
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = catchupStatus,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 50-day list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(p.days, key = { it.day_number }) { d ->
                Card(
                    onClick = { if (!d.is_future) onOpenDay(d.day_number) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (d.is_future)
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        else
                            MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (d.is_future) 0.dp else 1.dp
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (d.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (d.completed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )

                        Spacer(Modifier.size(14.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Day ${d.day_number} — ${d.assignment}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (d.completed) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (d.is_future) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = buildString {
                                    append(d.date)
                                    if (d.quiz_taken) append(" · quiz ${d.quiz_score}/${d.quiz_total}")
                                    if (d.notes_count > 0) append(" · ${d.notes_count} notes")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
