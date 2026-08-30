package com.smartattendance.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.smartattendance.R

/**
 * فونت وزیرمتن برای پشتیبانی کامل فارسی.
 * فایل‌ها در res/font/vazirmatn_regular.ttf و vazirmatn_bold.ttf قرار دارند.
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/** تایپوگرافی Material 3 با فونت فارسی */
val SmartAttendanceTypography = Typography(
    displaySmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 11.sp),
)
