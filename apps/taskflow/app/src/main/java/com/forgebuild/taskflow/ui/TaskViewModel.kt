package com.forgebuild.taskflow.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgebuild.taskflow.data.DayAccounting
import com.forgebuild.taskflow.data.PriorityRank
import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.Task
import com.forgebuild.taskflow.data.TaskRepository
import com.forgebuild.taskflow.reminder.DueForegroundService
import com.forgebuild.taskflow.reminder.ReminderScheduler
import com.forgebuild.taskflow.settings.AppSettings
import com.forgebuild.taskflow.settings.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimeRangeView { TODAY, WEEK, MONTH, YEAR }

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModel(app: Application) : AndroidViewModel(app) {
    val repo = TaskRepository.get(app)
    val settings = AppSettings.get(app)
    private val scheduler = ReminderScheduler(app)

    private val _timeRange = MutableStateFlow(TimeRangeView.TODAY)
    val timeRange: StateFlow<TimeRangeView> = _timeRange.asStateFlow()

    /** Navigation stack of parent ids; empty = top level. */
    private val _path = MutableStateFlow<List<Long>>(emptyList())
    val path: StateFlow<List<Long>> = _path.asStateFlow()

    val currentParentId: StateFlow<Long?> = _path.map { it.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** All active children at current level. */
    private val rawActiveTasks: StateFlow<List<Task>> = currentParentId
        .flatMapLatest { repo.activeChildren(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Filtered tasks according to the selected TimeRangeView, using shared [DayAccounting] day-occupancy rules. */
    val tasks: StateFlow<List<Task>> = combine(rawActiveTasks, _timeRange) { list, range ->
        val now = System.currentTimeMillis()

        /** Day-start millis for every calendar day covered by the selected range. */
        fun daysInRange(): List<Long> {
            val todayStart = DayAccounting.dayStart(now)
            return when (range) {
                TimeRangeView.TODAY -> listOf(todayStart)
                TimeRangeView.WEEK -> {
                    val weekCal = Calendar.getInstance().apply { timeInMillis = now }
                    weekCal.set(Calendar.DAY_OF_WEEK, weekCal.firstDayOfWeek)
                    weekCal.set(Calendar.HOUR_OF_DAY, 0); weekCal.set(Calendar.MINUTE, 0)
                    weekCal.set(Calendar.SECOND, 0); weekCal.set(Calendar.MILLISECOND, 0)
                    (0 until 7).map { weekCal.timeInMillis + it * DayAccounting.DAY_MILLIS }
                }
                TimeRangeView.MONTH -> {
                    val m = Calendar.getInstance().apply { timeInMillis = now }
                    val dim = m.getActualMaximum(Calendar.DAY_OF_MONTH)
                    m.set(Calendar.DAY_OF_MONTH, 1); m.set(Calendar.HOUR_OF_DAY, 0); m.set(Calendar.MINUTE, 0)
                    m.set(Calendar.SECOND, 0); m.set(Calendar.MILLISECOND, 0)
                    (0 until dim).map { m.timeInMillis + it * DayAccounting.DAY_MILLIS }
                }
                TimeRangeView.YEAR -> {
                    val y = Calendar.getInstance().apply { timeInMillis = now }
                    val diy = y.getActualMaximum(Calendar.DAY_OF_YEAR)
                    y.set(Calendar.DAY_OF_YEAR, 1); y.set(Calendar.HOUR_OF_DAY, 0); y.set(Calendar.MINUTE, 0)
                    y.set(Calendar.SECOND, 0); y.set(Calendar.MILLISECOND, 0)
                    (0 until diy).map { y.timeInMillis + it * DayAccounting.DAY_MILLIS }
                }
            }
        }
        val days = daysInRange()

        list.filter { t ->
            if (t.dueNow) return@filter true
            when {
                // Untimed tasks belong to today (and therefore to every range that includes today).
                t.fixedTime == null && !t.isRecurringTemplate ->
                    days.any { DayAccounting.touchesDay(t, it, now) }
                // Recurring template: show if it occurs on any day in range.
                t.isRecurringTemplate ->
                    days.any { DayAccounting.occursOnDay(t, it) != null }
                // Timed one-off / generated instance: show on any day its occupancy
                // touches (incl. overnight spill into the next day and carry-over).
                else -> days.any { DayAccounting.touchesDay(t, it, now) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Completed tasks list. */
    val completedTasks: StateFlow<List<Task>> = repo.completedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Unfinished (missed fixed-time) tasks list. */
    val unfinishedTasks: StateFlow<List<Task>> = repo.unfinishedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Breadcrumb titles along the current path. */
    val breadcrumbs: StateFlow<List<Pair<Long, String>>> = _path.map { ids ->
        ids.mapNotNull { id -> repo.get(id)?.let { id to it.title } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val onboardingDone: StateFlow<Boolean> = settings.onboardingDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val retentionDays: StateFlow<Int> = settings.retentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 14)

    /** Time tracking for the day — shared [DayAccounting] rules so every task category counts. */
    val allocatedMinutesToday: StateFlow<Long> = repo.allActive().map { all ->
        val now = System.currentTimeMillis()
        DayAccounting.allocatedMinutesForDay(all, now, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** Pass 7: minutes of the 24h day already elapsed. */
    val elapsedMinutesToday: StateFlow<Long> = repo.allActive().map {
        val now = System.currentTimeMillis()
        ((now - DayAccounting.dayStart(now)) / 60000L).coerceIn(0L, 1440L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** Pass 7 (horizontal bar): day "used" = elapsed time + time still booked by tasks, vs 24h. */
    val usedDayMinutes: StateFlow<Long> = combine(allocatedMinutesToday, elapsedMinutesToday) { alloc, elapsed ->
        (elapsed + alloc).coerceIn(0L, 1440L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val pomodoroEnabled: StateFlow<Boolean> = settings.pomodoroEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pomodoroWorkMinutes: StateFlow<Int> = settings.pomodoroWorkMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 25)
    val pomodoroBreakMinutes: StateFlow<Int> = settings.pomodoroBreakMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val remainingMinutesToday: StateFlow<Long> = allocatedMinutesToday.map { allocated ->
        val now = System.currentTimeMillis()
        val minutesUntilMidnight = ((DayAccounting.nextDayStart(now) - now) / 60000L).coerceAtLeast(0L)
        (minutesUntilMidnight - allocated).coerceAtLeast(0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** User feedback message channel (e.g. allocation error snackbar). */
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        repo.onTimedTasksChanged = { scheduler.rescheduleAll() }
        repo.onRecurringInstance = { t ->
            com.forgebuild.taskflow.notify.NotificationHub.recurringInstance(getApplication(), t.title)
        }
        repo.onOverdue = { t ->
            com.forgebuild.taskflow.notify.NotificationHub.overdue(getApplication(), t.id, t.title)
        }
        viewModelScope.launch {
            scheduler.rescheduleAll()
            // Day-rollover rules on every open: expired fixed-time tasks -> Unfinished view.
            repo.sweepMissed()
            // Degrade any stale loud due-now pins whose full span already elapsed.
            repo.transitionDueNow()
            // Auto-purge expired completed AND missed (Unfinished) tasks per retention setting
            val days = settings.retentionDays.first()
            repo.purgeExpiredCompleted(days)
            repo.purgeExpiredMissed(days)
        }
    }

    fun setTimeRange(range: TimeRangeView) { _timeRange.value = range }

    fun openTask(taskId: Long) { _path.value = _path.value + taskId }
    fun navigateToBreadcrumb(index: Int) {
        if (index < 0) _path.value = emptyList()
        else _path.value = _path.value.take(index + 1)
    }
    fun navigateUp(): Boolean {
        if (_path.value.isEmpty()) return false
        _path.value = _path.value.dropLast(1)
        return true
    }

    fun addTask(
        title: String,
        durationMinutes: Long = 30L,
        fixedTime: Long? = null,
        scheduledDate: Long? = null,
        recurrence: Recurrence = Recurrence.NONE,
        weekdaysMask: Int = 0,
        info: String = "",
        recurrenceEndDate: Long? = null,
        parentId: Long? = null
    ) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        // Pass 7 FIX: an explicit parentId (e.g. the edit screen adding a sub-task)
        // overrides the navigation level, so the new task nests under the intended
        // parent instead of becoming a root task.
        val effectiveParent = parentId ?: _path.value.lastOrNull()
        val dur = durationMinutes.coerceAtLeast(1L)
        val now = System.currentTimeMillis()
        val proposed = Task(
            title = "",
            rank = 0.0,
            durationMinutes = dur,
            fixedTime = fixedTime,
            scheduledDate = scheduledDate
        )
        val todayStart = DayAccounting.dayStart(now)
        val isFutureDay = scheduledDate != null && scheduledDate > todayStart
        val todaySegment = if (isFutureDay) 0L else if (fixedTime == null) dur else DayAccounting.minutesOnDay(proposed, now, now)
        val isToday = !isFutureDay && (fixedTime == null || todaySegment > 0L)
        if (isToday) {
            val remaining = repo.getRemainingMinutesToday()
            if (todaySegment > remaining) {
                _userMessage.emit("Cannot add \"$title\": today's portion (${todaySegment}m) exceeds remaining unallocated time today (${remaining}m left).")
                return@launch
            }
        }
        // Overnight rule: a task may only span past midnight when it has a fixed time
        // (with duration). An untimed task is confined to a single day by construction
        // (its whole duration is charged to today and blocked above when it overflows).
        if (!isFutureDay && fixedTime == null && dur > repo.getRemainingMinutesToday()) {
            _userMessage.emit("Cannot add \"$title\": tasks without a fixed time cannot run overnight — ${dur}m does not fit in today.")
            return@launch
        }
        // Parent/child constraint: cumulative direct sub-task durations ≤ parent duration.
        repo.childDurationError(effectiveParent, dur)?.let {
            _userMessage.emit(it)
            return@launch
        }
        repo.create(
            title = title.trim(),
            parentId = effectiveParent,
            durationMinutes = dur,
            fixedTime = fixedTime,
            scheduledDate = scheduledDate,
            recurrence = recurrence,
            weekdaysMask = weekdaysMask,
            info = info.trim(),
            recurrenceEndDate = if (recurrence != Recurrence.NONE) recurrenceEndDate else null
        )
    }
    fun toggleComplete(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, !task.completed) }

    // ---- Countdown timer controls (play / pause / extend / complete) ----
    fun timerToggle(task: com.forgebuild.taskflow.data.Task) = viewModelScope.launch {
        when (com.forgebuild.taskflow.data.TimerEngine.stateOf(task)) {
            com.forgebuild.taskflow.data.TimerEngine.TimerState.RUNNING -> repo.timerPause(task.id)
            else -> {
                repo.timerStart(task.id)
                repo.get(task.id)?.timerEndsAt?.let { scheduler.scheduleTimerEnd(task.id, it) }
            }
        }
    }
    fun timerExtend(task: com.forgebuild.taskflow.data.Task, minutes: Long) = viewModelScope.launch {
        repo.timerExtend(task.id, minutes)
        repo.get(task.id)?.timerEndsAt?.let { scheduler.scheduleTimerEnd(task.id, it) }
    }
    fun setPomodoroEnabled(enabled: Boolean) = viewModelScope.launch { settings.setPomodoroEnabled(enabled) }
    fun setPomodoroWorkMinutes(m: Int) = viewModelScope.launch { settings.setPomodoroWorkMinutes(m) }
    fun setPomodoroBreakMinutes(m: Int) = viewModelScope.launch { settings.setPomodoroBreakMinutes(m) }
    fun timerComplete(task: com.forgebuild.taskflow.data.Task) = viewModelScope.launch {
        repo.setCompleted(task.id, true)
    }
    fun restoreMissed(task: Task) = viewModelScope.launch { repo.restoreMissed(task.id) }
    fun clearMissed() = viewModelScope.launch { repo.clearMissed() }
    fun restoreTask(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, false) }
    fun delete(task: Task) = viewModelScope.launch { repo.deleteTree(task.id) }
    fun clearCompleted() = viewModelScope.launch { repo.clearCompleted() }

    fun saveEdit(task: Task, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val dur = task.durationMinutes.coerceAtLeast(1L)
        // Overnight-aware: only the portion of the task landing on today counts against today.
        val todayStart = DayAccounting.dayStart(now)
        val isFutureDay = task.scheduledDate != null && task.scheduledDate > todayStart
        val todaySegment = if (isFutureDay) 0L else if (task.fixedTime == null) dur else DayAccounting.minutesOnDay(task, now, now)
        val isToday = !isFutureDay && (task.fixedTime == null || todaySegment > 0L)

        if (isToday) {
            val remainingWithOld = repo.getRemainingMinutesToday(excludeTaskId = task.id)
            if (todaySegment > remainingWithOld) {
                onResult(false, "Duration (${dur}m) puts ${todaySegment}m on today, exceeding remaining time left today (${remainingWithOld}m available).")
                return@launch
            }
        }

        // Overnight rule: without a fixed time a task must never span into the next day.
        if (!isFutureDay && task.fixedTime == null && dur > repo.getRemainingMinutesToday(excludeTaskId = task.id)) {
            onResult(false, "Tasks without a fixed time cannot run overnight — ${dur}m does not fit in today.")
            return@launch
        }
        repo.childDurationError(task.parentId, dur, excludeChildId = task.id)?.let {
            onResult(false, it)
            return@launch
        }
        repo.parentShrinkError(task.id, dur)?.let {
            onResult(false, it)
            return@launch
        }

        repo.update(task)
        onResult(true, null)
    }

    fun moveRelative(task: Task, delta: Int) = viewModelScope.launch { repo.moveRelative(task.id, delta) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setRetentionDays(days: Int) = viewModelScope.launch {
        settings.setRetentionDays(days)
        repo.purgeExpiredCompleted(days)
        repo.purgeExpiredMissed(days)
    }
    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingDone() }
    fun setForegroundService(active: Boolean) = viewModelScope.launch {
        settings.setForegroundActive(active)
        if (active) DueForegroundService.start(getApplication()) else DueForegroundService.stop(getApplication())
    }
}
