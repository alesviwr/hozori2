package com.smartattendance

import android.app.Application
import android.content.Intent
import android.util.Log
import com.smartattendance.data.remote.api.AttendanceApi
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SmartAttendanceApp : Application() {

    @Inject
    lateinit var api: AttendanceApi

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        // ───────── گرم‌کردن اتصال شبکه ─────────
        // اولین درخواست هر اپ به یک سرور، هزینه‌ی DNS + TLS handshake + (احتمالاً)
        // Cold Start توابع Edge سوپابیس را با هم می‌پردازد — همین باعث می‌شود اولین
        // اسکن QR یا اولین بارگذاری صفحه محسوس کند شود. با زدن یک درخواست سبک به
        // مسیر ریشه («/») همان لحظه که اپ باز می‌شود (همزمان با نمایش اسپلش)،
        // این هزینه از قبل پرداخت می‌شود و OkHttp همان کانکشن را برای درخواست‌های
        // بعدی دوباره استفاده می‌کند (Connection Pool). خطای این درخواست مهم نیست؛
        // صرفاً یک گرم‌کردن بی‌اثر روی UI است.
        appScope.launch {
            runCatching { api.ping() }
        }
    }
}
