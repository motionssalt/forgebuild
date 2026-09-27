package com.forgebuild.taskflow

import android.app.Application
import android.os.Build
import com.forgebuild.taskflow.notify.NotificationHub
import com.forgebuild.taskflow.reminder.DueForegroundService
import com.forgebuild.taskflow.util.CrashLogger

class TaskFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 1. Crash handler and logger installed immediately on process boot
        CrashLogger.install(this)

        // 2. Notification channels configured
        NotificationHub.ensureChannels(this)

        // 3. Persistent foreground service started safely (never crash on background boot)
        try {
            DueForegroundService.start(this)
        } catch (e: Throwable) {
            CrashLogger.logHandledException(this, "TaskFlowApp", "Failed to start DueForegroundService at app launch", e)
        }
    }
}
