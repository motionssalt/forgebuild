package com.forgebuild.taskflow.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global uncaught crash handler and crash diagnostics recorder.
 * Captures stack traces, device specs, OS release, and timestamps
 * to a local rolling file so intermittent crashes can be diagnosed.
 */
object CrashLogger {
    private const val TAG = "TaskFlowCrash"
    private const val FILE_NAME = "crash_logs.txt"
    private const val MAX_LOG_BYTES = 64 * 1024 // 64 KB rolling window

    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private var isInstalled = false

    fun install(context: Context) {
        if (isInstalled) return
        isInstalled = true
        val appContext = context.applicationContext
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(appContext, thread.name, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record crash to file", e)
            } finally {
                originalHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun recordCrash(context: Context, threadName: String, throwable: Throwable) {
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val report = buildString {
            append("========================================\n")
            append("TIMESTAMP: ").append(timeStr).append("\n")
            append("THREAD: ").append(threadName).append("\n")
            append("DEVICE: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            append("ANDROID: API ").append(Build.VERSION.SDK_INT).append(" (").append(Build.VERSION.RELEASE).append(")\n")
            append("EXCEPTION: ").append(throwable.javaClass.name).append("\n")
            append("MESSAGE: ").append(throwable.message ?: "(none)").append("\n")
            append("STACKTRACE:\n").append(stackTrace).append("\n")
        }

        Log.e(TAG, report)

        runCatching {
            val file = logFile(context)
            val existing = if (file.exists()) file.readText() else ""
            val combined = (report + "\n" + existing).take(MAX_LOG_BYTES)
            file.writeText(combined)
        }
    }

    @Synchronized
    fun logHandledException(context: Context, tag: String, message: String, throwable: Throwable?) {
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sw = StringWriter()
        throwable?.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val entry = buildString {
            append("[HANDLED WARNING/ERROR] ").append(timeStr).append(" [").append(tag).append("]: ").append(message).append("\n")
            if (stackTrace.isNotBlank()) append(stackTrace).append("\n")
        }
        Log.w(tag, entry)

        runCatching {
            val file = logFile(context)
            val existing = if (file.exists()) file.readText() else ""
            val combined = (entry + "\n" + existing).take(MAX_LOG_BYTES)
            file.writeText(combined)
        }
    }

    @Synchronized
    fun getCrashLogs(context: Context): String {
        return runCatching {
            val file = logFile(context)
            if (file.exists()) file.readText() else "No crashes recorded."
        }.getOrDefault("Unable to read crash logs.")
    }

    @Synchronized
    fun clearCrashLogs(context: Context) {
        runCatching {
            val file = logFile(context)
            if (file.exists()) file.delete()
        }
    }
}
