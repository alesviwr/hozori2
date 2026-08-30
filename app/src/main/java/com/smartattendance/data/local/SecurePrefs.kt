package com.smartattendance.data.local

import android.content.Context
import android.content.SharedPreferences
import com.smartattendance.security.keystore.CryptoManager

/**
 * SharedPreferences با مقادیر رمزشده توسط Android Keystore (AES-256-GCM).
 * هیچ Secret ای به‌صورت Plain Text روی دیسک نوشته نمی‌شود.
 */
class SecurePrefs(context: Context, private val crypto: CryptoManager) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_attendance_secure", Context.MODE_PRIVATE)

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, crypto.encryptString(value)).apply()
    }

    fun getString(key: String): String? = prefs.getString(key, null)?.let {
        try {
            crypto.decryptString(it)
        } catch (_: Exception) {
            null // کلید عوض شده یا داده خراب — معادل نبودن مقدار
        }
    }

    fun remove(key: String) = prefs.edit().remove(key).apply()

    fun clear() = prefs.edit().clear().apply()

    companion object Keys {
        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val USER_JSON = "user_json"
        const val DEVICE_ID = "device_id"
    }
}
