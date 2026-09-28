package com.forgebuild.taskflow.ai

import com.forgebuild.taskflow.data.DayAccounting
import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.Task
import com.forgebuild.taskflow.data.TaskRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Declares Gemini function-calling schemas and executes them against the local Room DB.
 * Supports all task types: untimed future tasks, fixed-time, recurring, cross-midnight,
 * sub-tasks at any depth, and timers. Validates time budgets against the actual target date.
 */
class TaskAgentTools(private val repo: TaskRepository) {

    private fun prop(type: String, desc: String): JsonObject = buildJsonObject {
        put("type", type)
        put("description", desc)
    }

    /** Real tools array for Gemini generateContent API. */
    fun apiTools(): JsonArray {
        val decls = mutableListOf<JsonObject>()

        fun d(name: String, desc: String, props: Map<String, JsonObject>, required: List<String> = emptyList()) {
            decls.add(buildJsonObject {
                put("name", name)
                put("description", desc)
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
                    if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
                }
            })
        }

        d("get_day_status", "Get current device local time, time zone, and remaining unallocated minutes for today or any specified date.", mapOf(
            "date" to prop("string", "Optional target date 'yyyy-MM-dd' (e.g. tomorrow, next Friday). If omitted, returns today's status.")
        ))

        d("list_tasks", "List tasks. Inspect sub-tasks via parent_id or parent_title, or filter by date or title query.", mapOf(
            "parent_id" to prop("integer", "Parent task id; omit for top level."),
            "parent_title" to prop("string", "Title or keywords of parent task to inspect sub-tasks of."),
            "date" to prop("string", "Optional target date 'yyyy-MM-dd' to filter tasks scheduled for or occurring on that date."),
            "query" to prop("string", "Optional case-insensitive title filter.")
        ))

        d("create_task", "Create any type of task: normal untimed (for today or future date), fixed-time (including cross-midnight), recurring, or sub-task. Duration is mandatory; infer a reasonable estimate (15, 30, 45, 60m) if not specified. Proactively capture explanatory details in info.", mapOf(
            "title" to prop("string", "Task title (required)."),
            "duration_minutes" to prop("integer", "Estimated duration in minutes (e.g. 15, 30, 45, 60). Mandatory."),
            "parent_id" to prop("integer", "Numeric parent task id for a sub-task."),
            "parent_title" to prop("string", "Title or keywords of parent task to nest inside (e.g. 'Groceries'). Automatically resolves parent if parent_id is omitted."),
            "scheduled_date" to prop("string", "Target date 'yyyy-MM-dd' for plain/normal tasks scheduled for a future day without a fixed clock time (e.g. '2026-10-05')."),
            "fixed_time" to prop("string", "Local datetime 'yyyy-MM-dd HH:mm'. ONLY set this when the user explicitly requested a specific clock time. Untimed is the default."),
            "recurrence" to prop("string", "none, daily, weekly, monthly, yearly."),
            "weekdays" to prop("string", "For weekly recurrence: comma list like tue or mon,wed,fri."),
            "recurrence_end" to prop("string", "Optional end date of recurrence as 'yyyy-MM-dd'. Omit for indefinite."),
            "info" to prop("string", "Detailed notes, steps, reasoning, or explanatory context for the task. PROACTIVELY populate this whenever the user explains details.")
        ), listOf("title"))

        d("update_task", "Update any task field by ID or task title.", mapOf(
            "task_id" to prop("integer", "Task id (required unless task_title is provided)."),
            "task_title" to prop("string", "Title of the task to update if task_id is not known."),
            "title" to prop("string", "New title."),
            "duration_minutes" to prop("integer", "New duration in minutes."),
            "scheduled_date" to prop("string", "Target date 'yyyy-MM-dd' for plain/normal task without a fixed clock time, or 'clear' to reset."),
            "fixed_time" to prop("string", "Local datetime 'yyyy-MM-dd HH:mm', or 'clear' to remove."),
            "recurrence" to prop("string", "none, daily, weekly, monthly, yearly."),
            "weekdays" to prop("string", "For weekly: comma list."),
            "recurrence_end" to prop("string", "Recurrence end date 'yyyy-MM-dd', or 'clear' to make it indefinite."),
            "info" to prop("string", "New info text.")
        ))

        d("delete_task", "Delete a task and all its sub-tasks by ID or title.", mapOf(
            "task_id" to prop("integer", "Task id."),
            "task_title" to prop("string", "Title of task to delete if task_id not known.")
        ))

        d("complete_task", "Mark a task complete or incomplete by ID or title.", mapOf(
            "task_id" to prop("integer", "Task id."),
            "task_title" to prop("string", "Title of task to complete if task_id not known."),
            "completed" to prop("boolean", "True to mark done, false to reopen.")
        ))

        d("reorder_task", "Move a task between two others (or top/bottom) at its current level.", mapOf(
            "task_id" to prop("integer", "Task to move (required)."),
            "above_task_id" to prop("integer", "Task directly above new position; omit for very top."),
            "below_task_id" to prop("integer", "Task directly below new position; omit for very bottom.")
        ), listOf("task_id"))

        d("move_task", "Reparent a task under another task (or to top level) by ID or title.", mapOf(
            "task_id" to prop("integer", "Task to move."),
            "task_title" to prop("string", "Title of task to move if task_id not known."),
            "new_parent_id" to prop("integer", "New parent task id; omit for top level."),
            "new_parent_title" to prop("string", "Title of new parent task to nest under; omit for top level.")
        ))

        d("start_timer", "Start (or resume) the countdown timer on a task, based on its duration.", mapOf(
            "task_id" to prop("integer", "Task id."),
            "task_title" to prop("string", "Title of task if task_id not known.")
        ))

        d("pause_timer", "Pause the currently running countdown timer on a task.", mapOf(
            "task_id" to prop("integer", "Task id."),
            "task_title" to prop("string", "Title of task if task_id not known.")
        ))

        d("extend_timer", "Add extra time to a task's countdown timer. Works while running, paused, or finished.", mapOf(
            "task_id" to prop("integer", "Task id."),
            "task_title" to prop("string", "Title of task if task_id not known."),
            "amount" to prop("integer", "How much time to add (required)."),
            "unit" to prop("string", "'minutes' (default) or 'hours'.")
        ), listOf("amount"))

        return JsonArray(listOf(buildJsonObject { putJsonArray("functionDeclarations") { decls.forEach { add(it) } } }))
    }

    private fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank() || s.equals("clear", true)) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(s.trim())?.time
        }.getOrNull()
    }

    private fun parseRecurrence(s: String?): Recurrence = when (s?.trim()?.lowercase()) {
        "daily" -> Recurrence.DAILY
        "weekly" -> Recurrence.WEEKLY
        "monthly" -> Recurrence.MONTHLY
        "yearly" -> Recurrence.YEARLY
        else -> Recurrence.NONE
    }

    private fun parseWeekdays(s: String?): Int {
        if (s.isNullOrBlank()) return 0
        var mask = 0
        s.lowercase().split(",", " ", ";").forEach { d ->
            when (d.trim().take(3)) {
                "mon" -> mask = mask or 1
                "tue" -> mask = mask or 2
                "wed" -> mask = mask or 4
                "thu" -> mask = mask or 8
                "fri" -> mask = mask or 16
                "sat" -> mask = mask or 32
                "sun" -> mask = mask or 64
            }
        }
        return mask
    }

    /** Parse 'yyyy-MM-dd' into start-of-day millis; 'clear'/blank -> null. */
    private fun parseDate(s: String?): Long? {
        if (s.isNullOrBlank() || s.equals("clear", true)) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(s.trim())?.let { d ->
                val cal = Calendar.getInstance().apply { time = d }
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        }.getOrNull()
    }

    /** Parse 'yyyy-MM-dd' into end-of-day millis; 'clear'/blank -> null. */
    private fun parseEndDate(s: String?): Long? {
        if (s.isNullOrBlank() || s.equals("clear", true)) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(s.trim())?.let { d ->
                val c = Calendar.getInstance().apply { time = d }
                c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59)
                c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
                c.timeInMillis
            }
        }.getOrNull()
    }

    private fun JsonObject.str(k: String): String? = this[k]?.let { if (it is JsonPrimitive) it.content else null }
    private fun JsonObject.longOrNull(k: String): Long? = this[k]?.let {
        runCatching { (it as JsonPrimitive).content.toLong() }.getOrNull()
    }

    /** Execute one function call and return a natural-language summary. */
    suspend fun execute(name: String, args: JsonObject): String {
        return when (name) {
            "get_day_status" -> {
                val now = System.currentTimeMillis()
                val targetDay = args.str("date")?.let { parseDate(it) } ?: DayAccounting.dayStart(now)
                val fmt = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.getDefault())
                val tz = TimeZone.getDefault().id
                val remaining = repo.getRemainingMinutesForDay(targetDay)
                val allocated = repo.getAllocatedMinutesForDay(targetDay)
                val isToday = DayAccounting.dayStart(targetDay) == DayAccounting.dayStart(now)
                val dayDesc = if (isToday) "Today (${fmt.format(Date(now))})" else fmt.format(Date(targetDay))
                "$dayDesc in $tz. Total allocated: ${allocated}m. Remaining unallocated: ${remaining}m."
            }

            "list_tasks" -> {
                val parent = args.longOrNull("parent_id")
                    ?: args.str("parent_title")?.let { repo.findByTitle(it)?.id }
                val targetDate = args.str("date")?.let { parseDate(it) }
                val now = System.currentTimeMillis()

                var list = if (parent != null) {
                    repo.siblingsOf(parent)
                } else if (targetDate != null) {
                    repo.allActiveNow().filter { DayAccounting.touchesDay(it, targetDate, now) }
                } else {
                    repo.siblingsOf(null)
                }

                args.str("query")?.let { q -> list = list.filter { it.title.contains(q, true) } }
                if (list.isEmpty()) "No tasks found." else list.joinToString("\n") { t ->
                    val schedStr = when {
                        t.fixedTime != null -> " @ " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(t.fixedTime))
                        t.scheduledDate != null -> " on " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(t.scheduledDate))
                        else -> ""
                    }
                    val recStr = if (t.recurrence != Recurrence.NONE) " (${t.recurrence})" else ""
                    val timerStr = when (com.forgebuild.taskflow.data.TimerEngine.stateOf(t)) {
                        com.forgebuild.taskflow.data.TimerEngine.TimerState.RUNNING ->
                            " [timer running, " + (com.forgebuild.taskflow.data.TimerEngine.remainingMs(t) / 60000L) + "m left]"
                        com.forgebuild.taskflow.data.TimerEngine.TimerState.PAUSED ->
                            " [timer paused, " + (com.forgebuild.taskflow.data.TimerEngine.remainingMs(t) / 60000L) + "m left]"
                        com.forgebuild.taskflow.data.TimerEngine.TimerState.FINISHED -> " [timer finished — awaiting extend/complete]"
                        else -> ""
                    }
                    val infoStr = if (t.info.isNotBlank()) " [info: ${t.info}]" else ""
                    "#${t.id} ${if (t.completed) "[done] " else ""}${t.title} (${t.durationMinutes}m)$schedStr$recStr$timerStr$infoStr"
                }
            }

            "create_task" -> {
                val title = args.str("title") ?: return "Missing title."
                val duration = (args.longOrNull("duration_minutes") ?: 30L).coerceAtLeast(1L)
                val fixedTime = parseTime(args.str("fixed_time"))
                val scheduledDate = parseDate(args.str("scheduled_date"))
                val recurrence = parseRecurrence(args.str("recurrence"))
                val weekdaysMask = parseWeekdays(args.str("weekdays"))
                val recurrenceEnd = if (recurrence != Recurrence.NONE) parseEndDate(args.str("recurrence_end")) else null
                val info = args.str("info") ?: ""

                // Resolve parent by ID or title
                val parentId = args.longOrNull("parent_id")
                    ?: args.str("parent_title")?.let { repo.findByTitle(it)?.id }

                val now = System.currentTimeMillis()
                val todayStart = DayAccounting.dayStart(now)
                val targetDay = when {
                    fixedTime != null -> DayAccounting.dayStart(fixedTime)
                    scheduledDate != null -> DayAccounting.dayStart(scheduledDate)
                    else -> todayStart
                }
                val isToday = targetDay == todayStart
                val targetDateLabel = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(targetDay))

                val proposed = Task(
                    title = title,
                    rank = 0.0,
                    durationMinutes = duration,
                    fixedTime = fixedTime,
                    scheduledDate = scheduledDate,
                    recurrence = recurrence,
                    weekdaysMask = weekdaysMask,
                    recurrenceEndDate = recurrenceEnd
                )

                // Time budget check against the actual target date
                if (fixedTime != null) {
                    val targetDaySegment = DayAccounting.minutesOnDay(proposed, targetDay, now)
                    val remTarget = repo.getRemainingMinutesForDay(targetDay)
                    if (targetDaySegment > remTarget) {
                        return "Cannot create task \"$title\" (${duration}m): portion landing on $targetDateLabel (${targetDaySegment}m) exceeds remaining unallocated time (${remTarget}m left). Adjust time, schedule on another day, or reduce duration."
                    }
                    if (DayAccounting.crossesMidnight(proposed)) {
                        val nextDay = targetDay + DayAccounting.DAY_MILLIS
                        val nextDayLabel = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(nextDay))
                        val nextDaySegment = DayAccounting.minutesOnDay(proposed, nextDay, now)
                        val remNext = repo.getRemainingMinutesForDay(nextDay)
                        if (nextDaySegment > remNext) {
                            return "Cannot create overnight task \"$title\": overnight portion landing on $nextDayLabel (${nextDaySegment}m) exceeds remaining unallocated time on that day (${remNext}m left)."
                        }
                    }
                } else {
                    val rem = repo.getRemainingMinutesForDay(targetDay)
                    if (duration > rem) {
                        val dayName = if (isToday) "today" else "target date $targetDateLabel"
                        return "Cannot create task \"$title\" (${duration}m): duration exceeds remaining unallocated time on $dayName (${rem}m left). Choose another date or reduce duration."
                    }
                }

                // Parent/child constraint: cumulative direct sub-task durations <= parent duration.
                repo.childDurationError(parentId, duration)?.let { return it }

                val t = repo.create(
                    title = title,
                    parentId = parentId,
                    durationMinutes = duration,
                    fixedTime = fixedTime,
                    scheduledDate = scheduledDate,
                    recurrence = recurrence,
                    weekdaysMask = weekdaysMask,
                    info = info,
                    recurrenceEndDate = recurrenceEnd
                )
                val schedDesc = when {
                    fixedTime != null -> " @ " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(fixedTime))
                    scheduledDate != null -> " on " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(scheduledDate))
                    else -> " for today"
                }
                val parentDesc = if (t.parentId != null) {
                    val p = repo.get(t.parentId!!)
                    " (sub-task of \"${p?.title ?: "#${t.parentId}"}\")"
                } else ""
                "Created task #${t.id}: \"${t.title}\" (${t.durationMinutes} min)$schedDesc$parentDesc."
            }

            "update_task" -> {
                val id = args.longOrNull("task_id")
                    ?: args.str("task_title")?.let { repo.findByTitle(it)?.id }
                    ?: return "Missing task_id or task_title."
                val t = repo.get(id) ?: return "Task #$id not found."
                val newDuration = (args.longOrNull("duration_minutes") ?: t.durationMinutes).coerceAtLeast(1L)
                val newFixed = if (args.str("fixed_time") != null) parseTime(args.str("fixed_time")) else t.fixedTime
                val newScheduled = if (args.str("scheduled_date") != null) parseDate(args.str("scheduled_date")) else t.scheduledDate

                val now = System.currentTimeMillis()
                val todayStart = DayAccounting.dayStart(now)
                val targetDay = when {
                    newFixed != null -> DayAccounting.dayStart(newFixed)
                    newScheduled != null -> DayAccounting.dayStart(newScheduled)
                    else -> todayStart
                }
                val isToday = targetDay == todayStart
                val targetDateLabel = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(targetDay))

                val candidate = t.copy(
                    durationMinutes = newDuration,
                    fixedTime = newFixed,
                    scheduledDate = newScheduled
                )

                if (newFixed != null) {
                    val targetDaySegment = DayAccounting.minutesOnDay(candidate, targetDay, now)
                    val remTarget = repo.getRemainingMinutesForDay(targetDay, excludeTaskId = t.id)
                    if (targetDaySegment > remTarget) {
                        return "Cannot update task #$id to ${newDuration}m: portion landing on $targetDateLabel (${targetDaySegment}m) exceeds available unallocated time (${remTarget}m available)."
                    }
                    if (DayAccounting.crossesMidnight(candidate)) {
                        val nextDay = targetDay + DayAccounting.DAY_MILLIS
                        val nextDayLabel = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(nextDay))
                        val nextDaySegment = DayAccounting.minutesOnDay(candidate, nextDay, now)
                        val remNext = repo.getRemainingMinutesForDay(nextDay, excludeTaskId = t.id)
                        if (nextDaySegment > remNext) {
                            return "Cannot update task #$id: overnight spill portion landing on $nextDayLabel (${nextDaySegment}m) exceeds available time on that day (${remNext}m available)."
                        }
                    }
                } else {
                    val rem = repo.getRemainingMinutesForDay(targetDay, excludeTaskId = t.id)
                    if (newDuration > rem) {
                        val dayName = if (isToday) "today" else "target date $targetDateLabel"
                        return "Cannot update task #$id to ${newDuration}m: exceeds available unallocated time on $dayName (${rem}m available)."
                    }
                }

                // Parent/child constraints (both directions)
                repo.childDurationError(t.parentId, newDuration, excludeChildId = t.id)?.let { return it }
                repo.parentShrinkError(t.id, newDuration)?.let { return it }

                val newRecurrence = if (args.str("recurrence") != null) parseRecurrence(args.str("recurrence")) else t.recurrence
                val newRecurrenceEnd = when {
                    newRecurrence == Recurrence.NONE -> null
                    args.str("recurrence_end") != null -> parseEndDate(args.str("recurrence_end"))
                    else -> t.recurrenceEndDate
                }
                val updated = t.copy(
                    title = args.str("title") ?: t.title,
                    durationMinutes = newDuration,
                    fixedTime = newFixed,
                    scheduledDate = newScheduled,
                    recurrence = newRecurrence,
                    weekdaysMask = if (args.str("weekdays") != null) parseWeekdays(args.str("weekdays")) else t.weekdaysMask,
                    recurrenceEndDate = newRecurrenceEnd,
                    info = args.str("info") ?: t.info
                )
                repo.update(updated)
                "Updated task #$id: \"${updated.title}\"."
            }

            "delete_task" -> {
                val id = args.longOrNull("task_id")
                    ?: args.str("task_title")?.let { repo.findByTitle(it)?.id }
                    ?: return "Missing task_id or task_title."
                val t = repo.get(id) ?: return "Task #$id not found."
                repo.deleteTree(id)
                "Deleted \"${t.title}\" and its sub-tasks."
            }

            "complete_task" -> {
                val id = args.longOrNull("task_id")
                    ?: args.str("task_title")?.let { repo.findByTitle(it)?.id }
                    ?: return "Missing task_id or task_title."
                val t = repo.get(id) ?: return "Task #$id not found."
                val done = args["completed"]?.let { (it as JsonPrimitive).content.toBooleanStrictOrNull() } ?: true
                repo.setCompleted(id, done)
                "Marked \"${t.title}\" ${if (done) "complete" else "incomplete"}."
            }

            "reorder_task" -> {
                val id = args.longOrNull("task_id") ?: return "Missing task_id."
                repo.insertBetween(id, args.longOrNull("above_task_id"), args.longOrNull("below_task_id"))
                "Reordered task #$id."
            }

            "move_task" -> {
                val id = args.longOrNull("task_id")
                    ?: args.str("task_title")?.let { repo.findByTitle(it)?.id }
                    ?: return "Missing task_id or task_title."
                val newParent = args.longOrNull("new_parent_id")
                    ?: args.str("new_parent_title")?.let { repo.findByTitle(it)?.id }
                repo.move(id, newParent)
                val targetDesc = if (newParent != null) "under parent #${newParent} (\"${repo.get(newParent)?.title}\")" else "to top level"
                "Moved task #$id $targetDesc."
            }

            "start_timer" -> {
                val id = args.longOrNull("task_id")
                    ?: args.str("task_title")?.let { repo.findByTitle(it)?.id }
                    ?: return "Missing task_id or task_title."
                val t = repo.get(id) ?: return "Task #$id not found."
                val before = com.forgebuild.taskflow.data.TimerEngine.stateOf(t)
                repo.timerStart(id)
                if (before == com.forgebuild.taskflow.data.TimerEngine.TimerState.PAUSED)
                    "Resumed the timer on \"${t.title}\"." else "Started the timer on \"${t.title}\" (${t.durationMinutes}m countdown)."
            }

            "pause_timer" -> {
                val id = args.longOrNull("task_id")
                    ?: args.str("task_title")?.let { repo.findByTitle(it)?.id }
                    ?: return "Missing task_id or task_title."
                val t = repo.get(id) ?: return "Task #$id not found."
                if (com.forgebuild.taskflow.data.TimerEngine.stateOf(t) !=
                    com.forgebuild.taskflow.data.TimerEngine.TimerState.RUNNING)
                    return "No running timer on \"${t.title}\" to pause."
                repo.timerPause(id)
                val left = com.forgebuild.taskflow.data.TimerEngine.remainingMs(repo.get(id)!!) / 60000L
                "Paused the timer on \"${t.title}\" with about ${left}m left."
            }

            "extend_timer" -> {
                val id = args.longOrNull("task_id")
                    ?: args.str("task_title")?.let { repo.findByTitle(it)?.id }
                    ?: return "Missing task_id or task_title."
                val t = repo.get(id) ?: return "Task #$id not found."
                val amount = (args.longOrNull("amount") ?: return "Missing amount.").coerceAtLeast(1L)
                val minutes = if (args.str("unit")?.lowercase()?.startsWith("hour") == true) amount * 60 else amount
                val was = com.forgebuild.taskflow.data.TimerEngine.stateOf(t)
                repo.timerExtend(id, minutes)
                "Extended \"${t.title}\" by ${minutes}m" + when (was) {
                    com.forgebuild.taskflow.data.TimerEngine.TimerState.RUNNING -> " (mid-countdown)."
                    com.forgebuild.taskflow.data.TimerEngine.TimerState.FINISHED -> " — a fresh ${minutes}m timer is now running."
                    com.forgebuild.taskflow.data.TimerEngine.TimerState.PAUSED -> " (timer stays paused)."
                    else -> " (timer now running)."
                }
            }

            else -> "Unknown action: $name"
        }
    }
}
