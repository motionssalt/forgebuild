package com.forgebuild.forgehouse50.ui.home

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.MeResponse
import com.forgebuild.forgehouse50.data.ProgressResponse
import com.forgebuild.forgehouse50.data.ReadingSession
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.TodayResponse
import com.forgebuild.forgehouse50.ui.CommunityLinks
import com.forgebuild.forgehouse50.ui.ExpressiveButton
import com.forgebuild.forgehouse50.ui.ExpressiveButtonLoader

/**
 * Home / Today dashboard.
 *
 * Cache-First Architecture (Issue 1):
 * Initialized synchronously on the very first composition frame using
 * LocalCacheStore. Real user stats, days, points, streak, and catch-up plan
 * render immediately with ZERO zero/blank/default flash. Background refresh
 * reconciles data and writes back to cache and widget store.
 *
 * Premium Visual Pass (Issue 2):
 * Material 3 Expressive styling inspired by the premium reference design:
 * elevated stat cards with bold prominent numerals and tinted icon badges,
 * pill-shaped primary reading action, and full-width pill action rows with
 * leading circular chips and trailing chevrons.
 */
@Composable
fun HomeScreen(
    repo: Repository,
    reloadTick: Int,
    onOpenRead: (Int) -> Unit,
    onOpenSession: (List<Int>) -> Unit = {},
    onOpenQuiz: (Int) -> Unit,
    onOpenNotes: () -> Unit,
    onOpenAdmin: () -> Unit,
) {
    val context = LocalContext.current

    // Synchronous cache hydration on frame 1: eliminates zero/placeholder flash
    val cachedToday = remember { repo.cache.getToday() }
    val cachedMe = remember { repo.cache.getMe() }
    val cachedProgress = remember { repo.cache.getProgress() }
    val cachedPlan = remember(cachedToday, cachedProgress, cachedMe) {
        if (cachedToday != null) {
            ReadingSession.resolve(cachedProgress, cachedToday, cachedMe?.programme?.programme_end_date)
        } else null
    }

    var today by remember { mutableStateOf(cachedToday) }
    var me by remember { mutableStateOf(cachedMe) }
    var progress by remember { mutableStateOf(cachedProgress) }
    var plan by remember { mutableStateOf(cachedPlan) }
    var quizDone by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    // Background refresh + reconciliation
    LaunchedEffect(reloadTick) {
        refreshing = true
        val freshToday = repo.refreshToday()
        if (freshToday != null) today = freshToday

        val freshMe = repo.refreshMe()
        if (freshMe != null) me = freshMe

        val freshProgress = repo.refreshProgress()
        if (freshProgress != null) progress = freshProgress

        refreshing = false
    }

    // Keep session plan reconciled whenever today, progress, or me land
    LaunchedEffect(today, progress, me) {
        val t = today ?: return@LaunchedEffect
        plan = ReadingSession.resolve(progress, t, me?.programme?.programme_end_date)
        repo.cache.syncWidget()
    }

    val block = today?.today
    LaunchedEffect(block?.day_number, block?.completed) {
        val d = block?.day_number
        if (d != null) {
            val cachedQuiz = repo.cache.getQuiz(d)
            if (cachedQuiz != null) {
                quizDone = cachedQuiz.attempt != null
            }
            runCatching { repo.api.quiz(d) }.onSuccess {
                repo.cache.saveQuiz(d, it)
                quizDone = it.attempt != null
            }
        } else {
            quizDone = false
        }
    }

    val userName = me?.name ?: repo.session.userName ?: ""
    val greeting = if (userName.isNotBlank()) "Hello, $userName" else "Hello"
    val dateText = today?.date ?: ""

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        // ── Header Greeting ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (dateText.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (refreshing) {
                ExpressiveButtonLoader()
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Premium Stat Cards Strip ──────────────────────────────────────
        val stats = me?.stats ?: today?.stats
        val pointsVal = "${stats?.points ?: 0}"
        val daysCompleted = stats?.days_completed ?: 0
        val totalDays = today?.programme?.total_days ?: 50
        val daysVal = "$daysCompleted / $totalDays"
        val streakVal = "${stats?.streak_current ?: 0}"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PremiumStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Star,
                value = pointsVal,
                label = "Points",
                badgeContainer = MaterialTheme.colorScheme.primaryContainer,
                badgeTint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            PremiumStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CalendarMonth,
                value = daysVal,
                label = "Days",
                badgeContainer = MaterialTheme.colorScheme.secondaryContainer,
                badgeTint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            PremiumStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.LocalFireDepartment,
                value = streakVal,
                label = "Streak",
                badgeContainer = MaterialTheme.colorScheme.tertiaryContainer,
                badgeTint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Today Reading Card ────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                if (today == null) {
                    Text(
                        "Loading today's reading…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (today?.is_reading_day == true && block != null) {
                    val a = block.assignment
                    Text(
                        text = "Day ${block.day_number} of $totalDays",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = a?.summary ?: "Today's reading",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (a != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${a.chapter_count} chapters · about ${a.est_minutes} min",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    if (block.completed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Reading complete",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        if (quizDone) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                        RoundedCornerShape(16.dp),
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Quiz done — day fully complete",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            ExpressiveButton(
                                onClick = { onOpenQuiz(block.day_number) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Quiz, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Take today's quiz", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    } else {
                        val pl = plan
                        val buttonText = when {
                            pl != null && pl.catchup -> "Catch up: days ${pl.days.joinToString(" + ")}"
                            block.reading_seconds > 0 -> "Continue reading"
                            else -> "Start reading"
                        }

                        ExpressiveButton(
                            onClick = {
                                if (pl != null && pl.catchup && pl.days.isNotEmpty()) {
                                    onOpenSession(pl.days)
                                } else {
                                    onOpenRead(block.day_number)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(Icons.Filled.MenuBook, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = buttonText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null,
                                    Modifier.size(20.dp),
                                )
                            }
                        }

                        if (pl != null && pl.catchup) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = ReadingSession.statusLine(pl),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(
                        "Rest day",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    val next = today?.next_reading_day
                    if (next != null) {
                        Text(
                            "Next: Day ${next.day_number} on ${next.date}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Action Rows (Mockup-inspired full-width pill rows) ─────────────
        val notesCount = me?.stats?.notes_total ?: repo.cache.getNotes()?.notes?.size ?: 0

        ActionRowItem(
            icon = Icons.Filled.Edit,
            title = "My notes ($notesCount)",
            onClick = onOpenNotes,
            badgeContainer = MaterialTheme.colorScheme.primaryContainer,
            badgeTint = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Spacer(Modifier.height(10.dp))

        ActionRowItem(
            icon = Icons.Filled.Groups,
            title = "Join ForgeHouse WhatsApp Group",
            onClick = { CommunityLinks.open(context, CommunityLinks.WHATSAPP_GROUP_URL) },
            badgeContainer = MaterialTheme.colorScheme.secondaryContainer,
            badgeTint = MaterialTheme.colorScheme.onSecondaryContainer,
        )

        Spacer(Modifier.height(10.dp))

        ActionRowItem(
            icon = Icons.Filled.Favorite,
            title = "Support ForgeHouse Global",
            onClick = { CommunityLinks.open(context, CommunityLinks.SUPPORT_WA_ME_URL) },
            badgeContainer = MaterialTheme.colorScheme.tertiaryContainer,
            badgeTint = MaterialTheme.colorScheme.onTertiaryContainer,
        )

        if (repo.session.isAdmin || me?.role == "admin") {
            Spacer(Modifier.height(10.dp))
            ActionRowItem(
                icon = Icons.Filled.AdminPanelSettings,
                title = "Admin Dashboard",
                onClick = onOpenAdmin,
                badgeContainer = MaterialTheme.colorScheme.errorContainer,
                badgeTint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Premium stat card with soft elevated container, circular badge,
 * bold prominent numeral as focal point, and label beneath.
 */
@Composable
private fun PremiumStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    badgeContainer: Color,
    badgeTint: Color,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(badgeContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = badgeTint,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Clean full-width pill action row with circular icon chip,
 * label, and trailing chevron.
 */
@Composable
private fun ActionRowItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    badgeContainer: Color,
    badgeTint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badgeContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = badgeTint,
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
