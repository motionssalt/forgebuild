package com.forgebuild.forgehouse50.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forgebuild.forgehouse50.data.ReadingSession
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.SessionStore
import java.util.concurrent.TimeUnit

/**
 * catchup_widget_links_v1 / ISSUE 3 (refresh) — periodic + opportunistic
 * widget state refresh. Uses the SAME [ReadingSession] engine as the app so
 * the home-screen widget and the app never disagree about which day(s) are
 * due. Fails soft: on any network error the widget keeps its last good state
 * and hydrates from LocalCacheStore.
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext, SessionStore(applicationContext))
        refreshWidgetState(applicationContext, repo)
        ForgeHouseWidgetProvider.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "fh50_widget_refresh"

        /** 30-minute periodic refresh so day/progress data never goes stale. */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        /** One-shot refresh (widget placed, app foreground, day completed). */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Recompute widget state from the live API + the shared catch-up engine. */
        suspend fun refreshWidgetState(context: Context, repo: Repository) {
            val loggedIn = repo.session.isLoggedIn
            if (!loggedIn) {
                WidgetStateStore(context).write(WidgetStateStore.State(loggedIn = false))
                return
            }

            var updated = false
            runCatching {
                val progress = repo.api.progress()
                val today = repo.api.today()
                val me = repo.api.me()
                repo.cache.saveProgress(progress)
                repo.cache.saveToday(today)
                repo.cache.saveMe(me)
                val plan = ReadingSession.resolve(
                    progress = progress,
                    today = today,
                    meEndDate = me.programme.programme_end_date,
                )
                val d = plan.days.firstOrNull() ?: today.today?.day_number ?: 0
                var assignment = ""
                var verse = ""
                var started = false
                var completed = false
                if (d > 0) {
                    val dayResp = runCatching { repo.api.day(d) }.getOrNull()
                    if (dayResp != null) repo.cache.saveDay(d, dayResp)
                    completed = dayResp?.progress?.completed ?: (today.today?.completed ?: false)
                    val rSecs = dayResp?.progress?.reading_seconds ?: (today.today?.reading_seconds ?: 0)
                    started = rSecs > 0 && !completed
                    assignment = dayResp?.assignments?.joinToString(", ") {
                        if (it.chapter_start == it.chapter_end) "${it.book} ${it.chapter_start}"
                        else "${it.book} ${it.chapter_start}–${it.chapter_end}"
                    } ?: (today.today?.assignment?.summary ?: "")
                    dayResp?.assignments?.firstOrNull()?.let { a ->
                        verse = runCatching {
                            repo.getPassage(a.book, a.chapter_start, a.chapter_start,
                                repo.session.translationId ?: Repository.KJV_ID)
                        }.getOrNull()?.verses?.firstOrNull()?.text.orEmpty()
                    }
                }
                val existing = WidgetStateStore(context).read()
                val state = WidgetStateStore.State(
                    day = d,
                    totalDays = today.programme.total_days,
                    assignment = assignment.ifBlank { existing.assignment },
                    verse = if (verse.isNotBlank()) verse.take(220) else existing.verse,
                    chaptersDone = progress.totals.chapters_completed,
                    chaptersTotal = today.programme.total_chapters,
                    started = started,
                    completed = completed,
                    catchup = plan.catchup,
                    loggedIn = true,
                )
                WidgetStateStore(context).write(state)
                updated = true
            }

            // Failsafe: if network failed or offline, fall back to synchronous local cache
            if (!updated) {
                repo.cache.syncWidget()
            }
        }
    }
}
