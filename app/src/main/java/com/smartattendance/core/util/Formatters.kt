package com.smartattendance.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** ابزارهای نمایش زمان/تاریخ با ارقام فارسی */
object Formatters {

    private val clockFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    /** epoch millis → ساعت فارسی مثل ۱۴:۲۵ */
    fun clock(epochMs: Long): String = Fa.digits(clockFormatter.format(Instant.ofEpochMilli(epochMs)))

    /** میلی‌ثانیه → ۰۳:۲۵ برای شمارش معکوس */
    fun countdown(remainingMs: Long): String {
        val total = (remainingMs / 1000L).coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return Fa.digits("%02d:%02d".format(m, s))
    }
}
