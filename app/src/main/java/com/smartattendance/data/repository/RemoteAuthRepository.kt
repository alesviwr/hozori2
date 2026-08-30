package com.smartattendance.data.repository

import com.smartattendance.core.util.AppErrorType
import com.smartattendance.core.util.AppException
import com.smartattendance.data.local.DeviceIdManager
import com.smartattendance.data.local.StoredUser
import com.smartattendance.data.local.TokenStorage
import com.smartattendance.data.remote.api.AttendanceApi
import com.smartattendance.data.remote.dto.LoginRequestDto
import com.smartattendance.data.remote.dto.RegisterRequestDto
import com.smartattendance.data.remote.dto.RegisterDeviceDto
import com.smartattendance.domain.model.Role
import com.smartattendance.domain.model.User
import com.smartattendance.domain.repository.AuthRepository
import com.smartattendance.domain.repository.LoginResult
import com.smartattendance.domain.repository.mapThrowable
import kotlinx.coroutines.flow.Flow
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

/**
 * پیاده‌سازی واقعی احراز هویت با Retrofit.
 * با BuildConfig.USE_MOCK_BACKEND=false فعال می‌شود — بدون تغییر UI.
 */
@Singleton
class RemoteAuthRepository @Inject constructor(
    private val api: AttendanceApi,
    private val tokenStorage: TokenStorage,
    private val deviceIdManager: DeviceIdManager,
) : AuthRepository {

    override suspend fun login(email: String, password: String): LoginResult = try {
        val response = api.login(
            LoginRequestDto(
                email = email.trim(),
                password = password,
                deviceId = deviceIdManager.getOrCreate(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            ),
        )
        tokenStorage.saveSession(
            token = response.token,
            user = StoredUser(
                id = response.user.id,
                name = response.user.name,
                email = response.user.email,
                role = response.user.role,
                studentNumber = response.user.studentNumber,
            ),
            refreshToken = response.refreshToken,
        )
        LoginResult(User(response.user.id, response.user.name, response.user.email, Role.valueOf(response.user.role), response.user.studentNumber))
    } catch (t: Throwable) {
        throw AppException(mapThrowable(t))
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        role: Role,
        studentNumber: String?,
    ) = try {
        api.register(
            RegisterRequestDto(
                name = name.trim(),
                email = email.trim(),
                password = password,
                role = role.name,
                studentNumber = studentNumber?.trim()?.ifBlank { null },
                deviceId = deviceIdManager.getOrCreate(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            ),
        )
    } catch (t: Throwable) {
        throw AppException(mapThrowable(t))
    }

    override suspend fun currentUser(): User? {
        val stored = tokenStorage.currentUser() ?: return null
        // اعتبار Token روی سرور با اولین Request واقعی سنجیده می‌شود؛
        // اینجا فقط وجود نشست کافی است (Splash سریع می‌ماند).
        return runCatching { stored.toDomain() }.getOrNull()
    }

    override suspend fun logout() {
        tokenStorage.clear()
        tokenStorage.emitLogout()
    }

    override suspend fun registerDevice() = try {
        api.registerDevice(RegisterDeviceDto(deviceId = deviceIdManager.getOrCreate(), deviceModel = Build.MODEL))
    } catch (t: Throwable) {
        throw AppException(mapThrowable(t))
    }

    override val logoutEvents: Flow<Unit> get() = tokenStorage.logoutEvents
}
