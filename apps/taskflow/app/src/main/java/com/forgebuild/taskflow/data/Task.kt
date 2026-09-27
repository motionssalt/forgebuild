package com.forgebuild.taskflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Recurrence rules; WEEKLY carries [weekdaysMask] (bit 0=Monday … bit 6=Sunday). */
enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Task visual/behavioral type for color-coding and identification:
 * 1. NORMAL: No fixed time, not recurring
 * 2. FIXED_TIME: Has fixed time, not recurring
 * 3. RECURRING_FIXED: Recurring with fixed time
 * 4. RECURRING_NO_TIME: Recurring without fixed time
 */
enum class TaskType {
    NORMAL,
    FIXED_TIME,
    RECURRING_FIXED,
    RECURRING_NO_TIME
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Fractional priority rank (lexo-style). Higher = closer to top. Never shown raw to user. */
    val rank: Double,
    /** Mandatory duration in minutes (e.g. 15, 30, 60 min). Required for all tasks. */
    val durationMinutes: Long = 30L,
    /** Optional fixed due time, epoch millis. */
    val fixedTime: Long? = null,
    /** Optional scheduled calendar date (epoch millis at dayStart) for normal tasks without a fixed time. */
    val scheduledDate: Long? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    /** For WEEKLY recurrence: bitmask of weekdays (Calendar.MONDAY -> bit 0). */
    val weekdaysMask: Int = 0,
    /** Free-text notes ("info"). Shown via eye-icon quick-peek, edited on the edit screen. */
    val info: String = "",
    /** Nullable parent for infinitely nestable sub-tasks. */
    val parentId: Long? = null,
    val completed: Boolean = false,
    /** Timestamp when completed (epoch millis); used for configurable auto-purge retention. */
    val completedAt: Long? = null,
    /** For recurring series: template stays true; generated occurrences are clones. */
    val isRecurringTemplate: Boolean = false,
    /** Link from a generated occurrence back to its template. */
    val seriesId: Long? = null,
    /** True while a fixed-time task's due moment has arrived and it is not yet completed (today). */
    val dueNow: Boolean = false,
    /** Epoch millis of the day this due-now state belongs to (cleared on rollover). */
    val dueNowDay: Long? = null,
    /** Optional recurrence expiration (epoch millis, end of that day). After it, no new instances are generated. Null = repeat indefinitely. */
    val recurrenceEndDate: Long? = null,
    /** True when a fixed-time task's time passed without completion; shown in the Unfinished view, hidden from active lists. */
    val missed: Boolean = false,
    /** When the task was marked missed (epoch millis). */
    val missedAt: Long? = null,
    /** Countdown timer: epoch millis at which the timer ends (set while RUNNING). Null when not running. */
    val timerEndsAt: Long? = null,
    /** Countdown timer: remaining milliseconds held while PAUSED. Null when not paused. */
    val timerRemainingMs: Long? = null,
    /** True after the countdown has fully elapsed, until the user extends or completes the task. */
    val timerFinished: Boolean = false,
    /** Wall-clock start of the current timer run (needed to map wall time onto a Pomodoro plan). */
    val timerStartedAt: Long? = null,
    /** Pomodoro snapshot taken at timer start: work-interval ms. Null = plain continuous countdown. */
    val pomodoroWorkMs: Long? = null,
    /** Pomodoro snapshot taken at timer start: break-interval ms. Null = plain continuous countdown. */
    val pomodoroBreakMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val taskType: TaskType get() = when {
        recurrence != Recurrence.NONE && fixedTime != null -> TaskType.RECURRING_FIXED
        recurrence != Recurrence.NONE -> TaskType.RECURRING_NO_TIME
        fixedTime != null -> TaskType.FIXED_TIME
        else -> TaskType.NORMAL
    }
}
