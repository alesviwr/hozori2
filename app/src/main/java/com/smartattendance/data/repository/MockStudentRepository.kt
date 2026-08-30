package com.smartattendance.data.repository

import com.smartattendance.data.local.DeviceIdManager
import com.smartattendance.data.local.TokenStorage
import com.smartattendance.data.mock.MockBackend
import com.smartattendance.domain.model.AttendanceOutcome
import com.smartattendance.domain.model.IntegrityVerdict
import com.smartattendance.domain.model.StudentAttendanceItem
import com.smartattendance.domain.model.StudentHomeData
import com.smartattendance.domain.repository.QrVerification
import com.smartattendance.domain.repository.StudentRepository
import javax.inject.Inject
import javax.inject.Singleton

/** پیاده‌سازی Mock Repository دانشجو */
@Singleton
class MockStudentRepository @Inject constructor(
    private val backend: MockBackend,
    private val deviceIdManager: DeviceIdManager,
    private val tokenStorage: TokenStorage,
) : StudentRepository {

    override suspend fun getStudentHome(): StudentHomeData =
        backend.studentHome(tokenStorage.requireToken())

    override suspend fun verifyQr(qrPayload: String): QrVerification =
        backend.verifyQr(tokenStorage.requireToken(), qrPayload)

    override suspend fun submitAudioToken(
        sessionId: String,
        audioToken: String,
        biometricAttested: Boolean,
        integrityVerdict: IntegrityVerdict,
    ): AttendanceOutcome {
        val record = backend.submitAudio(
            token = tokenStorage.requireToken(),
            sessionId = sessionId,
            audioToken = audioToken,
            biometricAttested = biometricAttested,
            integrityVerdict = integrityVerdict.name,
            deviceId = deviceIdManager.getOrCreate(),
        )
        return AttendanceOutcome(record)
    }

    override suspend fun getHistory(): List<StudentAttendanceItem> =
        backend.history(tokenStorage.requireToken())
}
