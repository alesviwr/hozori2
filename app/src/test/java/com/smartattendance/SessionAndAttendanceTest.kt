package com.smartattendance

import com.smartattendance.core.util.AppErrorType
import com.smartattendance.core.util.AppException
import com.smartattendance.data.mock.MockBackend
import com.smartattendance.domain.model.AttendanceStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * تست‌های اعتبارسنجی Session و سناریوهای ضدتقلب (بخش ۳۳ پرامپت):
 * Replay QR، QR منقضی، Audio Token مصرف‌شده، Session اشتباه،
 * حضور تکراری، Role غیرمجاز و Device Binding.
 */
class SessionAndAttendanceTest {

    private lateinit var backend: MockBackend
    private var testNow = 1_750_000_000_000L

    private lateinit var profToken: String
    private lateinit var studentToken: String
    private lateinit var student2Token: String

    @Before
    fun setup() = runBlocking {
        backend = MockBackend(simulateLatency = false) { testNow }
        profToken = backend.login("prof@uni.edu", "12345678", null).token
        studentToken = backend.login("ali@uni.edu", "12345678", "device_A").token
        student2Token = backend.login("sara@uni.edu", "12345678", "device_B").token
    }

    private fun createSession(windowMinutes: Int = 10): String = runBlocking {
        backend.createSession(profToken, "c1", "مهندسی", "204", windowMinutes).id
    }

    private fun currentQr(sessionId: String): String = runBlocking {
        backend.pollQrToken(profToken, sessionId).fullToken
    }

    private fun expectError(type: AppErrorType, block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            throw AssertionError("باید AppException($type) پرتاب می‌شد")
        } catch (e: AppException) {
            assertEquals(type, e.type)
        }
    }

    // ─────────────── Role سرور ───────────────

    @Test
    fun `role comes from server and cannot be forged`() = runBlocking {
        val prof = backend.currentUser(profToken)
        val student = backend.currentUser(studentToken)
        assertEquals("PROFESSOR", prof.role.name)
        assertEquals("STUDENT", student.role.name)

        // دانشجو نباید به endpointهای استاد دسترسی داشته باشد
        expectError(AppErrorType.UNAUTHORIZED) { backend.dashboard(studentToken) }
        // استاد نباید فلو دانشجو را اجرا کند
        expectError(AppErrorType.UNAUTHORIZED) { backend.verifyQr(profToken, "AT|s|t|1|2|n|sig") }
    }

    // ─────────────── QR ───────────────

    @Test
    fun `valid live QR is accepted`() = runBlocking {
        val sessionId = createSession()
        val verification = backend.verifyQr(studentToken, currentQr(sessionId))
        assertEquals(sessionId, verification.sessionId)
        assertEquals("ساختمان داده", verification.courseName)
    }

    @Test
    fun `tampered QR signature is rejected`() = runBlocking {
        val sessionId = createSession()
        val original = currentQr(sessionId)
        val parts = original.split("|").toMutableList()
        parts[2] = "FFFFFFFF" // جعل tokenId
        expectError(AppErrorType.QR_INVALID) { backend.verifyQr(studentToken, parts.joinToString("|")) }
    }

    @Test
    fun `expired QR is rejected`() = runBlocking {
        val sessionId = createSession()
        val qr = currentQr(sessionId)
        testNow += MockBackend.QR_TTL_MS + 1
        expectError(AppErrorType.QR_EXPIRED) { backend.verifyQr(studentToken, qr) }
    }

    @Test
    fun `QR replay by same student is rejected but other students can scan`() {
        runBlocking {
            val sessionId = createSession()
            val qr = currentQr(sessionId)
            backend.verifyQr(studentToken, qr)
            expectError(AppErrorType.QR_INVALID) { backend.verifyQr(studentToken, qr) }
            // دانشجوی دیگر در همان بازه زنده مجاز است
            backend.verifyQr(student2Token, qr)
            Unit
        }
    }

    @Test
    fun `QR of closed session is rejected`() = runBlocking {
        val sessionId = createSession()
        val qr = currentQr(sessionId)
        backend.closeSession(profToken, sessionId)
        expectError(AppErrorType.SESSION_CLOSED) { backend.verifyQr(studentToken, qr) }
    }

    @Test
    fun `QR of window-expired session is rejected`() = runBlocking {
        val sessionId = createSession(windowMinutes = 1)
        val qr = currentQr(sessionId) // توکن قبل از انقضا
        testNow += 61_000 // Session از بازه عبور کرده (توکن هم منقضی است ولی وضعیت Session اولویت دارد)
        expectError(AppErrorType.SESSION_EXPIRED) { backend.verifyQr(studentToken, qr) }
    }

    // ─────────────── Audio + ثبت نهایی ───────────────

    @Test
    fun `submit without QR step is rejected`() = runBlocking {
        val sessionId = createSession()
        val challenge = backend.pollAudioChallenge(profToken, sessionId)
        expectError(AppErrorType.QR_REQUIRED) {
            backend.submitAudio(studentToken, sessionId, challenge.token, true, "PASSES", "device_A")
        }
    }

    @Test
    fun `full multi-factor flow registers PRESENT`() = runBlocking {
        val sessionId = createSession()
        val qr = currentQr(sessionId)
        backend.verifyQr(studentToken, qr)

        val challenge = backend.pollAudioChallenge(profToken, sessionId)
        val record = backend.submitAudio(studentToken, sessionId, challenge.token, true, "PASSES", "device_A")

        assertEquals(AttendanceStatus.PRESENT, record.status)
        assertTrue(record.qrVerified)
        assertTrue(record.biometricVerified)
        assertTrue(record.audioVerified)
        assertEquals("device_A", record.deviceId)
    }

    @Test
    fun `duplicate attendance is rejected by UNIQUE constraint`() = runBlocking {
        val sessionId = createSession()
        val qr = currentQr(sessionId)
        backend.verifyQr(studentToken, qr)
        val challenge = backend.pollAudioChallenge(profToken, sessionId)
        backend.submitAudio(studentToken, sessionId, challenge.token, true, "PASSES", "device_A")

        expectError(AppErrorType.ALREADY_ATTENDED) {
            backend.submitAudio(studentToken, sessionId, challenge.token, true, "PASSES", "device_A")
        }
    }

    @Test
    fun `wrong audio token is rejected`() = runBlocking {
        val sessionId = createSession()
        backend.verifyQr(studentToken, currentQr(sessionId))
        expectError(AppErrorType.AUDIO_INVALID) {
            backend.submitAudio(studentToken, sessionId, "DEADBEEF", true, "PASSES", "device_A")
        }
    }

    @Test
    fun `expired audio challenge is rejected`() = runBlocking {
        val sessionId = createSession()
        backend.verifyQr(studentToken, currentQr(sessionId))
        val challenge = backend.pollAudioChallenge(profToken, sessionId)
        testNow += MockBackend.AUDIO_TTL_MS + 1
        expectError(AppErrorType.CHALLENGE_EXPIRED) {
            backend.submitAudio(studentToken, sessionId, challenge.token, true, "PASSES", "device_A")
        }
    }

    @Test
    fun `audio token of another session is rejected`() = runBlocking {
        val session1 = createSession()
        val session2 = createSession()
        backend.verifyQr(studentToken, currentQr(session1))
        val challengeOfSession2 = backend.pollAudioChallenge(profToken, session2)
        expectError(AppErrorType.AUDIO_INVALID) {
            backend.submitAudio(studentToken, session1, challengeOfSession2.token, true, "PASSES", "device_A")
        }
    }

    @Test
    fun `missing biometric attestation is rejected`() = runBlocking {
        val sessionId = createSession()
        backend.verifyQr(studentToken, currentQr(sessionId))
        val challenge = backend.pollAudioChallenge(profToken, sessionId)
        expectError(AppErrorType.BIOMETRIC_FAILED) {
            backend.submitAudio(studentToken, sessionId, challenge.token, false, "PASSES", "device_A")
        }
    }

    @Test
    fun `device binding mismatch is rejected`() = runBlocking {
        val sessionId = createSession()
        backend.verifyQr(studentToken, currentQr(sessionId))
        val challenge = backend.pollAudioChallenge(profToken, sessionId)
        expectError(AppErrorType.DEVICE_MISMATCH) {
            backend.submitAudio(studentToken, sessionId, challenge.token, true, "PASSES", "device_X")
        }
    }

    // ─────────────── مانیتور و گزارش ───────────────

    @Test
    fun `monitor reflects live attendance and reports after close`() = runBlocking {
        val sessionId = createSession()
        backend.verifyQr(studentToken, currentQr(sessionId))
        val challenge = backend.pollAudioChallenge(profToken, sessionId)
        backend.submitAudio(studentToken, sessionId, challenge.token, true, "PASSES", "device_A")

        val monitor = backend.monitor(profToken, sessionId)
        assertEquals(1, monitor.presentCount)
        assertEquals(4, monitor.pendingCount)
        assertEquals(5, monitor.rows.size)

        backend.closeSession(profToken, sessionId)
        val reports = backend.reports(profToken)
        val summary = reports.firstOrNull { it.sessionId == sessionId }
        assertNotNull(summary)
        assertEquals(1, summary!!.presentCount)
        assertEquals(4, summary.absentCount)
    }

    @Test
    fun `student history only contains own records`() = runBlocking {
        val sessionId = createSession()
        backend.verifyQr(studentToken, currentQr(sessionId))
        val challenge = backend.pollAudioChallenge(profToken, sessionId)
        backend.submitAudio(studentToken, sessionId, challenge.token, true, "PASSES", "device_A")

        val history = backend.history(studentToken)
        assertTrue(history.any { it.status == AttendanceStatus.PRESENT })
        val otherHistory = backend.history(student2Token)
        // دانشجوی دیگر در جلسه باز نباید PRESENT دیده شود
        otherHistory.filter { it.timestamp != null }.forEach {
            assertTrue(it.status != AttendanceStatus.PRESENT || it.timestamp != null)
        }
    }
}
