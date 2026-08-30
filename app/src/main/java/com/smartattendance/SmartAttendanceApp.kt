package com.smartattendance

import android.app.Application
import android.content.Intent
import android.util.Log

@HiltAndroidApp
class SmartAttendanceApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                startActivity(
                    Intent(this, CrashReportActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(CrashReportActivity.EXTRA_TRACE, Log.getStackTraceString(throwable)),
                )
                Thread.sleep(1500)
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
