package com.smartattendance.data.repository

import com.smartattendance.data.local.AppDatabase
import com.smartattendance.data.local.CachedReportEntity
import com.smartattendance.data.local.TokenStorage
import com.smartattendance.data.local.toDomain
import com.smartattendance.data.mock.MockBackend
import com.smartattendance.domain.model.AttendanceSession
import com.smartattendance.domain.model.AudioChallengeData
import com.smartattendance.domain.model.Course
import com.smartattendance.domain.model.CreateSessionRequest
import com.smartattendance.domain.model.DashboardData
import com.smartattendance.domain.model.MonitorData
import com.smartattendance.domain.model.QrTokenData
import com.smartattendance.domain.model.ReportDetail
import com.smartattendance.domain.model.ReportSummary
import com.smartattendance.domain.repository.ProfessorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** پیاده‌سازی Mock Repository استاد */
@Singleton
class MockProfessorRepository @Inject constructor(
    private val backend: MockBackend,
    private val database: AppDatabase,
    private val tokenStorage: TokenStorage,
) : ProfessorRepository {

    override suspend fun getDashboard(): DashboardData =
        backend.dashboard(tokenStorage.requireToken())

    override suspend fun getCourses(): List<Course> =
        backend.getCourses(tokenStorage.requireToken())

    override suspend fun createCourse(name: String, building: String, room: String): Course =
        backend.createCourse(tokenStorage.requireToken(), name, building, room)

    override suspend fun createSession(request: CreateSessionRequest): AttendanceSession =
        backend.createSession(
            token = tokenStorage.requireToken(),
            courseId = request.courseId,
            building = request.building,
            room = request.room,
            windowMinutes = request.windowMinutes,
        )

    override suspend fun getActiveSession(): AttendanceSession? =
        backend.getActiveSession(tokenStorage.requireToken())

    override suspend fun pollQrToken(sessionId: String): QrTokenData =
        backend.pollQrToken(tokenStorage.requireToken(), sessionId)

    override suspend fun pollAudioChallenge(sessionId: String): AudioChallengeData =
        backend.pollAudioChallenge(tokenStorage.requireToken(), sessionId)

    override suspend fun getMonitor(sessionId: String): MonitorData =
        backend.monitor(tokenStorage.requireToken(), sessionId)

    override suspend fun closeSession(sessionId: String) {
        backend.closeSession(tokenStorage.requireToken(), sessionId)
        cacheReports()
    }

    override suspend fun getReports(): List<ReportSummary> {
        val reports = backend.reports(tokenStorage.requireToken())
        cacheReports(reports)
        return reports
    }

    override suspend fun getReportDetail(sessionId: String): ReportDetail {
        val (summary, rows) = backend.reportDetail(tokenStorage.requireToken(), sessionId)
        return ReportDetail(summary, rows)
    }

    override fun observeCachedReports(): Flow<List<ReportSummary>> =
        database.cachedReportDao().observeAll().map { it.toDomain() }

    private suspend fun cacheReports(source: List<ReportSummary>? = null) {
        val reports = source ?: runCatching { backend.reports(tokenStorage.requireToken()) }.getOrNull() ?: return
        database.cachedReportDao().clear()
        database.cachedReportDao().insertAll(
            reports.map {
                CachedReportEntity(
                    sessionId = it.sessionId,
                    courseName = it.courseName,
                    date = it.date,
                    startedAt = it.startedAt,
                    presentCount = it.presentCount,
                    absentCount = it.absentCount,
                )
            },
        )
    }
}
