package com.smartattendance.data.remote.api

import com.smartattendance.data.remote.dto.AttendanceOutcomeDto
import com.smartattendance.data.remote.dto.AudioChallengeDto
import com.smartattendance.data.remote.dto.CourseDto
import com.smartattendance.data.remote.dto.CreateCourseRequestDto
import com.smartattendance.data.remote.dto.CreateSessionRequestDto
import com.smartattendance.data.remote.dto.DashboardDto
import com.smartattendance.data.remote.dto.IntegrityRequestDto
import com.smartattendance.data.remote.dto.IntegrityResponseDto
import com.smartattendance.data.remote.dto.LoginRequestDto
import com.smartattendance.data.remote.dto.LoginResponseDto
import com.smartattendance.data.remote.dto.MonitorDto
import com.smartattendance.data.remote.dto.QrTokenDto
import com.smartattendance.data.remote.dto.RefreshRequestDto
import com.smartattendance.data.remote.dto.RefreshResponseDto
import com.smartattendance.data.remote.dto.RegisterDeviceDto
import com.smartattendance.data.remote.dto.RegisterRequestDto
import com.smartattendance.data.remote.dto.StudentAttendanceItemDto
import com.smartattendance.data.remote.dto.ReportDetailDto
import com.smartattendance.data.remote.dto.ReportSummaryDto
import com.smartattendance.data.remote.dto.StudentHomeDto
import com.smartattendance.data.remote.dto.SessionDto
import com.smartattendance.data.remote.dto.SubmitAttendanceRequestDto
import com.smartattendance.data.remote.dto.VerifyQrRequestDto
import com.smartattendance.data.remote.dto.VerifyQrResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * قرارداد REST سرور حضور و غیاب.
 * تمام endpointهای حساس با هدر Authorization: Bearer <token> فراخوانی می‌شوند
 * (AuthInterceptor این هدر را خودکار اضافه می‌کند).
 *
 * ساختار خطا: سرور با HTTP code != 2xx باید بدنه {"error": "QR_EXPIRED"} برگرداند
 * که ErrorInterceptor آن را به AppException مپ می‌کند.
 */
interface AttendanceApi {

    // ───────── Auth ─────────
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto)

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): RefreshResponseDto

    @POST("devices/register")
    suspend fun registerDevice(@Body body: RegisterDeviceDto)

    // ───────── Professor ─────────
    @GET("professor/dashboard")
    suspend fun dashboard(): DashboardDto

    @GET("professor/courses")
    suspend fun courses(): List<CourseDto>

    @POST("professor/courses")
    suspend fun createCourse(@Body body: CreateCourseRequestDto): CourseDto

    @POST("professor/sessions")
    suspend fun createSession(@Body body: CreateSessionRequestDto): SessionDto

    @GET("professor/sessions/active")
    suspend fun activeSession(): SessionDto?

    @GET("professor/sessions/{id}/qr-token")
    suspend fun qrToken(@Path("id") sessionId: String): QrTokenDto

    @GET("professor/sessions/{id}/audio-challenge")
    suspend fun audioChallenge(@Path("id") sessionId: String): AudioChallengeDto

    @GET("professor/sessions/{id}/monitor")
    suspend fun monitor(@Path("id") sessionId: String): MonitorDto

    @POST("professor/sessions/{id}/close")
    suspend fun closeSession(@Path("id") sessionId: String)

    @GET("professor/reports")
    suspend fun reports(): List<ReportSummaryDto>

    @GET("professor/reports/{id}")
    suspend fun reportDetail(@Path("id") sessionId: String): ReportDetailDto

    // ───────── Student ─────────
    @GET("student/home")
    suspend fun studentHome(): StudentHomeDto

    @POST("attendance/verify-qr")
    suspend fun verifyQr(@Body body: VerifyQrRequestDto): VerifyQrResponseDto

    @POST("attendance/verify-audio")
    suspend fun verifyAudio(@Body body: SubmitAttendanceRequestDto): AttendanceOutcomeDto

    @GET("student/history")
    suspend fun history(): List<StudentAttendanceItemDto>

    // ───────── Security ─────────
    @POST("security/integrity")
    suspend fun integrity(@Body body: IntegrityRequestDto): IntegrityResponseDto
}
