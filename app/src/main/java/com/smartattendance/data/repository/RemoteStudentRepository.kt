package com.smartattendance.data.repository

import com.smartattendance.core.util.AppException
import com.smartattendance.data.local.DeviceIdManager
import com.smartattendance.data.remote.api.AttendanceApi
import com.smartattendance.data.remote.dto.SubmitAttendanceRequestDto
import com.smartattendance.data.remote.dto.VerifyQrRequestDto
import com.smartattendance.domain.model.AttendanceOutcome
import com.smartattendance.domain.model.AttendanceRecord
import com.smartattendance.domain.model.AttendanceSession
import com.smartattendance.domain.model.AttendanceStatus
import com.smartattendance.domain.model.IntegrityVerdict
import com.smartattendance.domain.model.StudentAttendanceItem
import com.smartattendance.domain.model.StudentHomeData
import com.smartattendance.domain.repository.QrVerification
import com.smartattendance.domain.repository.StudentRepository
import com.smartattendance.domain.repository.mapThrowable
import javax.inject.Inject
import javax.inject.Singleton

/** پیاده‌سازی واقعی Repository دانشجو با Retrofit — فقط مپ DTO↔Domain */
@Singleton
class RemoteStudentRepository @Inject constructor(
    private val api: AttendanceApi,
    private val deviceIdManager: DeviceIdManager,
) : StudentRepository {

    override suspend fun getStudentHome(): StudentHomeData = api.studentHome().let { dto ->
        StudentHomeData(
            studentName = dto.studentName,
            studentNumber = dto.studentNumber,
            activeSession = dto.activeSession?.let { session ->
                AttendanceSession(
                    session.id, session.courseId, session.courseName, session.professorId,
                    session.building, session.room, session.startedAt, session.expiresAt,
                    session.windowSeconds,
                    runCatching { com.smartattendance.domain.model.SessionStatus.valueOf(session.status) }
                        .getOrDefault(com.smartattendance.domain.model.SessionStatus.CREATED),
                )
            },
            recent = dto.recent.map {
                StudentAttendanceItem(
                    it.courseName, it.date,
                    runCatching { AttendanceStatus.valueOf(it.status) }.getOrDefault(AttendanceStatus.PENDING),
                    it.timestamp,
                )
            },
        )
    }

    override suspend fun verifyQr(qrPayload: String): QrVerification = try {
        api.verifyQr(VerifyQrRequestDto(qrPayload)).let {
            QrVerification(it.sessionId, it.courseName)
        }
    } catch (t: Throwable) {
        throw AppException(mapThrowable(t))
    }

    override suspend fun submitAudioToken(
        sessionId: String,
        audioToken: String,
        biometricAttested: Boolean,
        integrityVerdict: IntegrityVerdict,
    ): AttendanceOutcome = try {
        api.verifyAudio(
            SubmitAttendanceRequestDto(
                sessionId = sessionId,
                audioToken = audioToken,
                biometricAttested = biometricAttested,
                integrityVerdict = integrityVerdict.name,
                deviceId = deviceIdManager.getOrCreate(),
            ),
        ).record?.let {
            AttendanceOutcome(
                AttendanceRecord(
                    it.sessionId, it.studentId, it.studentName, it.timestamp,
                    runCatching { AttendanceStatus.valueOf(it.status) }.getOrDefault(AttendanceStatus.PRESENT),
                    it.qrVerified, it.biometricVerified, it.audioVerified, it.deviceId,
                ),
            )
        } ?: AttendanceOutcome(null)
    } catch (t: Throwable) {
        throw AppException(mapThrowable(t))
    }

    override suspend fun getHistory(): List<StudentAttendanceItem> = api.history().map {
        StudentAttendanceItem(
            it.courseName, it.date,
            runCatching { AttendanceStatus.valueOf(it.status) }.getOrDefault(AttendanceStatus.PENDING),
            it.timestamp,
        )
    }
}
