package com.smartattendance.data.repository

import com.smartattendance.core.util.AppException
import com.smartattendance.data.local.AppDatabase
import com.smartattendance.data.local.CachedReportEntity
import com.smartattendance.data.local.toDomain
import com.smartattendance.data.remote.api.AttendanceApi
import com.smartattendance.data.remote.dto.CreateSessionRequestDto
import com.smartattendance.domain.model.AttendanceSession
import com.smartattendance.domain.model.AudioChallengeData
import com.smartattendance.domain.model.Course
import com.smartattendance.domain.model.CreateSessionRequest
import com.smartattendance.domain.model.DashboardData
import com.smartattendance.domain.model.MonitorData
import com.smartattendance.domain.model.QrTokenData
import com.smartattendance.domain.model.ReportDetail
import com.smartattendance.domain.model.ReportSummary
import com.smartattendance.domain.model.SessionStatus
import com.smartattendance.domain.repository.ProfessorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** پیاده‌سازی واقعی Repository استاد با Retrofit — فقط مپ DTO↔Domain */
@Singleton
class RemoteProfessorRepository @Inject constructor(
    private val api: AttendanceApi,
    private val database: AppDatabase,
) : ProfessorRepository {

    override suspend fun getDashboard(): DashboardData = api.dashboard().let { dto ->
        DashboardData(
            professorName = dto.professorName,
            activeSession = dto.activeSession?.toDomain(),
            presentCount = dto.presentCount,
            absentCount = dto.absentCount,
            pendingCount = dto.pendingCount,
            todayCourses = dto.todayCourses.map { com.smartattendance.domain.model.CourseToday(it.courseName, it.room, it.time) },
            recentSessions = dto.recentSessions.map { it.toDomain() },
        )
    }

    override suspend fun getCourses(): List<Course> = api.courses().map {
        Course(it.id, it.name, it.building, it.room)
    }

    override suspend fun createCourse(name: String, building: String, room: String): Course =
        api.createCourse(com.smartattendance.data.remote.dto.CreateCourseRequestDto(name, building, room)).let {
            Course(it.id, it.name, it.building, it.room)
        }

    override suspend fun createSession(request: CreateSessionRequest): AttendanceSession = api.createSession(
        CreateSessionRequestDto(request.courseId, request.building, request.room, request.windowMinutes),
    ).toDomain()

    override suspend fun getActiveSession(): AttendanceSession? = api.activeSession()?.toDomain()

    override suspend fun pollQrToken(sessionId: String): QrTokenData = api.qrToken(sessionId).let {
        QrTokenData(it.fullToken, it.expiresAt)
    }

    override suspend fun pollAudioChallenge(sessionId: String): AudioChallengeData = api.audioChallenge(sessionId).let {
        AudioChallengeData(it.challengeId, it.token, it.expiresAt)
    }

    override suspend fun getMonitor(sessionId: String): MonitorData = api.monitor(sessionId).let { dto ->
        MonitorData(
            session = dto.session.toDomain(),
            presentCount = dto.presentCount,
            pendingCount = dto.pendingCount,
            absentCount = dto.absentCount,
            rows = dto.rows.map {
                com.smartattendance.domain.model.StudentRow(
                    it.studentId, it.studentName,
                    com.smartattendance.domain.model.AttendanceStatus.valueOf(it.status), it.timestamp,
                )
            },
        )
    }

    override suspend fun closeSession(sessionId: String) {
        api.closeSession(sessionId)
        cacheReports()
    }

    override suspend fun getReports(): List<ReportSummary> {
        val reports = api.reports().map { it.toDomain() }
        cacheReports(reports)
        return reports
    }

    override suspend fun getReportDetail(sessionId: String): ReportDetail = api.reportDetail(sessionId).let { dto ->
        ReportDetail(
            summary = dto.summary.toDomain(),
            rows = dto.rows.map {
                com.smartattendance.domain.model.StudentRow(
                    it.studentId, it.studentName,
                    com.smartattendance.domain.model.AttendanceStatus.valueOf(it.status), it.timestamp,
                )
            },
        )
    }

    override fun observeCachedReports(): Flow<List<ReportSummary>> =
        database.cachedReportDao().observeAll().map { it.toDomain() }

    private suspend fun cacheReports(source: List<ReportSummary>? = null) {
        val reports = source ?: runCatching { api.reports().map { it.toDomain() } }.getOrNull() ?: return
        database.cachedReportDao().clear()
        database.cachedReportDao().insertAll(
            reports.map {
                CachedReportEntity(it.sessionId, it.courseName, it.date, it.startedAt, it.presentCount, it.absentCount)
            },
        )
    }

    private fun com.smartattendance.data.remote.dto.SessionDto.toDomain() = AttendanceSession(
        id, courseId, courseName, professorId, building, room, startedAt, expiresAt, windowSeconds,
        runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.CREATED),
    )

    private fun com.smartattendance.data.remote.dto.ReportSummaryDto.toDomain() =
        ReportSummary(sessionId, courseName, date, startedAt, presentCount, absentCount)
}
