package com.smartattendance.di

import android.content.Context
import com.smartattendance.BuildConfig
import com.smartattendance.core.util.AppErrorType
import com.smartattendance.core.util.AppException
import com.smartattendance.data.local.TokenStorage
import com.smartattendance.data.remote.api.AttendanceApi
import com.smartattendance.data.remote.dto.RefreshRequestDto
import com.smartattendance.data.repository.MockAuthRepository
import com.smartattendance.data.repository.MockProfessorRepository
import com.smartattendance.data.repository.MockStudentRepository
import com.smartattendance.data.repository.RemoteAuthRepository
import com.smartattendance.data.repository.RemoteProfessorRepository
import com.smartattendance.data.repository.RemoteStudentRepository
import com.smartattendance.domain.repository.AuthRepository
import com.smartattendance.domain.repository.ProfessorRepository
import com.smartattendance.domain.repository.StudentRepository
import com.smartattendance.security.integrity.IntegrityChecker
import com.smartattendance.security.integrity.MockIntegrityChecker
import com.smartattendance.security.integrity.PlayIntegrityChecker
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** نشانگر حالت Backend شبیه‌سازی‌شده */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UseMockBackend

/** Interceptor افزودن هدر Authorization + رفرش خودکار توکن منقضی‌شده (با Lazy برای شکستن وابستگی) */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStorage: Lazy<TokenStorage>,
    private val api: Lazy<AttendanceApi>,
) : Interceptor {

    private val refreshing = java.util.concurrent.atomic.AtomicBoolean(false)
    private val mutex = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        val storage = tokenStorage.get()
        var token = storage.accessToken()

        if (token != null && isExpired(token) && refreshing.compareAndSet(false, true)) {
            try {
                synchronized(mutex) {
                    val current = storage.accessToken()
                    if (current != null && isExpired(current)) {
                        val refresh = storage.refreshToken()
                        if (refresh != null) {
                            runCatching {
                                kotlinx.coroutines.runBlocking { api.get().refresh(RefreshRequestDto(refresh)) }
                            }.onSuccess { resp ->
                                storage.updateTokens(resp.token, resp.refreshToken)
                            }
                        }
                    }
                    token = storage.accessToken()
                }
            } finally {
                refreshing.set(false)
            }
        }

        token?.let { builder.header("Authorization", "Bearer $it") }
        return chain.proceed(builder.build())
    }

    private fun isExpired(jwt: String): Boolean {
        val exp = runCatching {
            val payload = jwt.split(".")[1]
            val json = String(java.util.Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
            org.json.JSONObject(json).optLong("exp", 0L)
        }.getOrDefault(0L)
        return exp > 0 && exp * 1000L < System.currentTimeMillis() + 30_000L
    }
}

/** تبدیل بدنهٔ خطای سرور به ساختار استاندارد {"error": CODE} — بدون پرتاب استثنا (پرتاب از interceptor باعث کرش اپ می‌شود) */
@Singleton
class ErrorMappingInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) return response

        val code = try {
            val body = JSONObject(response.peekBody(1024).string())
            body.optString("error").ifBlank { body.optString("code") }.ifBlank { "SERVER_ERROR" }
        } catch (_: Exception) {
            "SERVER_ERROR"
        }
        val normalized = JSONObject().put("error", code).toString()
            .toResponseBody("application/json".toMediaType())
        return response.newBuilder().body(normalized).build()
    }
}

/**
 * سوییچ خودکار بین پیاده‌سازی Mock و Remote بر اساس BuildConfig.USE_MOCK_BACKEND.
 * اتصال Backend واقعی = false کردن این فلگ در app/build.gradle.kts — بدون تغییر UI.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    @UseMockBackend
    fun provideUseMockFlag(): Boolean = BuildConfig.USE_MOCK_BACKEND

    @Provides
    @Singleton
    fun provideAuthRepository(
        @UseMockBackend useMock: Boolean,
        mock: MockAuthRepository,
        remote: RemoteAuthRepository,
    ): AuthRepository = if (useMock) mock else remote

    @Provides
    @Singleton
    fun provideProfessorRepository(
        @UseMockBackend useMock: Boolean,
        mock: MockProfessorRepository,
        remote: RemoteProfessorRepository,
    ): ProfessorRepository = if (useMock) mock else remote

    @Provides
    @Singleton
    fun provideStudentRepository(
        @UseMockBackend useMock: Boolean,
        mock: MockStudentRepository,
        remote: RemoteStudentRepository,
    ): StudentRepository = if (useMock) mock else remote

    @Provides
    @Singleton
    fun provideIntegrityChecker(
        @UseMockBackend useMock: Boolean,
        mockChecker: MockIntegrityChecker,
        playChecker: Lazy<PlayIntegrityChecker>,
    ): IntegrityChecker = if (useMock) mockChecker else playChecker.get()

    @Provides
    @Singleton
    fun provideMockIntegrityChecker(): MockIntegrityChecker = MockIntegrityChecker()

    @Provides
    @Singleton
    fun providePlayIntegrityChecker(
        @ApplicationContext context: Context,
        api: AttendanceApi,
    ): PlayIntegrityChecker = PlayIntegrityChecker(context, api)
}
