package com.smartattendance.core.util

/**
 * مرکز تمام متن‌های UI به زبان فارسی + ابزار تبدیل ارقام.
 * (برای دوزبانه‌شدن بعدی کافی است این آبجکت به Resource مپ شود)
 */
object Fa {
    val faDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /** تبدیل ارقام لاتین به فارسی */
    fun digits(value: Int): String = digits(value.toString())
    fun digits(value: Long): String = digits(value.toString())
    fun digits(value: String): String = buildString {
        for (c in value) append(if (c in '0'..'9') faDigits[c - '0'] else c)
    }

    // ---------- عمومی ----------
    const val APP_NAME = "حضور و غیاب هوشمند"
    const val LOGIN = "ورود"
    const val LOGOUT = "خروج"
    const val RETRY = "تلاش مجدد"
    const val BACK = "بازگشت"
    const val EMAIL = "ایمیل / شماره دانشجویی"
    const val PASSWORD = "رمز عبور"
    const val LOADING = "کمی صبر کنید..."
    const val PRESENT = "حاضر"
    const val ABSENT = "غایب"
    const val PENDING = "در انتظار"
    const val FAILED = "ناموفق"
    const val COURSE = "درس"
    const val CLASS = "کلاس"
    const val BUILDING = "ساختمان"
    const val ROOM = "شماره کلاس"
    const val DATE = "تاریخ"
    const val TIME = "ساعت"
    const val STATUS = "وضعیت"

    // ---------- اسپلش / لاگین ----------
    const val LOGIN_TITLE = "ورود به سامانه"
    const val LOGIN_SUBTITLE = "برای ادامه وارد حساب کاربری خود شوید"
    const val LOGIN_HINT = "حساب آزمایشی استاد: prof@uni.edu / 12345678"
    const val NO_ACCOUNT = "حساب کاربری ندارید؟"
    const val REGISTER = "ثبت‌نام"

    // ---------- ثبت‌نام ----------
    const val REGISTER_TITLE = "ساخت حساب جدید"
    const val REGISTER_SUBTITLE = "اطلاعات خود را وارد کنید"
    const val FULL_NAME = "نام و نام خانوادگی"
    const val STUDENT_NUMBER = "شماره دانشجویی"
    const val CHOOSE_ROLE = "نقش خود را انتخاب کنید"
    const val ROLE_PROFESSOR = "استاد"
    const val ROLE_STUDENT = "دانشجو"
    const val HAVE_ACCOUNT = "قبلاً ثبت‌نام کرده‌اید؟ وارد شوید"
    const val PASSWORD_SHORT = "رمز عبور باید حداقل ۶ کاراکتر باشد"
    const val REGISTER_DONE = "ثبت‌نام با موفقیت انجام شد"

    // ---------- داشبورد استاد ----------
    const val PROF_GREETING = "سلام استاد 👋"
    const val TODAY_COURSES = "کلاس‌های امروز"
    const val ACTIVE_SESSIONS = "جلسه فعال"
    const val NO_ACTIVE_SESSION = "جلسه فعالی وجود ندارد"
    const val START_ATTENDANCE = "شروع حضور و غیاب"
    const val RESUME_ATTENDANCE = "ادامه جلسه فعال"
    const val RECENT_SESSIONS = "جلسات اخیر"
    const val VIEW_REPORTS = "مشاهده گزارش‌ها"

    // ---------- ساخت جلسه ----------
    const val CREATE_SESSION_TITLE = "ایجاد جلسه حضور و غیاب"
    const val ATTENDANCE_WINDOW = "بازه زمانی حضورگیری"
    const val MINUTES = "دقیقه"
    const val START_SESSION = "شروع جلسه"
    const val NEW_COURSE = "درس جدید"
    const val COURSE_NAME = "نام درس"
    const val SAVE = "ذخیره"
    const val COURSE_CREATED = "درس جدید ساخته شد"
    const val COURSE_NAME_REQUIRED = "نام درس را وارد کنید"

    // ---------- جلسه زنده ----------
    const val LIVE_TITLE = "حضور و غیاب زنده"
    const val QR_ROTATES = "کد QR هر چند ثانیه به‌صورت خودکار تغییر می‌کند"
    const val AUDIO_CHALLENGE = "Audio Challenge"
    const val MONITOR = "وضعیت دانشجویان"
    const val CLOSE_SESSION = "پایان جلسه"
    const val CLOSE_CONFIRM_TITLE = "پایان جلسه؟"
    const val CLOSE_CONFIRM_TEXT = "پس از پایان جلسه، QR و Audio Challengeها نامعتبر شده و ثبت حضور جدید امکان‌پذیر نیست."
    const val REMAINING = "زمان باقی‌مانده"
    const val SESSION_CLOSED_DONE = "جلسه با موفقیت بسته شد"

    // ---------- گزارش‌ها ----------
    const val REPORTS_TITLE = "گزارش جلسات"
    const val REPORT_DETAIL = "جزئیات جلسه"
    const val NO_REPORTS = "گزارشی برای نمایش وجود ندارد"
    const val STUDENT = "دانشجو"
    const val ATTENDANCE_TIME = "زمان ثبت"

    // ---------- دانشجو ----------
    const val STUDENT_GREETING = "سلام 👋"
    const val ACTIVE_FOUND = "جلسه فعال پیدا شد"
    const val NO_ACTIVE = "در حال حاضر جلسه فعالی وجود ندارد"
    const val NO_ACTIVE_HINT = "به‌محض شروع جلسه توسط استاد، کد QR را اسکن کنید"
    const val REGISTER_ATTENDANCE = "ثبت حضور"
    const val SCAN_QR = "اسکن کد QR"
    const val MY_SESSIONS = "جلسات من"
    const val VIEW_ALL = "مشاهده همه"
    const val HISTORY_TITLE = "تاریخچه حضور و غیاب"

    // ---------- اسکنر ----------
    const val SCANNER_TITLE = "اسکن QR جلسه"
    const val SCANNER_HINT = "کد QR نمایش‌داده‌شده توسط استاد را مقابل دوربین قرار دهید"
    const val CAMERA_PERMISSION_NEEDED = "برای اسکن QR، دسترسی دوربین لازم است"
    const val GRANT_PERMISSION = "اعطای دسترسی"
    const val VERIFYING_QR = "در حال بررسی کد..."

    // ---------- بیومتریک ----------
    const val BIOMETRIC_TITLE = "تأیید هویت"
    const val BIOMETRIC_SUBTITLE = "برای ادامه، هویت خود را تأیید کنید"
    const val USE_FINGERPRINT = "استفاده از اثر انگشت"
    const val BIOMETRIC_STEP = "مرحله ۲ از ۳ · تأیید بیومتریک"

    // ---------- صوتی ----------
    const val AUDIO_TITLE = "تأیید صوتی"
    const val AUDIO_STEP = "مرحله ۳ از ۳ · شنیدن Challenge"
    const val LISTENING = "در حال شنیدن..."
    const val AUDIO_DONT_LEAVE = "از این صفحه خارج نشوید؛ خروج باعث ابطال Challenge می‌شود"
    const val SECONDS_LEFT = "ثانیه باقی‌مانده"
    const val RESTART_AUDIO = "شروع مجدد شنیدن"

    // ---------- نتیجه ----------
    const val SUCCESS_TITLE = "حضور شما ثبت شد ✅"
    const val SUCCESS_DESC = "ثبت حضور پس از تأیید سرور و بررسی چند عامل مستقل انجام شد."
    const val BACK_HOME = "بازگشت به خانه"
}
