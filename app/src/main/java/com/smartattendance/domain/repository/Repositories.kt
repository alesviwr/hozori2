package com.smartattendance.domain.repository

import com.smartattendance.core.util.AppErrorType
import com.smartattendance.domain.model.AttendanceOutcome
import com.smartattendance.domain.model.AttendanceSession
import com.smartattendance.domain.model.AudioChallengeData
import com.smartattendance.domain.model.Course
import com.smartattendance.domain.model.CreateSessionRequest
import com.smartattendance.domain.model.DashboardData
import com.smartattendance.domain.model.IntegrityVerdict
import com.smartattendance.domain.model.MonitorData
import com.smartattendance.domain.model.QrTokenData
import com.smartattendance.domain.model.ReportDetail
import com.smartattendance.domain.model.ReportSummary
import com.smartattendance.domain.model.StudentAttendanceItem
import com.smartattendance.domain.model.StudentHomeData
import com.smartattendance.domain.model.User
import kotlinx.coroutines.flow.Flow

// ─────────────────────────── Auth ───────────────────────────

data class LoginResult(val user: User)

interface AuthRepository {
    /** ورود — Role توسط Backend تعیین و در پاسخ برگردانده می‌شود */
    suspend fun login(email: String, password: String): LoginResult

    /** ثبت‌نام کاربر جدید — پس از موفقیت، ورود خودکار انجام می‌شود */
    suspend fun register(name: String, email: String, password: String, role: Role, studentNumber: String?)

    /** کاربر جاری؛ اگر نشست نامعتبر باشد null برمی‌گرداند */
    suspend fun currentUser(): User?

    suspend fun logout()

    /** ثبت دستگاه دانشجو برای Device Binding */
    suspend fun registerDevice()

    /** رویداد خروج (برای هدایت سراسری به صفحه لاگین) */
    val logoutEvents: Flow<Unit>
}

// ──────────────────────── Professor ─────────────────────────

interface ProfessorRepository {
    suspend fun getDashboard(): DashboardData
    suspend fun getCourses(): List<Course>
    suspend fun createCourse(name: String, building: String, room: String): Course
    suspend fun createSession(request: CreateSessionRequest): AttendanceSession
    suspend fun getActiveSession(): AttendanceSession?

    /** توکن QR گردشی جاری جلسه (سرور هر ~۳ ثانیه rotate می‌کند) */
    suspend fun pollQrToken(sessionId: String): QrTokenData

    /** Audio Challenge فعال جاری جلسه (سرور هر ~۱۲ ثانیه عوض می‌کند) */
    suspend fun pollAudioChallenge(sessionId: String): AudioChallengeData

    suspend fun getMonitor(sessionId: String): MonitorData
    suspend fun closeSession(sessionId: String)
    suspend fun getReports(): List<ReportSummary>
    suspend fun getReportDetail(sessionId: String): ReportDetail

    /** کش محلی جلسات بسته‌شده (Room) */
    fun observeCachedReports(): Flow<List<ReportSummary>>
}

// ───────────────────────── Student ──────────────────────────

data class QrVerification(
    val sessionId: String,
    val courseName: String,
)

interface StudentRepository {
    suspend fun getStudentHome(): StudentHomeData

    /**
     * ارسال توکن QR اسکن‌شده به سرور.
     * سرور امضا، انقضا، Replay و وضعیت Session را بررسی می‌کند.
     */
    suspend fun verifyQr(qrPayload: String): QrVerification

    /**
     * ارسال توکن صوتی استخراج‌شده از محیط + اظهار بیومتریک + نتیجه Integrity.
     * سرور همه عوامل را دوباره بررسی و رکورد نهایی را با UNIQUE(sessionId, studentId) ثبت می‌کند.
     */
    suspend fun submitAudioToken(
        sessionId: String,
        audioToken: String,
        biometricAttested: Boolean,
        integrityVerdict: IntegrityVerdict,
    ): AttendanceOutcome

    suspend fun getHistory(): List<StudentAttendanceItem>
}

/** نگاشت خطای سرور/شبکه به AppErrorType — در هر دو پیاده‌سازی Mock و Remote استفاده می‌شود */
fun mapThrowable(t: Throwable): AppErrorType = when (t) {
    is com.smartattendance.core.util.AppException -> t.type
    else -> AppErrorType.NETWORK_ERROR
}
