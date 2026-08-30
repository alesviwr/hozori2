package com.smartattendance.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * DTOهای قرارداد Backend واقعی.
 * نام فیلدها و کدهای خطا باید با سرور یکسان بماند (مستند در README).
 */

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
    val deviceId: String? = null,
    val deviceModel: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val role: String, // PROFESSOR | STUDENT
    val studentNumber: String? = null,
)

@Serializable
data class LoginResponseDto(val token: String, val refreshToken: String? = null, val user: UserDto)

@Serializable
data class RegisterRequestDto(
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    val studentNumber: String? = null,
    val deviceId: String? = null,
    val deviceModel: String? = null,
)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class RefreshResponseDto(val token: String, val refreshToken: String? = null)

@Serializable
data class RegisterDeviceDto(val deviceId: String, val deviceModel: String? = null)

@Serializable
data class CourseDto(val id: String, val name: String, val building: String, val room: String)

@Serializable
data class CreateCourseRequestDto(val name: String, val building: String, val room: String)

@Serializable
data class SessionDto(
    val id: String,
    val courseId: String,
    val courseName: String,
    val professorId: String,
    val building: String,
    val room: String,
    val startedAt: Long,
    val expiresAt: Long,
    val windowSeconds: Long,
    val status: String, // CREATED | ACTIVE | EXPIRED | CLOSED
)

@Serializable
data class CreateSessionRequestDto(
    val courseId: String,
    val building: String,
    val room: String,
    val windowMinutes: Int,
)

@Serializable
data class QrTokenDto(val fullToken: String, val expiresAt: Long)

@Serializable
data class AudioChallengeDto(val challengeId: String, val token: String, val expiresAt: Long)

@Serializable
data class CourseTodayDto(val courseName: String, val room: String, val time: String)

@Serializable
data class ReportSummaryDto(
    val sessionId: String,
    val courseName: String,
    val date: String,
    val startedAt: Long,
    val presentCount: Int,
    val absentCount: Int,
)

@Serializable
data class DashboardDto(
    val professorName: String,
    val activeSession: SessionDto? = null,
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val pendingCount: Int = 0,
    val todayCourses: List<CourseTodayDto> = emptyList(),
    val recentSessions: List<ReportSummaryDto> = emptyList(),
)

@Serializable
data class StudentRowDto(
    val studentId: String,
    val studentName: String,
    val status: String, // PRESENT | PENDING | FAILED | ABSENT
    val timestamp: Long? = null,
)

@Serializable
data class MonitorDto(
    val session: SessionDto,
    val presentCount: Int,
    val pendingCount: Int,
    val absentCount: Int,
    val rows: List<StudentRowDto>,
)

@Serializable
data class ReportDetailDto(val summary: ReportSummaryDto, val rows: List<StudentRowDto>)

@Serializable
data class VerifyQrRequestDto(val qrPayload: String)

@Serializable
data class VerifyQrResponseDto(val sessionId: String, val courseName: String)

@Serializable
data class SubmitAttendanceRequestDto(
    val sessionId: String,
    val audioToken: String,
    val biometricAttested: Boolean,
    val integrityVerdict: String,
    val deviceId: String,
)

@Serializable
data class AttendanceRecordDto(
    val sessionId: String,
    val studentId: String,
    val studentName: String,
    val timestamp: Long,
    val status: String,
    val qrVerified: Boolean,
    val biometricVerified: Boolean,
    val audioVerified: Boolean,
    val deviceId: String,
)

@Serializable
data class AttendanceOutcomeDto(val record: AttendanceRecordDto? = null)

@Serializable
data class StudentAttendanceItemDto(
    val courseName: String,
    val date: String,
    val status: String,
    val timestamp: Long? = null,
)

@Serializable
data class StudentHomeDto(
    val studentName: String,
    val studentNumber: String,
    val activeSession: SessionDto? = null,
    val recent: List<StudentAttendanceItemDto> = emptyList(),
)

@Serializable
data class IntegrityRequestDto(val nonce: String, val integrityToken: String)

@Serializable
data class IntegrityResponseDto(val verdict: String) // PASSES | FAILS | UNKNOWN

@Serializable
data class BiometricEnrollDto(
    val deviceId: String,
    val publicKey: String,
    val algorithm: String = "EC_P256",
)
