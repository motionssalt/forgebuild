package com.forgebuild.forgehouse50.data.local

import android.content.Context
import android.content.SharedPreferences
import com.forgebuild.forgehouse50.data.AdminProgramme
import com.forgebuild.forgehouse50.data.AdminStats
import com.forgebuild.forgehouse50.data.DayResponse
import com.forgebuild.forgehouse50.data.LeaderboardResponse
import com.forgebuild.forgehouse50.data.MeResponse
import com.forgebuild.forgehouse50.data.NotesResponse
import com.forgebuild.forgehouse50.data.Participant
import com.forgebuild.forgehouse50.data.ParticipantsResponse
import com.forgebuild.forgehouse50.data.ProgressResponse
import com.forgebuild.forgehouse50.data.QuizResponse
import com.forgebuild.forgehouse50.data.ReadingSession
import com.forgebuild.forgehouse50.data.TodayResponse
import com.forgebuild.forgehouse50.ui.AppJson
import com.forgebuild.forgehouse50.widget.ForgeHouseWidgetProvider
import com.forgebuild.forgehouse50.widget.WidgetStateStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * LocalCacheStore: Centralized, synchronous persistent cache for user data.
 *
 * Implements the Engine cache-first pattern (instant render from local cache,
 * background refresh + reconcile, never a blank/zero/stale flash).
 *
 * All get*() methods execute synchronously in-memory / from SharedPreferences,
 * allowing Compose screens to initialize mutableStateOf(cache.get*()) BEFORE
 * the first frame is drawn.
 */
class LocalCacheStore(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fh50_cache", Context.MODE_PRIVATE)

    // ── Today ─────────────────────────────────────────────────────────────
    fun getToday(): TodayResponse? =
        prefs.getString(KEY_TODAY, null)?.let {
            runCatching { AppJson.decodeFromString<TodayResponse>(it) }.getOrNull()
        }

    fun saveToday(data: TodayResponse) {
        prefs.edit().putString(KEY_TODAY, AppJson.encodeToString(TodayResponse.serializer(), data)).apply()
        syncWidget()
    }

    // ── Me Profile ────────────────────────────────────────────────────────
    fun getMe(): MeResponse? =
        prefs.getString(KEY_ME, null)?.let {
            runCatching { AppJson.decodeFromString<MeResponse>(it) }.getOrNull()
        }

    fun saveMe(data: MeResponse) {
        prefs.edit().putString(KEY_ME, AppJson.encodeToString(MeResponse.serializer(), data)).apply()
        syncWidget()
    }

    // ── Progress ──────────────────────────────────────────────────────────
    fun getProgress(): ProgressResponse? =
        prefs.getString(KEY_PROGRESS, null)?.let {
            runCatching { AppJson.decodeFromString<ProgressResponse>(it) }.getOrNull()
        }

    fun saveProgress(data: ProgressResponse) {
        prefs.edit().putString(KEY_PROGRESS, AppJson.encodeToString(ProgressResponse.serializer(), data)).apply()
        syncWidget()
    }

    // ── Day Details ───────────────────────────────────────────────────────
    fun getDay(dayNumber: Int): DayResponse? =
        prefs.getString(keyDay(dayNumber), null)?.let {
            runCatching { AppJson.decodeFromString<DayResponse>(it) }.getOrNull()
        }

    fun saveDay(dayNumber: Int, data: DayResponse) {
        prefs.edit().putString(keyDay(dayNumber), AppJson.encodeToString(DayResponse.serializer(), data)).apply()
        syncWidget()
    }

    // ── Notes ─────────────────────────────────────────────────────────────
    fun getNotes(): NotesResponse? =
        prefs.getString(KEY_NOTES, null)?.let {
            runCatching { AppJson.decodeFromString<NotesResponse>(it) }.getOrNull()
        }

    fun saveNotes(data: NotesResponse) {
        prefs.edit().putString(KEY_NOTES, AppJson.encodeToString(NotesResponse.serializer(), data)).apply()
    }

    // ── Leaderboard ───────────────────────────────────────────────────────
    fun getLeaderboard(category: String): LeaderboardResponse? =
        prefs.getString("lb2_$category", null)?.let {
            runCatching { AppJson.decodeFromString<LeaderboardResponse>(it) }.getOrNull()
        }

    fun saveLeaderboard(category: String, data: LeaderboardResponse) {
        prefs.edit().putString("lb2_$category", AppJson.encodeToString(LeaderboardResponse.serializer(), data)).apply()
    }

    // ── Quiz ──────────────────────────────────────────────────────────────
    fun getQuiz(dayNumber: Int): QuizResponse? =
        prefs.getString(keyQuiz(dayNumber), null)?.let {
            runCatching { AppJson.decodeFromString<QuizResponse>(it) }.getOrNull()
        }

    fun saveQuiz(dayNumber: Int, data: QuizResponse) {
        prefs.edit().putString(keyQuiz(dayNumber), AppJson.encodeToString(QuizResponse.serializer(), data)).apply()
    }

    // ── Admin ─────────────────────────────────────────────────────────────
    fun getAdminStats(): AdminStats? =
        prefs.getString(KEY_ADMIN_STATS, null)?.let {
            runCatching { AppJson.decodeFromString<AdminStats>(it) }.getOrNull()
        }

    fun saveAdminStats(data: AdminStats) {
        prefs.edit().putString(KEY_ADMIN_STATS, AppJson.encodeToString(AdminStats.serializer(), data)).apply()
    }

    fun getAdminProgramme(): AdminProgramme? =
        prefs.getString(KEY_ADMIN_PROGRAMME, null)?.let {
            runCatching { AppJson.decodeFromString<AdminProgramme>(it) }.getOrNull()
        }

    fun saveAdminProgramme(data: AdminProgramme) {
        prefs.edit().putString(KEY_ADMIN_PROGRAMME, AppJson.encodeToString(AdminProgramme.serializer(), data)).apply()
    }

    fun getAdminParticipants(): List<Participant> =
        prefs.getString(KEY_ADMIN_PARTICIPANTS, null)?.let {
            runCatching { AppJson.decodeFromString<ParticipantsResponse>(it).participants }.getOrNull()
        } ?: emptyList()

    fun saveAdminParticipants(data: List<Participant>) {
        val wrapped = ParticipantsResponse(data)
        prefs.edit().putString(KEY_ADMIN_PARTICIPANTS, AppJson.encodeToString(ParticipantsResponse.serializer(), wrapped)).apply()
    }

    // ── Reading Plan Resolution (Cache-First) ─────────────────────────────
    /**
     * Resolves the ReadingSession plan synchronously from cached data.
     * Guaranteed non-blocking and instant on screen composition.
     */
    fun resolvePlan(): ReadingSession.SessionPlan? {
        val t = getToday() ?: return null
        val pr = getProgress()
        val me = getMe()
        return ReadingSession.resolve(pr, t, me?.programme?.programme_end_date)
    }

    // ── Widget Synchronization ───────────────────────────────────────────
    /**
     * Syncs cached state directly into the widget store and notifies the launcher.
     * Ensures the home screen widget is updated immediately whenever the app updates cache,
     * without waiting for background WorkManager jobs.
     */
    fun syncWidget() {
        val t = getToday() ?: return
        val pr = getProgress()
        val me = getMe()

        val plan = ReadingSession.resolve(pr, t, me?.programme?.programme_end_date)
        val d = plan.days.firstOrNull() ?: t.today?.day_number ?: 0
        var assignment = ""
        var completed = false
        var started = false

        if (d > 0) {
            val cachedDay = getDay(d)
            completed = cachedDay?.progress?.completed ?: (t.today?.completed ?: false)
            val readingSecs = cachedDay?.progress?.reading_seconds ?: (t.today?.reading_seconds ?: 0)
            started = readingSecs > 0 && !completed
            assignment = cachedDay?.assignments?.joinToString(", ") {
                if (it.chapter_start == it.chapter_end) "${it.book} ${it.chapter_start}"
                else "${it.book} ${it.chapter_start}–${it.chapter_end}"
            } ?: (t.today?.assignment?.summary ?: "")
        }

        val existing = WidgetStateStore(context).read()
        val newState = WidgetStateStore.State(
            day = d,
            totalDays = t.programme.total_days,
            assignment = assignment.ifBlank { existing.assignment },
            verse = existing.verse, // keep last good verse
            chaptersDone = pr?.totals?.chapters_completed ?: existing.chaptersDone,
            chaptersTotal = t.programme.total_chapters,
            started = started,
            completed = completed,
            catchup = plan.catchup,
            loggedIn = true,
        )
        WidgetStateStore(context).write(newState)
        ForgeHouseWidgetProvider.updateAll(context)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TODAY = "today"
        const val KEY_ME = "me_profile"
        const val KEY_PROGRESS = "progress"
        const val KEY_NOTES = "notes"
        const val KEY_ADMIN_STATS = "admin_stats"
        const val KEY_ADMIN_PROGRAMME = "admin_programme"
        const val KEY_ADMIN_PARTICIPANTS = "admin_participants"
        fun keyDay(day: Int) = "day_$day"
        fun keyQuiz(day: Int) = "quiz_$day"
    }
}
