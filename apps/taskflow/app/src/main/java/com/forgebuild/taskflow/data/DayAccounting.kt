package com.forgebuild.taskflow.data

import java.util.Calendar

/**
 * Central day-accounting engine: decides how many minutes of a task occupy a
 * given calendar day. Used by the "time remaining today" indicator, the
 * day/week/month/year views, and the due-now / missed sweep so that every
 * category behaves consistently:
 *
 *  - one-off fixed-time tasks
 *  - normal (no-fixed-time) tasks scheduled for today or a specific future date
 *  - recurring templates occurring that day
 *  - generated recurring instances
 *  - carried-over tasks (still active with a stale date/time)
 *  - overnight tasks whose span crosses midnight (split into two day segments)
 */
object DayAccounting {
    const val DAY_MILLIS: Long = 86_400_000L

    fun dayStart(millis: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Exclusive end of the calendar day containing [millis]. */
    fun nextDayStart(millis: Long): Long = dayStart(millis) + DAY_MILLIS

    /** A single contiguous occupancy window [startMillis, endMillis) of a task. */
    data class Segment(val startMillis: Long, val endMillis: Long) {
        val minutes: Long get() = ((endMillis - startMillis) / 60_000L).coerceAtLeast(0L)
    }

    /** Does [template] (recurrence != NONE) have an occurrence on the calendar day containing [dayMillis]? */
    fun occursOnDay(template: Task, dayMillis: Long): Boolean {
        if (template.recurrence == Recurrence.NONE) return false
        val start = dayStart(dayMillis)
        // Respect recurrence expiration: no occurrences after the end date.
        template.recurrenceEndDate?.let { if (start > it) return false }
        // Respect first occurrence: the series cannot occur before the anchor's day.
        val anchor = template.fixedTime ?: template.scheduledDate ?: template.createdAt
        if (start < dayStart(anchor)) return false
        val day = Calendar.getInstance().apply { timeInMillis = start }
        val base = Calendar.getInstance().apply { timeInMillis = anchor }
        return when (template.recurrence) {
            Recurrence.NONE -> false
            Recurrence.DAILY -> true
            Recurrence.WEEKLY -> {
                val mask = if (template.weekdaysMask != 0) template.weekdaysMask
                    else RecurrenceEngine.calendarDayToBit(base.get(Calendar.DAY_OF_WEEK))
                (mask and RecurrenceEngine.calendarDayToBit(day.get(Calendar.DAY_OF_WEEK))) != 0
            }
            Recurrence.MONTHLY ->
                day.get(Calendar.DAY_OF_MONTH) == minOf(
                    base.get(Calendar.DAY_OF_MONTH),
                    day.getActualMaximum(Calendar.DAY_OF_MONTH))
            Recurrence.YEARLY ->
                day.get(Calendar.MONTH) == base.get(Calendar.MONTH) &&
                    day.get(Calendar.DAY_OF_MONTH) == minOf(
                        base.get(Calendar.DAY_OF_MONTH),
                        day.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
    }

    /** The occurrence start time of [template] on the calendar day containing [dayMillis], or null. */
    fun occurrenceOnDay(template: Task, dayMillis: Long): Long? {
        if (!occursOnDay(template, dayMillis)) return null
        val base = template.fixedTime ?: return dayStart(dayMillis)
        val tod = Calendar.getInstance().apply { timeInMillis = base }
        val day = Calendar.getInstance().apply { timeInMillis = dayStart(dayMillis) }
        day.set(Calendar.HOUR_OF_DAY, tod.get(Calendar.HOUR_OF_DAY))
        day.set(Calendar.MINUTE, tod.get(Calendar.MINUTE))
        day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0)
        return day.timeInMillis
    }

    /**
     * Segments of [task] overlapping the calendar day containing [dayMillis].
     * An overnight task yields only the portion inside this day; the caller can
     * query the neighbouring day for the linked remainder.
     */
    fun segmentsOnDay(task: Task, dayMillis: Long, nowMillis: Long): List<Segment> {
        val start = dayStart(dayMillis)
        val end = start + DAY_MILLIS
        val spanStart = effectiveSpanStart(task, dayMillis, nowMillis) ?: return emptyList()
        val spanEnd = spanStart + task.durationMinutes.coerceAtLeast(1L) * 60_000L
        val s = maxOf(spanStart, start)
        val e = minOf(spanEnd, end)
        return if (e > s) listOf(Segment(s, e)) else emptyList()
    }

    /**
     * Resolve the moment this task begins occupying time relative to [dayMillis].
     * Returns null when the task does not touch that day at all.
     *
     * Rules:
     *  - Timed task whose span overlaps the day -> its fixedTime (or template occurrence).
     *  - Timed task scheduled before this day -> carries over to "today" ONLY when queried day is today.
     *  - Untimed task with scheduledDate -> occupies that specific scheduled calendar day.
     *  - Untimed task without scheduledDate -> defaults to the day it was created (createdAt).
     *  - Untimed task scheduled in the future -> NEVER touches today or earlier days.
     *  - Untimed task scheduled in the past -> carries over to "today" ONLY when queried day is today.
     */
    private fun effectiveSpanStart(task: Task, dayMillis: Long, nowMillis: Long): Long? {
        val start = dayStart(dayMillis)
        if (task.isRecurringTemplate) {
            return occurrenceOnDay(task, dayMillis)
        }
        val ft = task.fixedTime
        if (ft != null) {
            val spanEnd = ft + task.durationMinutes.coerceAtLeast(1L) * 60_000L
            val end = start + DAY_MILLIS
            // Overlaps this day naturally (incl. overnight spill from previous day)?
            if (ft < end && spanEnd > start) return ft
            // Carried over: scheduled before this day, still active and not swept -> counts today ONLY if queried day is today.
            if (ft < start && !task.completed && !task.missed && start == dayStart(nowMillis)) return start
            return null
        }
        // Untimed normal task:
        if (task.completed || task.missed) return null
        val targetDay = task.scheduledDate ?: dayStart(task.createdAt)
        // If scheduled for a future day relative to this queried day, it does NOT belong to this day.
        if (targetDay > start) return null
        // Belongs directly to this day.
        if (targetDay == start) return start
        // Carried over from an earlier day -> only active if queried day is today.
        if (targetDay < start && start == dayStart(nowMillis)) return start
        return null
    }

    /** The absolute span of this task's occupancy: [start, start+duration) or null if unschedulable. */
    fun spanOf(task: Task): Segment? {
        val ft = task.fixedTime ?: return null
        return Segment(ft, ft + task.durationMinutes.coerceAtLeast(1L) * 60_000L)
    }

    /** True when this task's span crosses a midnight boundary (occupies two calendar days). */
    fun crossesMidnight(task: Task): Boolean {
        val span = spanOf(task) ?: return false
        return dayStart(span.startMillis) != dayStart(span.endMillis - 1L)
    }

    /** Minutes of [task] allocated to the calendar day containing [dayMillis]. */
    fun minutesOnDay(task: Task, dayMillis: Long, nowMillis: Long): Long =
        segmentsOnDay(task, dayMillis, nowMillis).sumOf { it.minutes }

    /** Total minutes allocated to the day across [tasks] (excluding [excludeTaskId]). */
    fun allocatedMinutesForDay(tasks: List<Task>, dayMillis: Long, nowMillis: Long, excludeTaskId: Long? = null): Long {
        var total = 0L
        for (t in tasks) {
            if (t.id == excludeTaskId) continue
            total += minutesOnDay(t, dayMillis, nowMillis)
        }
        return total
    }

    /**
     * True when a task's occupancy touches the calendar day containing [dayMillis].
     */
    fun touchesDay(task: Task, dayMillis: Long, nowMillis: Long): Boolean =
        segmentsOnDay(task, dayMillis, nowMillis).isNotEmpty()
}
