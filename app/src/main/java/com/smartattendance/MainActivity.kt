package com.smartattendance

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import com.smartattendance.core.theme.SmartAttendanceTheme
import com.smartattendance.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity واحد پروژه.
 *
 * نکته مهم: FragmentActivity انتخاب شده چون BiometricPrompt
 * به FragmentActivity نیاز دارد؛ Compose کاملاً با آن سازگار است.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // FLAG_SECURE: هم جلوی اسکرین‌شات/اسکرین‌رکورد را می‌گیرد (سیستم خودش خطا می‌دهد)
        // و هم باعث می‌شود توی لیست برنامه‌های اخیر (Recents) به‌جای پیش‌نمایش صفحه،
        // یک صفحه‌ی خالی نشان داده شود — تا محتوای حساس (QR، بیومتریک، گزارش حضور) لو نرود.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SmartAttendanceTheme {
                // RTL سراسری برای فارسی
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppNavHost()
                }
            }
        }
    }
}
