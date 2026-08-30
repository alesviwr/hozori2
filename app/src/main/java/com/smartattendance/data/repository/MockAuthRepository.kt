package com.smartattendance.data.repository

import com.smartattendance.core.util.AppErrorType
import com.smartattendance.core.util.AppException
import com.smartattendance.data.local.DeviceIdManager
import com.smartattendance.data.local.StoredUser
import com.smartattendance.data.local.TokenStorage
import com.smartattendance.data.mock.MockBackend
import com.smartattendance.domain.model.Role
import com.smartattendance.domain.model.User
import com.smartattendance.domain.repository.AuthRepository
import com.smartattendance.domain.repository.LoginResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** پیاده‌سازی Mock احراز هویت — با Backend درون‌اپ */
@Singleton
class MockAuthRepository @Inject constructor(
    private val backend: MockBackend,
    private val tokenStorage: TokenStorage,
    private val deviceIdManager: DeviceIdManager,
) : AuthRepository {

    override suspend fun login(email: String, password: String): LoginResult {
        val result = backend.login(email, password, deviceIdManager.getOrCreate())
        tokenStorage.saveSession(
            token = result.token,
            user = StoredUser(
                id = result.user.id,
                name = result.user.name,
                email = result.user.email,
                role = result.user.role.name,
                studentNumber = result.user.studentNumber,
            ),
        )
        return LoginResult(result.user)
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        role: Role,
        studentNumber: String?,
    ) {
        val result = backend.register(name, email, password, role, studentNumber, deviceIdManager.getOrCreate())
        tokenStorage.saveSession(
            token = result.token,
            user = StoredUser(
                id = result.user.id,
                name = result.user.name,
                email = result.user.email,
                role = result.user.role.name,
                studentNumber = result.user.studentNumber,
            ),
        )
    }

    override suspend fun currentUser(): User? {
        val stored = tokenStorage.currentUser() ?: return null
        val token = tokenStorage.accessToken() ?: return null
        return try {
            val serverUser = backend.currentUser(token)
            User(
                id = serverUser.id,
                name = serverUser.name,
                email = serverUser.email,
                role = Role.valueOf(stored.role),
                studentNumber = serverUser.studentNumber,
            )
        } catch (_: AppException) {
            null
        }
    }

    override suspend fun logout() {
        tokenStorage.clear()
        tokenStorage.emitLogout()
    }

    override suspend fun registerDevice() {
        val token = tokenStorage.accessToken() ?: throw AppException(AppErrorType.UNAUTHORIZED)
        backend.registerDevice(token, deviceIdManager.getOrCreate())
    }

    override val logoutEvents: Flow<Unit> get() = tokenStorage.logoutEvents
}

/** استخراج Token نشست برای Repositoryهای دیگر */
internal fun TokenStorage.requireToken(): String =
    accessToken() ?: throw AppException(AppErrorType.UNAUTHORIZED)

internal fun StoredUser.toDomain() = User(
    id = id, name = name, email = email,
    role = Role.valueOf(role), studentNumber = studentNumber,
)
