package com.smartattendance.data.mock

import com.smartattendance.core.util.AppErrorType
import com.smartattendance.core.util.AppException
import com.smartattendance.core.util.JalaliDate
import com.smartattendance.domain.model.AttendanceRecord
import com.smartattendance.domain.model.AttendanceSession
import com.smartattendance.domain.model.AttendanceStatus
import com.smartattendance.domain.model.Course
import com.smartattendance.domain.model.CourseToday
import com.smartattendance.domain.model.DashboardData
import com.smartattendance.domain.model.IntegrityVerdict
import com.smartattendance.domain.model.MonitorData
import com.smartattendance.domain.repository.QrVerification
import com.smartattendance.domain.model.ReportSummary
import com.smartattendance.domain.model.Role
import com.smartattendance.domain.model.StudentAttendanceItem
import com.smartattendance.domain.model.StudentHomeData
import com.smartattendance.domain.model.StudentRow
import com.smartattendance.domain.model.User
import com.smartattendance.security.ReplayGuard
import com.smartattendance.security.TokenSigner
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import kotlin.random.Random

/**
 * ═══════════════════════════════════════════════════════════════════
 *  Mock Backend — شبیه‌ساز کامل سرور با «همان قواعد امنیتی تولید»
 * ═══════════════════════════════════════════════════════════════════
 *
 *  • توکن QR با HMAC-SHA256 امضا می‌شود (کلید فقط همینجا / روی سرور است)
 *  • QR هر ۳ ثانیه به‌صورت Canonical روی سرور Rotate می‌شود
 *  • Audio Challenge هر ۱۲ ثانیه عوض و Session-bound می‌ماند
 *  • ReplayGuard مصرف هر عامل را به‌ازای هر دانشجو یک‌بار محدود می‌کند
 *  • UNIQUE(sessionId, studentId) با کلید «session:student» در Map تضمین شده
 *  • Role همیشه سرور تعیین می‌کند؛ کلاینت نقشی نمی‌فرستد
 *
 *  در تولید، همین منطق باید در Backend واقعی (Ktor/Spring/...) پیاده شود.
 */
class MockBackend(
    private val simulateLatency: Boolean = true,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val signer = TokenSigner(ByteArray(32).also { SecureRandom().nextBytes(it) })
    private val qrReplayGuard = ReplayGuard(now)
    private val audioReplayGuard = ReplayGuard(now)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    // ───────────────────────── State ─────────────────────────

    private val users = LinkedHashMap<String, MockUser>()
    private val courses = LinkedHashMap<String, Course>()
    private val deviceBindings = HashMap<String, String>()   // userId → deviceId
    private val sessions = LinkedHashMap<String, AttendanceSession>()
    private val currentQr = HashMap<String, QrTokenRecord>() // sessionId → توکن گردش فعلی
    private val challenges = HashMap<String, MutableList<ChallengeRecord>>() // sessionId → چالش‌های اخیر
    private val pending = HashMap<String, PendingAttendance>()   // "session:student" → QR تأییدشده
    private val attendance = LinkedHashMap<String, AttendanceRecord>() // UNIQUE(sessionId:studentId)

    // ───────────────────────── Seed ──────────────────────────

    init {
        fun user(id: String, name: String, email: String, pass: String, role: Role, number: String?) {
            users[id] = MockUser(User(id, name, email, role, number), pass)
        }
        user("p1", "دکتر علی محمدی", "prof@uni.edu", "12345678", Role.PROFESSOR, null)
        user("s1", "علی رضایی", "ali@uni.edu", "12345678", Role.STUDENT, "40112233")
        user("s2", "سارا احمدی", "sara@uni.edu", "12345678", Role.STUDENT, "40112234")
        user("s3", "رضا کریمی", "reza@uni.edu", "12345678", Role.STUDENT, "40112235")
        user("s4", "مریم حسینی", "maryam@uni.edu", "12345678", Role.STUDENT, "40112236")
        user("s5", "حسین نوری", "hossein@uni.edu", "12345678", Role.STUDENT, "40112237")

        courses["c1"] = Course("c1", "ساختمان داده", "مهندسی", "204")
        courses["c2"] = Course("c2", "شبکه‌های کامپیوتری", "مهندسی", "310")
        courses["c3"] = Course("c3", "سیستم عامل", "مهندسی", "112")

        // جلسات بسته‌شده تاریخی برای گزارش‌ها و تاریخچه
        seedClosedSession("h1", "c1", daysAgo = 1, presentIds = listOf("s1", "s2", "s3", "s4"))
        seedClosedSession("h2", "c2", daysAgo = 2, presentIds = listOf("s1", "s3"))
        seedClosedSession("h3", "c3", daysAgo = 4, presentIds = listOf("s2", "s5"))
    }

    private fun seedClosedSession(id: String, courseId: String, daysAgo: Int, presentIds: List<String>) {
        val startedAt = now() - daysAgo * 24L * 60L * 60L * 1000L
        val course = courses.getValue(courseId)
        sessions[id] = AttendanceSession(
            id = id, courseId = course.id, courseName = course.name, professorId = "p1",
            building = course.building, room = course.room,
            startedAt = startedAt, expiresAt = startedAt + 300_000, windowSeconds = 300,
            status = com.smartattendance.domain.model.SessionStatus.CLOSED,
        )
        presentIds.forEachIndexed { index, studentId ->
            val u = users.getValue(studentId)
            attendance["$id:$studentId"] = AttendanceRecord(
                sessionId = id, studentId = studentId, studentName = u.user.name,
                timestamp = startedAt + (index + 1) * 45_000L, status = AttendanceStatus.PRESENT,
                qrVerified = true, biometricVerified = true, audioVerified = true, deviceId = "seeded",
            )
        }
    }

    // ─────────────────────── Auth / Token ───────────────────────

    data class MockAuthData(val token: String, val user: User)

    @Serializable
    private data class Claims(val sub: String, val role: String, val exp: Long)

    suspend fun login(email: String, password: String, deviceId: String?): MockAuthData = locked {
        val u = users.values.firstOrNull { it.user.email.equals(email.trim(), ignoreCase = true) }
            ?: throw AppException(AppErrorType.INVALID_CREDENTIALS)
        if (u.password != password) throw AppException(AppErrorType.INVALID_CREDENTIALS)
        if (u.user.role == Role.STUDENT && !deviceId.isNullOrBlank()) {
            deviceBindings[u.user.id] = deviceId
        }
        MockAuthData(issueAccessToken(u), u.user)
    }

    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: Role,
        studentNumber: String?,
        deviceId: String?,
    ): MockAuthData = locked {
        val normalized = email.trim().lowercase()
        if (name.isBlank() || normalized.isBlank() || password.length < 6) {
            throw AppException(AppErrorType.INVALID_CREDENTIALS)
        }
        if (users.values.any { it.user.email.equals(normalized, ignoreCase = true) }) {
            throw AppException(AppErrorType.EMAIL_TAKEN)
        }
        val user = User(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            email = normalized,
            role = role,
            studentNumber = studentNumber?.takeIf { role == Role.STUDENT },
        )
        users[user.id] = MockUser(user, password)
        if (role == Role.STUDENT && !deviceId.isNullOrBlank()) {
            deviceBindings[user.id] = deviceId
        }
        MockAuthData(issueAccessToken(users[user.id]!!), user)
    }

    suspend fun registerDevice(token: String, deviceId: String): Unit = locked {
        val u = requireUser(token)
        deviceBindings[u.user.id] = deviceId
    }

    suspend fun currentUser(token: String): User = locked { requireUser(token).user }

    suspend fun integrity(token: String, nonce: String): String = locked {
        requireUser(token)
        "PASSES" // در تولید: سرور توکن Play Integrity را با Google verify می‌کند
    }

    private fun issueAccessToken(u: MockUser): String {
        val claims = Claims(u.user.id, u.user.role.name, now() + ACCESS_TTL_MS)
        val payload = Base64.getEncoder().encodeToString(json.encodeToString(claims).toByteArray(Charsets.UTF_8))
        return "$TOKEN_PREFIX.$payload.${signer.sign(payload)}"
    }

    private fun requireUser(token: String?): MockUser {
        if (token.isNullOrBlank()) throw AppException(AppErrorType.UNAUTHORIZED)
        val parts = token.split(".")
        if (parts.size != 3 || parts[0] != TOKEN_PREFIX) throw AppException(AppErrorType.UNAUTHORIZED)
        if (!signer.verify(parts[1], parts[2])) throw AppException(AppErrorType.UNAUTHORIZED)
        val claims = try {
            json.decodeFromString<Claims>(String(Base64.getDecoder().decode(parts[1]), Charsets.UTF_8))
        } catch (_: Exception) {
            throw AppException(AppErrorType.UNAUTHORIZED)
        }
        if (claims.exp < now()) throw AppException(AppErrorType.UNAUTHORIZED)
        return users[claims.sub] ?: throw AppException(AppErrorType.UNAUTHORIZED)
    }

    private fun requireProfessor(token: String?): MockUser =
        requireUser(token).takeIf { it.user.role == Role.PROFESSOR }
            ?: throw AppException(AppErrorType.UNAUTHORIZED)

    private fun requireStudent(token: String?): MockUser =
        requireUser(token).takeIf { it.user.role == Role.STUDENT }
            ?: throw AppException(AppErrorType.UNAUTHORIZED)

    // ─────────────────────── Professor ───────────────────────

    suspend fun dashboard(token: String): DashboardData = locked {
        val prof = requireProfessor(token)
        refreshSessions()
        val active = sessions.values.firstOrNull {
            it.professorId == prof.user.id && it.status == com.smartattendance.domain.model.SessionStatus.ACTIVE
        }
        val present = active?.let { countPresent(it.id) } ?: 0
        val pendingCount = active?.let { rosterSize() - present } ?: 0
        DashboardData(
            professorName = prof.user.name,
            activeSession = active,
            presentCount = present,
            absentCount = 0,
            pendingCount = pendingCount,
            todayCourses = courses.values.map {
                CourseToday(it.name, "${it.building} - ${it.room}", "")
            },
            recentSessions = closedReports(prof.user.id).take(4),
        )
    }

    suspend fun getCourses(token: String): List<Course> = locked {
        requireProfessor(token)
        courses.values.toList()
    }

    suspend fun createCourse(token: String, name: String, building: String, room: String): Course = locked {
        requireProfessor(token)
        if (name.isBlank()) throw AppException(AppErrorType.UNKNOWN)
        val course = Course(
            id = "c_${randomHex(6)}",
            name = name.trim(),
            building = building.ifBlank { "-" },
            room = room.ifBlank { "-" },
        )
        courses[course.id] = course
        course
    }

    suspend fun createSession(token: String, courseId: String, building: String, room: String, windowMinutes: Int): AttendanceSession = locked {
        val prof = requireProfessor(token)
        val course = courses[courseId] ?: throw AppException(AppErrorType.UNKNOWN)
        val id = "s_${randomHex(6)}"
        val start = now()
        val session = AttendanceSession(
            id = id, courseId = course.id, courseName = course.name, professorId = prof.user.id,
            building = building.ifBlank { course.building }, room = room.ifBlank { course.room },
            startedAt = start, expiresAt = start + windowMinutes * 60_000L,
            windowSeconds = windowMinutes * 60L,
            status = com.smartattendance.domain.model.SessionStatus.ACTIVE,
        )
        sessions[id] = session
        session
    }

    suspend fun getActiveSession(token: String): AttendanceSession? = locked {
        val prof = requireProfessor(token)
        refreshSessions()
        sessions.values.firstOrNull {
            it.professorId == prof.user.id && it.status == com.smartattendance.domain.model.SessionStatus.ACTIVE
        }
    }

    /**
     * توکن QR گردشی جلسه — Canonical روی سرور:
     * تا ۳ ثانیه همان توکن برگردانده می‌شود و بعدش توکن جدید با nonce و امضای تازه ساخته می‌شود.
     */
    suspend fun pollQrToken(token: String, sessionId: String): com.smartattendance.domain.model.QrTokenData = locked {
        val prof = requireProfessor(token)
        val session = refreshAndGet(sessionId, prof.user.id)
        requireActive(session)
        val current = currentQr[sessionId]
        if (current != null && now() < current.expiresAt) {
            return@locked com.smartattendance.domain.model.QrTokenData(current.full(signer), current.expiresAt)
        }
        val issued = now()
        val record = QrTokenRecord(
            sessionId = sessionId, tokenId = randomHex(8),
            issuedAt = issued, expiresAt = issued + QR_TTL_MS, nonce = randomHex(8),
        )
        currentQr[sessionId] = record
        com.smartattendance.domain.model.QrTokenData(record.full(signer), record.expiresAt)
    }

    /** چالش صوتی فعال جلسه — کد ۸ رقمی هگز که استاد پخش می‌کند */
    suspend fun pollAudioChallenge(token: String, sessionId: String): com.smartattendance.domain.model.AudioChallengeData = locked {
        val prof = requireProfessor(token)
        val session = refreshAndGet(sessionId, prof.user.id)
        requireActive(session)
        val list = challenges.getOrPut(sessionId) { mutableListOf() }
        list.removeAll { now() > it.expiresAt }
        val current = list.maxByOrNull { it.createdAt }
        if (current != null) {
            return@locked com.smartattendance.domain.model.AudioChallengeData(current.challengeId, current.token, current.expiresAt)
        }
        val created = now()
        val record = ChallengeRecord(
            sessionId = sessionId, challengeId = randomHex(4),
            token = randomHex(8), createdAt = created, expiresAt = created + AUDIO_TTL_MS,
        )
        list.add(record)
        com.smartattendance.domain.model.AudioChallengeData(record.challengeId, record.token, record.expiresAt)
    }

    suspend fun monitor(token: String, sessionId: String): MonitorData = locked {
        val prof = requireProfessor(token)
        val session = refreshAndGet(sessionId, prof.user.id)
        val present = attendance.values.filter { it.sessionId == sessionId }.toList()
        val rows = roster().map { u ->
            val record = present.firstOrNull { it.studentId == u.user.id }
            if (record != null) {
                StudentRow(u.user.id, u.user.name, AttendanceStatus.PRESENT, record.timestamp)
            } else {
                StudentRow(u.user.id, u.user.name, AttendanceStatus.PENDING, null)
            }
        }.sortedWith(
            compareByDescending<StudentRow> { it.status == AttendanceStatus.PRESENT }.thenBy { it.studentName },
        )
        MonitorData(
            session = session,
            presentCount = present.size,
            pendingCount = rosterSize() - present.size,
            absentCount = if (session.status == com.smartattendance.domain.model.SessionStatus.CLOSED) {
                rosterSize() - present.size
            } else 0,
            rows = rows,
        )
    }

    suspend fun closeSession(token: String, sessionId: String) = locked {
        val prof = requireProfessor(token)
        val session = refreshAndGet(sessionId, prof.user.id)
        sessions[sessionId] = session.copy(status = com.smartattendance.domain.model.SessionStatus.CLOSED)
        currentQr.remove(sessionId)
        challenges.remove(sessionId)
        pending.keys.removeAll { it.startsWith("$sessionId:") }
    }

    suspend fun reports(token: String): List<ReportSummary> = locked {
        val prof = requireProfessor(token)
        closedReports(prof.user.id)
    }

    suspend fun reportDetail(token: String, sessionId: String): Pair<ReportSummary, List<StudentRow>> = locked {
        val prof = requireProfessor(token)
        val session = sessions[sessionId] ?: throw AppException(AppErrorType.UNKNOWN)
        if (session.professorId != prof.user.id) throw AppException(AppErrorType.UNAUTHORIZED)
        val present = attendance.values.filter { it.sessionId == sessionId }
        val summary = summaryOf(session, present.size)
        val rows = roster().map { u ->
            val record = present.firstOrNull { it.studentId == u.user.id }
            if (record != null) StudentRow(u.user.id, u.user.name, AttendanceStatus.PRESENT, record.timestamp)
            else StudentRow(u.user.id, u.user.name, AttendanceStatus.ABSENT, null)
        }
        summary to rows.sortedWith(compareByDescending<StudentRow> { it.status == AttendanceStatus.PRESENT }.thenBy { it.studentName })
    }

    // ───────────────────────── Student ─────────────────────────

    suspend fun studentHome(token: String): StudentHomeData = locked {
        val student = requireStudent(token)
        refreshSessions()
        val active = sessions.values.firstOrNull {
            it.status == com.smartattendance.domain.model.SessionStatus.ACTIVE
        }
        StudentHomeData(
            studentName = student.user.name,
            studentNumber = student.user.studentNumber ?: "",
            activeSession = active,
            recent = historyItems(student.user.id).take(4),
        )
    }

    /**
     * بررسی توکن QR اسکن‌شده:
     * امضا → وجود Session → وضعیت Session → انقضای توکن → Replay → ثبت مرحله QR
     */
    suspend fun verifyQr(token: String, qrPayload: String): QrVerification = locked {
        val student = requireStudent(token)
        refreshSessions() // انقضای خودکار Session قبل از بررسی

        val parts = qrPayload.trim().split("|")
        if (parts.size != 7 || parts[0] != QR_PREFIX) throw AppException(AppErrorType.QR_INVALID)
        val (sessionId, tokenId, issuedAt, expiresAt, nonce) = listOf(parts[1], parts[2], parts[3], parts[4], parts[5])
        val signature = parts[6]
        val signedPayload = "$sessionId|$tokenId|$issuedAt|$expiresAt|$nonce"
        if (!signer.verify(signedPayload, signature)) throw AppException(AppErrorType.QR_INVALID)

        val session = sessions[sessionId] ?: throw AppException(AppErrorType.QR_INVALID)
        when (session.status) {
            com.smartattendance.domain.model.SessionStatus.CLOSED -> throw AppException(AppErrorType.SESSION_CLOSED)
            com.smartattendance.domain.model.SessionStatus.EXPIRED -> throw AppException(AppErrorType.SESSION_EXPIRED)
            com.smartattendance.domain.model.SessionStatus.CREATED -> throw AppException(AppErrorType.QR_INVALID)
            com.smartattendance.domain.model.SessionStatus.ACTIVE -> Unit
        }
        if (now() > (expiresAt.toLongOrNull() ?: 0L)) throw AppException(AppErrorType.QR_EXPIRED)

        // Replay فقط برای «همین دانشجو» رد می‌شود؛ چند دانشجو می‌توانند همان QR زنده را اسکن کنند
        val consumed = qrReplayGuard.checkAndConsume("qr|$tokenId|${student.user.id}", ttlMs = 60_000)
        if (!consumed) throw AppException(AppErrorType.QR_INVALID, "این کد قبلاً توسط شما مصرف شده است.")

        pending["${sessionId}:${student.user.id}"] = PendingAttendance(sessionId, student.user.id, tokenId, now())
        QrVerification(sessionId = sessionId, courseName = session.courseName)
    }

    /**
     * تأیید نهایی حضور — همه شرط‌ها باید هم‌زمان برقرار باشند:
     * QR مرحله اول + Session فعال + عدم ثبت قبلی + چالش صوتی معتبر + بیومتریک اظهارشده + Device Binding
     */
    suspend fun submitAudio(
        token: String,
        sessionId: String,
        audioToken: String,
        biometricAttested: Boolean,
        integrityVerdict: String,
        deviceId: String,
    ): AttendanceRecord = locked {
        val student = requireStudent(token)
        val session = refreshAndGetActive(sessionId)

        val key = "$sessionId:${student.user.id}"
        if (attendance.containsKey(key)) throw AppException(AppErrorType.ALREADY_ATTENDED)

        val step = pending[key] ?: throw AppException(AppErrorType.QR_REQUIRED)
        if (step.sessionId != sessionId) throw AppException(AppErrorType.QR_INVALID)

        if (!biometricAttested) throw AppException(AppErrorType.BIOMETRIC_FAILED)

        val boundDevice = deviceBindings[student.user.id]
        if (boundDevice != null && boundDevice != deviceId) throw AppException(AppErrorType.DEVICE_MISMATCH)

        // چالش صوتی باید جزو چالش‌های زنده «همین جلسه» باشد
        val list = challenges[sessionId].orEmpty()
        val match = list.firstOrNull { it.token.equals(audioToken.trim(), ignoreCase = true) }
        when {
            match == null -> throw AppException(AppErrorType.AUDIO_INVALID)
            now() > match.expiresAt -> throw AppException(AppErrorType.CHALLENGE_EXPIRED)
        }

        val consumed = audioReplayGuard.checkAndConsume("audio|${match!!.challengeId}|${student.user.id}", ttlMs = 120_000)
        if (!consumed) throw AppException(AppErrorType.AUDIO_INVALID)

        // integrityVerdict صرفاً ثبت می‌شود؛ جایگزین سایر عوامل نیست (طبق طراحی)
        val record = AttendanceRecord(
            sessionId = sessionId, studentId = student.user.id, studentName = student.user.name,
            timestamp = now(), status = AttendanceStatus.PRESENT,
            qrVerified = true, biometricVerified = biometricAttested, audioVerified = true,
            deviceId = deviceId.ifBlank { "unknown" },
        )
        attendance[key] = record // UNIQUE(sessionId, studentId) — چون بالا reject شده
        pending.remove(key)
        record
    }

    suspend fun history(token: String): List<StudentAttendanceItem> = locked {
        val student = requireStudent(token)
        refreshSessions()
        historyItems(student.user.id)
    }

    // ─────────────────────── Internals ───────────────────────

    private fun historyItems(studentId: String): List<StudentAttendanceItem> {
        val items = mutableListOf<StudentAttendanceItem>()
        sessions.values.sortedByDescending { it.startedAt }.forEach { session ->
            val record = attendance["${session.id}:$studentId"]
            when {
                record != null && record.status == AttendanceStatus.PRESENT -> items.add(
                    StudentAttendanceItem(session.courseName, JalaliDate.format(session.startedAt), AttendanceStatus.PRESENT, record.timestamp),
                )
                session.status == com.smartattendance.domain.model.SessionStatus.CLOSED && record == null -> items.add(
                    StudentAttendanceItem(session.courseName, JalaliDate.format(session.startedAt), AttendanceStatus.ABSENT, null),
                )
            }
        }
        return items
    }

    private fun closedReports(professorId: String): List<ReportSummary> =
        sessions.values
            .filter { it.professorId == professorId && it.status == com.smartattendance.domain.model.SessionStatus.CLOSED }
            .sortedByDescending { it.startedAt }
            .map { summaryOf(it, countPresent(it.id)) }

    private fun summaryOf(session: AttendanceSession, present: Int) = ReportSummary(
        sessionId = session.id,
        courseName = session.courseName,
        date = JalaliDate.format(session.startedAt),
        startedAt = session.startedAt,
        presentCount = present,
        absentCount = rosterSize() - present,
    )

    private fun countPresent(sessionId: String): Int = attendance.keys.count { it.startsWith("$sessionId:") }

    private fun roster(): List<MockUser> = users.values.filter { it.user.role == Role.STUDENT }
    private fun rosterSize(): Int = roster().size

    /** انقضای خودکار جلسات ACTIVE گذشته از window */
    private fun refreshSessions() {
        val expired = sessions.values.filter {
            it.status == com.smartattendance.domain.model.SessionStatus.ACTIVE && now() > it.expiresAt
        }
        expired.forEach { sessions[it.id] = it.copy(status = com.smartattendance.domain.model.SessionStatus.EXPIRED) }
    }

    private fun refreshAndGet(sessionId: String, professorId: String): AttendanceSession {
        refreshSessions()
        val session = sessions[sessionId] ?: throw AppException(AppErrorType.UNKNOWN)
        if (session.professorId != professorId) throw AppException(AppErrorType.UNAUTHORIZED)
        return session
    }

    private fun refreshAndGetActive(sessionId: String): AttendanceSession {
        refreshSessions()
        val session = sessions[sessionId] ?: throw AppException(AppErrorType.QR_INVALID)
        return when (session.status) {
            com.smartattendance.domain.model.SessionStatus.ACTIVE -> session
            com.smartattendance.domain.model.SessionStatus.EXPIRED -> throw AppException(AppErrorType.SESSION_EXPIRED)
            com.smartattendance.domain.model.SessionStatus.CLOSED -> throw AppException(AppErrorType.SESSION_CLOSED)
            com.smartattendance.domain.model.SessionStatus.CREATED -> throw AppException(AppErrorType.QR_INVALID)
        }
    }

    private fun requireActive(session: AttendanceSession): AttendanceSession = when (session.status) {
        com.smartattendance.domain.model.SessionStatus.ACTIVE -> session
        com.smartattendance.domain.model.SessionStatus.EXPIRED -> throw AppException(AppErrorType.SESSION_EXPIRED)
        com.smartattendance.domain.model.SessionStatus.CLOSED -> throw AppException(AppErrorType.SESSION_CLOSED)
        com.smartattendance.domain.model.SessionStatus.CREATED -> throw AppException(AppErrorType.QR_INVALID)
    }

    private suspend fun <T> locked(block: suspend () -> T): T {
        lag()
        return mutex.withLock { block() }
    }

    private suspend fun lag() {
        if (simulateLatency) delay(Random.nextLong(70, 220))
    }

    private fun randomHex(length: Int): String =
        (1..length).map { "0123456789ABCDEF"[Random.nextInt(16)] }.joinToString("")

    // ─────────────────────── Data holders ───────────────────────

    data class MockUser(val user: User, val password: String)

    data class PendingAttendance(
        val sessionId: String,
        val studentId: String,
        val qrTokenId: String,
        val verifiedAt: Long,
    )

    data class ChallengeRecord(
        val sessionId: String,
        val challengeId: String,
        val token: String,
        val createdAt: Long,
        val expiresAt: Long,
    )

    data class QrTokenRecord(
        val sessionId: String,
        val tokenId: String,
        val issuedAt: Long,
        val expiresAt: Long,
        val nonce: String,
    ) {
        val signaturePayload: String get() = "$sessionId|$tokenId|$issuedAt|$expiresAt|$nonce"
        fun full(signer: TokenSigner): String =
            "$QR_PREFIX|$sessionId|$tokenId|$issuedAt|$expiresAt|$nonce|${signer.sign(signaturePayload)}"
    }

    companion object {
        private const val TOKEN_PREFIX = "sat"
        private const val QR_PREFIX = "AT"
        const val QR_TTL_MS = 3_000L
        const val AUDIO_TTL_MS = 12_000L
        const val ACCESS_TTL_MS = 12L * 60 * 60 * 1000
    }
}
