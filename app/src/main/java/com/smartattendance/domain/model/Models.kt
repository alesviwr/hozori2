package com.smartattendance.domain.model

/** نقش کاربر — فقط سرور تعیین می‌کند؛ کلاینت حق تغییر ندارد */
enum class Role { PROFESSOR, STUDENT }

data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: Role,
    val studentNumber: String? = null,
)

data class Course(
    val id: String,
    val name: String,
    val building: String,
    val room: String,
)

enum class SessionStatus { CREATED, ACTIVE, EXPIRED, CLOSED }

/**
 * جلسه حضور و غیاب.
 * windowSeconds: بازه‌ای که دانشجو اجازه ثبت حضور دارد.
 */
data class AttendanceSession(
    val id: String,
    val courseId: String,
    val courseName: String,
    val professorId: String,
    val building: String,
    val room: String,
    val startedAt: Long,
    val expiresAt: Long,
    val windowSeconds: Long,
    val status: SessionStatus,
)

enum class AttendanceStatus { PRESENT, PENDING, FAILED, ABSENT }

/**
 * رکورد حضور نهایی — روی سرور با UNIQUE(sessionId, studentId) محافظت می‌شود.
 * پرچم‌های verify صرفاً «اظهار» عوامل سمت کلاینت‌اند؛ QR و Audio روی سرور خودش verify می‌شود.
 */
data class AttendanceRecord(
    val sessionId: String,
    val studentId: String,
    val studentName: String,
    val timestamp: Long,
    val status: AttendanceStatus,
    val qrVerified: Boolean,
    val biometricVerified: Boolean,
    val audioVerified: Boolean,
    val deviceId: String,
)

/** توکن QR گردشی که سرور تولید و امضا می‌کند */
data class QrTokenData(
    val fullToken: String,
    val expiresAt: Long,
)

/** Audio Challenge فعال جلسه — کد ۸ رقمی هگز که صدا پخش می‌شود */
data class AudioChallengeData(
    val challengeId: String,
    val token: String,
    val expiresAt: Long,
)

data class CourseToday(val courseName: String, val room: String, val time: String)

data class DashboardData(
    val professorName: String,
    val activeSession: AttendanceSession?,
    val presentCount: Int,
    val absentCount: Int,
    val pendingCount: Int,
    val todayCourses: List<CourseToday>,
    val recentSessions: List<ReportSummary>,
)

data class StudentAttendanceItem(
    val courseName: String,
    val date: String,
    val status: AttendanceStatus,
    val timestamp: Long? = null,
)

data class StudentHomeData(
    val studentName: String,
    val studentNumber: String,
    val activeSession: AttendanceSession?,
    val recent: List<StudentAttendanceItem>,
)

data class StudentRow(
    val studentId: String,
    val studentName: String,
    val status: AttendanceStatus,
    val timestamp: Long? = null,
)

data class MonitorData(
    val session: AttendanceSession,
    val presentCount: Int,
    val pendingCount: Int,
    val absentCount: Int,
    val rows: List<StudentRow>,
)

data class CreateSessionRequest(
    val courseId: String,
    val building: String,
    val room: String,
    val windowMinutes: Int,
)

data class ReportSummary(
    val sessionId: String,
    val courseName: String,
    val date: String,
    val startedAt: Long,
    val presentCount: Int,
    val absentCount: Int,
)

data class ReportDetail(
    val summary: ReportSummary,
    val rows: List<StudentRow>,
)

/** نتیجه تأیید نهایی حضور روی سرور */
data class AttendanceOutcome(
    val record: AttendanceRecord?,
)

/** اعتبار یکپارچگی اپ (Play Integrity) */
enum class IntegrityVerdict { PASSES, FAILS, UNKNOWN }
