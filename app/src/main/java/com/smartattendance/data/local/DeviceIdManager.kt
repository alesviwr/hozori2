package com.smartattendance.data.local

import java.util.UUID

/**
 * Device Binding امن و سازگار با سیاست‌های Android:
 * به‌جای شناسه‌های سخت‌افزاری ممنوع (IMEI/ANDROID_ID/...)، یک UUID تصادفی
 * در اولین اجرا تولید و رمزشده نگهداری می‌شود؛ سرور این شناسه را به حساب
 * دانشجو متصل می‌کند و در ثبت حضور تطبیق می‌دهد.
 *
 * اکشن‌های کاربر (مثلاً پاک‌کردن داده اپ) شناسه را عوض می‌کند که رفتاری
 * قابل‌قبول است چون Binding مکمل است، نه عامل اصلی احراز.
 */
class DeviceIdManager(private val prefs: SecurePrefs) {

    fun getOrCreate(): String {
        prefs.getString(SecurePrefs.DEVICE_ID)?.let { return it }
        val fresh = "dev_" + UUID.randomUUID().toString().replace("-", "")
        prefs.putString(SecurePrefs.DEVICE_ID, fresh)
        return fresh
    }
}
