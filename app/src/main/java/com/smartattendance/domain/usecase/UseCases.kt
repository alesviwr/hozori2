package com.smartattendance.domain.usecase

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
import com.smartattendance.domain.model.Role
import com.smartattendance.domain.model.User
import com.smartattendance.domain.repository.AuthRepository
import com.smartattendance.domain.repository.LoginResult
import com.smartattendance.domain.repository.ProfessorRepository
import com.smartattendance.domain.repository.QrVerification
import com.smartattendance.domain.repository.StudentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// ─────────────────────────── Auth ───────────────────────────

class LoginUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): LoginResult = auth.login(email, password)
}

class RegisterUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(
        name: String,
        email: String,
        password: String,
        role: Role,
        studentNumber: String?,
    ) = auth.register(name, email, password, role, studentNumber)
}

class GetCurrentUserUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(): User? = auth.currentUser()
}

class LogoutUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke() = auth.logout()
}

class ObserveLogoutUseCase @Inject constructor(private val auth: AuthRepository) {
    operator fun invoke(): Flow<Unit> = auth.logoutEvents
}

// ───────────────────────── Professor ─────────────────────────

class GetDashboardUseCase @Inject constructor(private val repo: ProfessorRepository) {
    suspend operator fun invoke(): DashboardData = repo.getDashboard()
}

class GetCoursesUseCase @Inject constructor(private val repo: ProfessorRepository) {
    suspend operator fun invoke(): List<Course> = repo.getCourses()
}

class CreateCourseUseCase @Inject constructor(private val repo: ProfessorRepository) {
    suspend operator fun invoke(name: String, building: String, room: String): Course =
        repo.createCourse(name, building, room)
}

class CreateSessionUseCase @Inject constructor(private val repo: ProfessorRepository) {
    suspend operator fun invoke(request: CreateSessionRequest): AttendanceSession = repo.createSession(request)
}

class GetActiveSessionUseCase @Inject constructor(private val repo: ProfessorRepository) {
    suspend operator fun invoke(): AttendanceSession? = repo.getActiveSession()
}

class PollQrTokenUseCase @Inject constructor(private val repo: ProfessorRepository) {
    suspend operator fun invoke(sessionId: String): QrTokenData = repo.pollQrToken(sessionId)
}

class PollAudioChallengeUseCase @Inject constructor(private val repo: ProfessorRepository) {
    suspend operator fun invoke(sessionId: String): AudioChallengeData = repo.pollAudioChallenge(sessionId)
}

class GetMonitorUseCase @Inject constructor(private val repo: ProfessorRepository) {
    suspend operator fun invoke(sessionId: String): MonitorData = repo.getMonitor(sessionId)
}

class CloseSessionUseCase @Inject constructor(private val repo: ProfessorRepository) {
    suspend operator fun invoke(sessionId: String) = repo.closeSession(sessionId)
}

class GetReportsUseCase @Inject constructor(private val repo: ProfessorRepository) {
    suspend operator fun invoke(): List<ReportSummary> = repo.getReports()
}

class GetReportDetailUseCase @Inject constructor(private val repo: ProfessorRepository) {
    suspend operator fun invoke(sessionId: String): ReportDetail = repo.getReportDetail(sessionId)
}

class ObserveCachedReportsUseCase @Inject constructor(private val repo: ProfessorRepository) {
    operator fun invoke(): Flow<List<ReportSummary>> = repo.observeCachedReports()
}

// ───────────────────────── Student ──────────────────────────

class GetStudentHomeUseCase @Inject constructor(private val repo: StudentRepository) {
    suspend operator fun invoke(): StudentHomeData = repo.getStudentHome()
}

class VerifyQrUseCase @Inject constructor(private val repo: StudentRepository) {
    suspend operator fun invoke(qrPayload: String): QrVerification = repo.verifyQr(qrPayload)
}

class SubmitAudioTokenUseCase @Inject constructor(private val repo: StudentRepository) {
    suspend operator fun invoke(
        sessionId: String,
        audioToken: String,
        biometricAttested: Boolean,
        integrityVerdict: IntegrityVerdict,
    ): AttendanceOutcome = repo.submitAudioToken(sessionId, audioToken, biometricAttested, integrityVerdict)
}

class GetHistoryUseCase @Inject constructor(private val repo: StudentRepository) {
    suspend operator fun invoke(): List<StudentAttendanceItem> = repo.getHistory()
}
