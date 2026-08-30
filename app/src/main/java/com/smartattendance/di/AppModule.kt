package com.smartattendance.di

import android.content.Context
import com.smartattendance.BuildConfig
import com.smartattendance.data.local.AppDatabase
import com.smartattendance.data.local.CachedReportDao
import com.smartattendance.data.local.DeviceIdManager
import com.smartattendance.data.local.SecurePrefs
import com.smartattendance.data.local.TokenStorage
import com.smartattendance.data.mock.MockBackend
import com.smartattendance.data.remote.api.AttendanceApi
import com.smartattendance.security.keystore.CryptoManager
import com.smartattendance.audio.MicRecorder
import com.smartattendance.audio.TonePlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * ماژول اصلی DI:
 * • Storage امن (Keystore + EncryptedPrefs)
 * • MockBackend (سرور شبیه‌سازی‌شده)
 * • Retrofit/OkHttp (برای Backend واقعی)
 * • Room (کش محلی)
 * • موتور صوتی
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ───────── Storage امن ─────────

    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager = CryptoManager()

    @Provides
    @Singleton
    fun provideSecurePrefs(@ApplicationContext context: Context, crypto: CryptoManager): SecurePrefs =
        SecurePrefs(context, crypto)

    @Provides
    @Singleton
    fun provideTokenStorage(prefs: SecurePrefs): TokenStorage =
        TokenStorage(prefs, Json { ignoreUnknownKeys = true })

    @Provides
    @Singleton
    fun provideDeviceIdManager(prefs: SecurePrefs): DeviceIdManager = DeviceIdManager(prefs)

    // ───────── Mock Backend ─────────

    @Provides
    @Singleton
    fun provideMockBackend(): MockBackend = MockBackend(simulateLatency = true)

    // ───────── Room ─────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, "smart_attendance.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCachedReportDao(db: AppDatabase): CachedReportDao = db.cachedReportDao()

    // ───────── موتور صوتی ─────────

    @Provides
    @Singleton
    fun provideTonePlayer(): TonePlayer = TonePlayer()

    @Provides
    @Singleton
    fun provideMicRecorder(): MicRecorder = MicRecorder()
}

/** شبکه Retrofit — حتی در حالت Mock ساخته می‌شود ولی فراخوانی نمی‌شود */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(auth: AuthInterceptor, errors: ErrorMappingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(auth)
            .addInterceptor(errors)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAttendanceApi(retrofit: Retrofit): AttendanceApi =
        retrofit.create(AttendanceApi::class.java)
}
