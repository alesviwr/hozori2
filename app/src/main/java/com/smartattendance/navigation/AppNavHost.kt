package com.smartattendance.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartattendance.presentation.auth.LoginScreen
import com.smartattendance.presentation.professor.createsession.CreateSessionScreen
import com.smartattendance.presentation.professor.dashboard.ProfessorDashboardScreen
import com.smartattendance.presentation.professor.livesession.LiveSessionScreen
import com.smartattendance.presentation.professor.reports.ReportDetailScreen
import com.smartattendance.presentation.professor.reports.ReportsScreen
import com.smartattendance.presentation.student.audio.AudioVerificationScreen
import com.smartattendance.presentation.student.biometric.BiometricStepScreen
import com.smartattendance.presentation.student.history.HistoryScreen
import com.smartattendance.presentation.student.home.StudentHomeScreen
import com.smartattendance.presentation.student.result.AttendanceResultScreen
import com.smartattendance.presentation.student.scanner.ScannerScreen
import com.smartattendance.presentation.splash.SplashScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject

/** مسیرهای Navigation — جداسازی کامل گراف استاد و دانشجو بر اساس Role سرور */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"

    // Professor
    const val PROF_DASHBOARD = "prof_dashboard"
    const val PROF_CREATE = "prof_create"
    const val PROF_LIVE = "prof_live/{sessionId}"
    const val PROF_REPORTS = "prof_reports"
    const val PROF_REPORT_DETAIL = "prof_report/{sessionId}"

    // Student
    const val STUDENT_HOME = "student_home"
    const val SCANNER = "student_scanner"
    const val BIOMETRIC = "student_biometric/{sessionId}/{courseName}/{bioOk}"
    const val AUDIO = "student_audio/{sessionId}/{courseName}/{bioOk}"
    const val RESULT = "student_result/{sessionId}"
    const val HISTORY = "student_history"

    fun live(sessionId: String) = "prof_live/$sessionId"
    fun reportDetail(sessionId: String) = "prof_report/$sessionId"
    fun biometric(sessionId: String, courseName: String, bioOk: Boolean = true) =
        "student_biometric/$sessionId/${URLEncoder.encode(courseName, "UTF-8")}/$bioOk"

    fun audio(sessionId: String, courseName: String, bioOk: Boolean = true) =
        "student_audio/$sessionId/${URLEncoder.encode(courseName, "UTF-8")}/$bioOk"

    fun result(sessionId: String) = "student_result/$sessionId"

    fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")
}

@HiltViewModel
class NavViewModel @Inject constructor(
    observeLogout: com.smartattendance.domain.usecase.ObserveLogoutUseCase,
) : androidx.lifecycle.ViewModel() {
    val logoutEvents = observeLogout()
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val navVm: NavViewModel = androidx.hilt.navigation.compose.hiltViewModel()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        navVm.logoutEvents.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // ───────── ریست امنیتیِ جریان حضور و غیاب ─────────
    // اگر کاربر در حین مراحل بیومتریک یا شنیدن Challenge (یعنی بعد از اسکن QR) برنامه را
    // پس‌زمینه ببرد — دکمه Home، سوییچ بین برنامه‌ها، خاموش‌شدن صفحه، رفتن به حالت
    // چندپنجره‌ای/شناور و غیره — با بازگشت باید از نو از اسکن QR شروع کند. این از دست‌به‌دست
    // شدن گوشی وسط فرآیند یا استفاده از چندوظیفه‌ای برای دور زدن مراحل جلوگیری می‌کند.
    // از ProcessLifecycleOwner استفاده می‌شود چون رویدادش برای «کل برنامه» است، نه برای
    // دیالوگ‌های موقتی مثل BiometricPrompt که فقط باعث ON_PAUSE کوتاه می‌شوند نه ON_STOP.
    androidx.compose.runtime.DisposableEffect(Unit) {
        var leftDuringSensitiveFlow = false
        val processObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    val route = navController.currentDestination?.route.orEmpty()
                    if (route.startsWith("student_biometric") || route.startsWith("student_audio")) {
                        leftDuringSensitiveFlow = true
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    if (leftDuringSensitiveFlow) {
                        leftDuringSensitiveFlow = false
                        navController.navigate(Routes.SCANNER) {
                            popUpTo(Routes.STUDENT_HOME)
                        }
                    }
                }
                else -> Unit
            }
        }
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        onDispose {
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(220)) },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onGoProfessor = { navController.navigate(Routes.PROF_DASHBOARD) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onGoStudent = { navController.navigate(Routes.STUDENT_HOME) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onGoLogin = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.SPLASH) { inclusive = true } } },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onGoProfessor = { navController.navigate(Routes.PROF_DASHBOARD) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onGoStudent = { navController.navigate(Routes.STUDENT_HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onGoRegister = { navController.navigate(Routes.REGISTER) },
            )
        }

        composable(Routes.REGISTER) {
            com.smartattendance.presentation.auth.RegisterScreen(
                onRegistered = { role ->
                    if (role == com.smartattendance.domain.model.Role.PROFESSOR) {
                        navController.navigate(Routes.PROF_DASHBOARD) { popUpTo(0) { inclusive = true } }
                    } else {
                        navController.navigate(Routes.STUDENT_HOME) { popUpTo(0) { inclusive = true } }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ───────────── Professor ─────────────
        composable(Routes.PROF_DASHBOARD) {
            ProfessorDashboardScreen(
                onCreateSession = { navController.navigate(Routes.PROF_CREATE) },
                onOpenLive = { navController.navigate(Routes.live(it)) },
                onOpenReports = { navController.navigate(Routes.PROF_REPORTS) },
            )
        }

        composable(Routes.PROF_CREATE) {
            CreateSessionScreen(
                onSessionStarted = { navController.navigate(Routes.live(it)) { popUpTo(Routes.PROF_DASHBOARD) } },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.PROF_LIVE,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) {
            LiveSessionScreen(
                onClosed = { navController.popBackStack(Routes.PROF_DASHBOARD, inclusive = false) },
            )
        }

        composable(Routes.PROF_REPORTS) {
            ReportsScreen(
                onBack = { navController.popBackStack() },
                onOpenDetail = { navController.navigate(Routes.reportDetail(it)) },
            )
        }

        composable(
            Routes.PROF_REPORT_DETAIL,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) {
            ReportDetailScreen(onBack = { navController.popBackStack() })
        }

        // ───────────── Student ─────────────
        composable(Routes.STUDENT_HOME) {
            StudentHomeScreen(
                onStartAttendance = { navController.navigate(Routes.SCANNER) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }

        composable(Routes.SCANNER) {
            ScannerScreen(
                onVerified = { sessionId, courseName ->
                    navController.navigate(Routes.biometric(sessionId, courseName)) {
                        popUpTo(Routes.SCANNER) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.BIOMETRIC,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("courseName") { type = NavType.StringType },
                navArgument("bioOk") { type = NavType.BoolType },
            ),
        ) { entry ->
            BiometricStepScreen(
                courseName = Routes.decode(entry.arguments?.getString("courseName").orEmpty()),
                onVerified = { sessionId, courseName ->
                    navController.navigate(Routes.audio(sessionId, courseName)) {
                        popUpTo(Routes.BIOMETRIC) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.AUDIO,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("courseName") { type = NavType.StringType },
                navArgument("bioOk") { type = NavType.BoolType },
            ),
        ) { entry ->
            AudioVerificationScreen(
                sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                courseName = Routes.decode(entry.arguments?.getString("courseName").orEmpty()),
                biometricOk = entry.arguments?.getBoolean("bioOk") ?: false,
                onVerified = { sessionId -> navController.navigate(Routes.result(sessionId)) { popUpTo(Routes.STUDENT_HOME) } },
                onBack = { navController.popBackStack() },
                onSecurityReset = {
                    navController.navigate(Routes.SCANNER) { popUpTo(Routes.STUDENT_HOME) }
                },
            )
        }

        composable(
            Routes.RESULT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) {
            AttendanceResultScreen(
                sessionId = it.arguments?.getString("sessionId").orEmpty(),
                onBackHome = {
                    navController.navigate(Routes.STUDENT_HOME) { popUpTo(Routes.STUDENT_HOME) { inclusive = true } }
                },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}
