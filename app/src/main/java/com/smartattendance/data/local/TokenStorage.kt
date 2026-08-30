package com.smartattendance.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** اطلاعات کاربر ذخیره‌شده روی دستگاه (رمزشده توسط SecurePrefs) */
@Serializable
data class StoredUser(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val studentNumber: String? = null,
)

/**
 * نگهداری امن Token نشست و اطلاعات کاربر.
 * Token هرگز Plain Text ذخیره نمی‌شود و در هر Request از اینجا خوانده می‌شود.
 */
class TokenStorage(private val prefs: SecurePrefs, private val json: Json) {

    private val _logoutEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutEvents: SharedFlow<Unit> = _logoutEvents.asSharedFlow()

    fun saveSession(token: String, user: StoredUser, refreshToken: String? = null) {
        prefs.putString(SecurePrefs.ACCESS_TOKEN, token)
        if (refreshToken != null) prefs.putString(SecurePrefs.REFRESH_TOKEN, refreshToken)
        prefs.putString(SecurePrefs.USER_JSON, json.encodeToString(StoredUser.serializer(), user))
    }

    fun accessToken(): String? = prefs.getString(SecurePrefs.ACCESS_TOKEN)

    fun refreshToken(): String? = prefs.getString(SecurePrefs.REFRESH_TOKEN)

    fun updateTokens(token: String, refreshToken: String?) {
        prefs.putString(SecurePrefs.ACCESS_TOKEN, token)
        if (refreshToken != null) prefs.putString(SecurePrefs.REFRESH_TOKEN, refreshToken)
    }

    fun currentUser(): StoredUser? = prefs.getString(SecurePrefs.USER_JSON)?.let { raw ->
        try {
            json.decodeFromString(StoredUser.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        prefs.remove(SecurePrefs.ACCESS_TOKEN)
        prefs.remove(SecurePrefs.REFRESH_TOKEN)
        prefs.remove(SecurePrefs.USER_JSON)
    }

    fun emitLogout() = _logoutEvents.tryEmit(Unit)
}
