package com.smartattendance.core.util

import java.time.Instant
import java.time.ZoneId

/**
 * تبدیل میلادی به جلالی (الگوریتم استاندارد jdf — دقیق در بازه ۱۱۷۸ تا ۱۶۳۳ هجری شمسی).
 * خروجی با ارقام فارسی مثل: ۱۴۰۵/۰۶/۰۵
 */
object JalaliDate {

    fun format(epochMs: Long): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        val (jy, jm, jd) = gregorianToJalali(date.year, date.monthValue, date.dayOfMonth)
        return Fa.digits("%04d/%02d/%02d".format(jy, jm, jd))
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gYear = gy
        val jyBase = if (gYear <= 1600) 0 else 979
        gYear -= if (gYear <= 1600) 621 else 1600
        val gYear2 = if (gm > 2) gYear + 1 else gYear
        var days = 365 * gYear + (gYear2 + 3) / 4 - (gYear2 + 99) / 100 +
            (gYear2 + 399) / 400 - 80 + gd + gDaysInMonth[gm - 1]

        var jy = jyBase + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        jy += (days - 1) / 365
        if (days > 365) days = (days - 1) % 365

        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + (if (days < 186) days % 31 else (days - 186) % 30)
        return Triple(jy, jm, jd)
    }
}
