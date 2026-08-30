package com.smartattendance.core.util

/**
 * انواع خطای سیستم — قرارداد مشترک Client/Server.
 * هر نوع خطا پیام فارسی قابل فهم برای کاربر دارد.
 */
enum class AppErrorType {
    QR_EXPIRED,
    QR_INVALID,
    QR_REQUIRED,
    SESSION_EXPIRED,
    SESSION_CLOSED,
    BIOMETRIC_FAILED,
    BIOMETRIC_UNAVAILABLE,
    AUDIO_TIMEOUT,
    AUDIO_INVALID,
    MIC_UNAVAILABLE,
    CHALLENGE_EXPIRED,
    ALREADY_ATTENDED,
    BACKGROUND_DETECTED,
    NETWORK_ERROR,
    SERVER_ERROR,
    INVALID_CREDENTIALS,
    EMAIL_TAKEN,
    DEVICE_MISMATCH,
    UNAUTHORIZED,
    UNKNOWN,
}

/** استثنای استاندارد دامنه — از Backend/Mock و Repositoryها پرتاب می‌شود */
class AppException(val type: AppErrorType, override val message: String? = null) : Exception(message)

fun AppErrorType.persianMessage(): String = when (this) {
    AppErrorType.QR_EXPIRED -> "کد QR منقضی شده است؛ دوباره اسکن کنید."
    AppErrorType.QR_INVALID -> "کد QR نامعتبر است."
    AppErrorType.QR_REQUIRED -> "ابتدا کد QR جلسه را اسکن کنید."
    AppErrorType.SESSION_EXPIRED -> "زمان حضور و غیاب این جلسه به پایان رسیده است."
    AppErrorType.SESSION_CLOSED -> "جلسه توسط استاد پایان یافته است."
    AppErrorType.BIOMETRIC_FAILED -> "تأیید بیومتریک ناموفق بود."
    AppErrorType.BIOMETRIC_UNAVAILABLE -> "احراز هویت بیومتریک روی این دستگاه در دسترس نیست."
    AppErrorType.AUDIO_TIMEOUT -> "زمان شنیدن Challenge به پایان رسید. دوباره تلاش کنید."
    AppErrorType.AUDIO_INVALID -> "توکن صوتی نامعتبر است."
    AppErrorType.MIC_UNAVAILABLE -> "به میکروفون دسترسی نیست. مجوز میکروفون را برای برنامه فعال کنید یا مطمئن شوید برنامه‌ی دیگری از میکروفون استفاده نمی‌کند."
    AppErrorType.CHALLENGE_EXPIRED -> "این Challenge منقضی شده است؛ از استاد بخواهید مجدداً پخش شود."
    AppErrorType.ALREADY_ATTENDED -> "حضور شما قبلاً ثبت شده است."
    AppErrorType.BACKGROUND_DETECTED -> "خروج از صفحه باعث لغو Challenge شد. فرآیند را از نو شروع کنید."
    AppErrorType.NETWORK_ERROR -> "خطای شبکه؛ اتصال اینترنت را بررسی کنید. اگر فیلترشکن دارید، آن را روشن کنید یا عوضش کنید."
    AppErrorType.SERVER_ERROR -> "خطای سرور؛ کمی بعد دوباره تلاش کنید."
    AppErrorType.INVALID_CREDENTIALS -> "ایمیل یا رمز عبور نادرست است."
    AppErrorType.EMAIL_TAKEN -> "این ایمیل قبلاً ثبت‌نام کرده است."
    AppErrorType.DEVICE_MISMATCH -> "این حساب به دستگاه دیگری متصل است."
    AppErrorType.UNAUTHORIZED -> "نشست شما معتبر نیست؛ دوباره وارد شوید."
    AppErrorType.UNKNOWN -> "خطای ناشناخته رخ داد."
}
