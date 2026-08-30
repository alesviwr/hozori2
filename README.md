# 📱 حضور و غیاب هوشمند ضدتقلب — Smart Attendance

اپلیکیشن Production-Ready اندروید برای حضور و غیاب دانشجویان با **سه عامل مستقل امنیتی**:
Dynamic QR گردش + BiometricPrompt + Audio Challenge صوتی — با تأیید نهایی سمت سرور.

| مشخصات | مقدار |
|---|---|
| زبان | Kotlin 2.0.20 |
| UI | Jetpack Compose + Material 3 (بدون هیچ XML Layout) |
| معماری | Clean Architecture + MVVM + Usecase |
| DI | Hilt |
| شبکه | Retrofit + OkHttp + kotlinx.serialization |
| دیتابیس محلی | Room (فقط کش گزارش‌ها) |
| دوربین | CameraX + ML Kit Barcode (فقط فریم زنده) |
| بیومتریک | androidx.biometric (BiometricPrompt) |
| صوت | FSK شانزده‌تنه + دیکودر Goertzel (AudioTrack/AudioRecord) |
| minSdk / target | 26 / 35 |
| RTL | کامل (فارسی + فونت وزیرمتن) |

---

## ۱) اجرای پروژه

1. **Android Studio** (Ladybug یا جدیدتر) → `Open` → پوشه `SmartAttendance`
2. JDK 17 انتخاب باشد (`Settings → Build Tools → Gradle → Gradle JDK`)
3. Sync و Run

> پوشه `gradle/wrapper` همراه پروژه نیست؛ Android Studio هنگام Sync به‌صورت خودکار Wrapper می‌سازد، یا اگر Gradle نصب دارید: `gradle wrapper --gradle-version 8.7`

**حساب‌های آزمایشی (Mock Backend):**

| نقش | ایمیل | رمز |
|---|---|---|
| استاد | `prof@uni.edu` | `12345678` |
| دانشجوها | `ali@uni.edu` / `sara@uni.edu` / `reza@uni.edu` / `maryam@uni.edu` / `hossein@uni.edu` | `12345678` |

**اجرای تست‌های امنیتی:**
```bash
./gradlew :app:testDebugUnitTest
```

---

## ۲) معماری سیستم

```text
                 MockBackend (درون‌اپ — نقش سرور)
                 HMAC TokenSigner · ReplayGuard
                 UNIQUE(sessionId, studentId)
                       ▲           │
          ┌────────────┘           ▼
   Professor UI              Student UI
   ├─ Dashboard              ├─ Home
   ├─ Create Session         ├─ Live QR Scanner
   ├─ Live Session           ├─ BiometricPrompt
   │   ├─ Dynamic QR گردش    ├─ Audio Verification
   │   ├─ پخش Challenge      │   (Foreground Enforcement)
   │   └─ Monitor زنده       └─ History
   └─ Reports (+ Room cache)

   Domain (UseCases / Repository Interfaces)
   Data (Mock + Remote repositories — سوییچ خودکار)
```

**نکته کلیدی معماری:** کلاینت هرگز توکن معتبر تولید نمی‌کند.
- توکن QR و Audio Challenge توسط Backend (اینجا: MockBackend) تولید، امضا و Rotate می‌شود.
- اپ استاد فقط «رندرکننده QR» و «پخش‌کننده صدا» است.
- اپ دانشجو فقط «اسکنر» و «شنونده» است.
- ثبت حضور نهایی و همه بررسی‌های امنیتی سمت سرور انجام می‌شود.

---

## ۳) مدل امنیتی

### ۳.۱) Dynamic QR (بخش ۶ پرامپت)
- سرور برای هر جلسه یک توکن **Canonical** نگه می‌دارد و هر **۳ ثانیه** Rotate می‌کند.
- ساختار توکن: `AT|sessionId|tokenId|issuedAt|expiresAt|nonce|HMAC-SHA256`
- امضا با کلیدی انجام می‌شود که فقط سمت سرور است (کلاینت فقط رشته را رد و بدل می‌کند).
- مقایسه امضا **زمان-ثابت** است (ضد Timing Attack).

> ⚠️ تصمیم طراحی: توکن QR برای «هر دانشجو» یک‌بارمصرف است، نه به‌صورت سراسری — چون ۳۰ دانشجوی حاضر در کلاس باید بتوانند در همان پنجره ۳ ثانیه‌ای QR روی پرده را اسکن کنند. بردار Screenshot/فوروارد با TTL کوتاه ۳ ثانیه‌ای + عوامل بعدی (بیومتریک و چالش صوتی حاضر در کلاس) خنثی می‌شود. در صورت نیاز به مصرف سراسری، فقط `MockBackend.verifyQr` باید تغییر کند.

### ۳.۲) Audio Challenge — FSK شانزده‌تنه (بخش ۱۳ پرامپت)
- سرور هر **۱۲ ثانیه** چالش ۸ کاراکتری هگز جدید می‌سازد (Session-bound، کوتاه‌عمر).
- استاد آن را با **۱۶ تن ۱۰۰۰ تا ۳۸۵۰ هرتز** پخش می‌کند (پری‌امبل ۷۵۰Hz + ۸ فریم ۹۰ms + اندمارک ۴۳۰۰Hz ≈ ۱ ثانیه، لوپ پیوسته).
- دانشجو با میکروفون (منبع `VOICE_RECOGNITION` بدون AGC مخرب) می‌شنود و با فیلتر **Goertzel** دیکود می‌کند؛ آستانه‌های SNR و نسبت Top/Second ضدنویز کلاس هستند.
- توکن استخراج‌شده به سرور برمی‌گردد؛ سرور تطبیق با چالش‌های زنده «همین جلسه» + انقضا + Replay-per-student را بررسی می‌کند.

### ۳.۳) اجرای سخت‌گیرانه Foreground (بخش‌های ۱۴ و ۱۵ پرامپت)
در صفحه Audio Verification:
- `ON_PAUSE` / `ON_STOP` → توقف میکروفون + ابطال چالش + خطای `BACKGROUND_DETECTED`
- از دست دادن فوکوس پنجره (Split-Screen / Multi-Window / دیالوگ سیستم) → همان ابطال
- خروج از صفحه (onDispose) → توقف کامل
- بازگشت کاربر = شروع اجباری «شنیدن از نو»؛ چالش قبلی قابل استفاده نیست.

### ۳.۴) تأیید سرور (بخش ۱۷ پرامپت) — همه باید هم‌زمان برقرار باشند
```text
Session ACTIVE و داخل بازه
AND امضای QR معتبر  AND QR منقضی نشده  AND QR قبلاً توسط این دانشجو مصرف نشده
AND مرحله QR برای این دانشجو ثبت شده
AND Audio Token جزو چالش‌های زنده همین Session
AND Audio Token منقضی نشده  AND Audio Token برای این دانشجو مصرف نشده
AND اظهار بیومتریک true
AND Device Binding این حساب با deviceId درخواست یکی است
AND UNIQUE(sessionId, studentId) — حضور تکراری رد می‌شود
⇒ Attendance = PRESENT
```

### ۳.۵) Device Binding و Integrity (بخش‌های ۲۶ و ۲۷ پرامپت)
- Device Binding با **UUID تصادفی** (رمزشده در Keystore) انجام می‌شود — بدون هیچ شناسه سخت‌افزاری ممنوعه.
- Play Integrity به‌صورت Interface پیاده شده (PlayIntegrityChecker واقعی + Mock)؛ `UNKNOWN` مسدودکننده نیست چون طبق طراحی، Integrity جایگزین سایر عوامل نیست.
- Token نشست در `SharedPreferences` **رمزشده با AES-256-GCM + Android Keystore** ذخیره می‌شود (security-crypto به دلیل deprecated شدن کنار گذاشته شد — همان تضمین‌ها دستی پیاده شده).

---

## ۴) فهرست کامل فایل‌ها

### ریشه پروژه
| فایل | توضیح |
|---|---|
| `settings.gradle.kts` | تعریف پروژه و ریپازیتوری‌ها |
| `build.gradle.kts` | پلاگین‌های سطح ریشه از Version Catalog |
| `gradle.properties` | تنظیمات Gradle / AndroidX |
| `gradle/libs.versions.toml` | تمام نسخه‌ها و وابستگی‌ها (Version Catalog) |
| `.gitignore` | فایل‌های نادیده‌گرفته |

### `app/`
| فایل | توضیح |
|---|---|
| `app/build.gradle.kts` | پیکربندی ماژول + `USE_MOCK_BACKEND` و `BASE_URL` + همه وابستگی‌ها |
| `app/proguard-rules.pro` | قواعد R8 برای kotlinx.serialization و ZXing |
| `app/src/main/AndroidManifest.xml` | دسترسی‌های CAMERA / RECORD_AUDIO / USE_BIOMETRIC + Activity واحد |

### پیکربندی اپ و Core
| فایل | توضیح |
|---|---|
| `SmartAttendanceApp.kt` | `@HiltAndroidApp` |
| `MainActivity.kt` | Activity واحد (FragmentActivity برای BiometricPrompt) + RTL سراسری |
| `core/theme/Color.kt` | پالت Light/Dark و رنگ‌های وضعیت |
| `core/theme/Theme.kt` | تم Material 3 با سوییچ خودکار Dark/Light |
| `core/theme/Type.kt` | تایپوگرافی با فونت وزیرمتن |
| `core/ui/CommonComponents.kt` | StatCard / StatusChip / PrimaryButton / ErrorBanner / EmptyState / LabeledRow |
| `core/util/AppError.kt` | قرارداد خطای مشترک Client/Server + پیام فارسی هر کد |
| `core/util/FaStrings.kt` | مرکز متن‌های فارسی + تبدیل ارقام به فارسی |
| `core/util/Formatters.kt` | قالب ساعت و شمارش معکوس |
| `core/util/JalaliDate.kt` | تبدیل میلادی→جلالی برای نمایش تاریخ شمسی |

### Domain
| فایل | توضیح |
|---|---|
| `domain/model/Models.kt` | User/Role/Course/AttendanceSession/AttendanceRecord/MonitorData/... |
| `domain/repository/Repositories.kt` | اینترفیس‌های Auth/Professor/Student + نگاشت خطا |
| `domain/usecase/UseCases.kt` | ۱۵ Use Case لایه دامنه |

### Data — Remote (قرارداد Backend واقعی)
| فایل | توضیح |
|---|---|
| `data/remote/api/AttendanceApi.kt` | اینترفیس Retrofit همه Endpointها |
| `data/remote/dto/Dtos.kt` | DTOهای @Serializable قرارداد سرور |

### Data — Local (امن و Room)
| فایل | توضیح |
|---|---|
| `data/local/SecurePrefs.kt` | SharedPreferences با مقادیر رمزشده |
| `data/local/TokenStorage.kt` | نگهداری امن Token نشست + کاربر + رویداد خروج |
| `data/local/DeviceIdManager.kt` | UUID تصادفی رمزشده برای Device Binding |
| `data/local/AppDatabase.kt` | Room + Entity + DAO کش گزارش‌ها |

### Data — Mock Backend (نقش سرور)
| فایل | توضیح |
|---|---|
| `data/mock/MockBackend.kt` | قلب منطق سرور: کاربران/دروس/جلسات، Rotate توکن QR با HMAC، تولید چالش صوتی، verifyQr، submitAudio با همه شرط‌های بخش ۱۷، UNIQUE حضور، مانیتور و گزارش |

### Data — Repositoryها (Mock و Remote)
| فایل | توضیح |
|---|---|
| `data/repository/MockAuthRepository.kt` | ورود/نشست/خروج + `requireToken` |
| `data/repository/MockProfessorRepository.kt` | داشبورد/جلسه/QR/چالش/مانیتور/گزارش + کش Room |
| `data/repository/MockStudentRepository.kt` | خانه/verifyQr/submitAudio/تاریخچه |
| `data/repository/RemoteAuthRepository.kt` | پیاده‌سازی واقعی Retrofit (آماده اتصال) |
| `data/repository/RemoteProfessorRepository.kt` | پیاده‌سازی واقعی Retrofit |
| `data/repository/RemoteStudentRepository.kt` | پیاده‌سازی واقعی Retrofit |

### Security
| فایل | توضیح |
|---|---|
| `security/TokenSigner.kt` | HMAC-SHA256 با مقایسه زمان-ثابت |
| `security/ReplayGuard.kt` | محافظ مصرف یک‌باره با TTL (ضد Replay) |
| `security/keystore/CryptoManager.kt` | AES-256-GCM با کلید Android Keystore |
| `security/biometric/BiometricAuthenticator.kt` | پوشش BiometricPrompt (اثر انگشت خام هرگز ذخیره نمی‌شود) |
| `security/integrity/IntegrityChecker.kt` | Interface + Mock + PlayIntegrityChecker واقعی |

### Audio و QR
| فایل | توضیح |
|---|---|
| `audio/AudioChallengeCodec.kt` | کدگذار FSK شانزده‌تنه + دیکودر استریمی Goertzel (Pure Kotlin و تست‌پذیر) |
| `audio/TonePlayer.kt` | پخش لوپ چالش با AudioTrack MODE_STATIC |
| `audio/MicRecorder.kt` | ضبط زنده با AudioRecord → Flow فریم‌ها |
| `qr/QrGenerator.kt` | رندر QR با ZXing (اپ فقط رندرکننده است) |

### Navigation و DI
| فایل | توضیح |
|---|---|
| `navigation/AppNavHost.kt` | گراف ناوبری مبتنی بر Role + Routes |
| `di/AppModule.kt` | Storage امن / MockBackend / Room / موتور صوتی / شبکه Retrofit |
| `di/RepositoryModule.kt` | سوییچ خودکار Mock↔Remote + AuthInterceptor + ErrorMappingInterceptor + Integrity |

### Presentation — Splash و Auth
| فایل | توضیح |
|---|---|
| `presentation/splash/SplashScreen.kt` | تشخیص نشست و هدایت بر اساس Role سرور |
| `presentation/auth/LoginScreen.kt` | صفحه ورود با مدیریت خطا |

### Presentation — استاد
| فایل | توضیح |
|---|---|
| `presentation/professor/dashboard/ProfessorDashboardScreen.kt` | داشبورد: جلسه فعال، شمارنده‌ها، کلاس‌های امروز، جلسات اخیر |
| `presentation/professor/createsession/CreateSessionScreen.kt` | انتخاب درس/کلاس/بازه حضورگیری |
| `presentation/professor/livesession/LiveSessionScreen.kt` | QR گردش زنده + پخش Audio Challenge + مانیتور زنده + پایان جلسه |
| `presentation/professor/reports/ReportsScreen.kt` | گزارش جلسات + جزئیات + کش Room |

### Presentation — دانشجو
| فایل | توضیح |
|---|---|
| `presentation/student/home/StudentHomeScreen.kt` | جلسه فعال + ثبت حضور + جلسات من |
| `presentation/student/scanner/ScannerScreen.kt` | اسکن زنده CameraX + ML Kit — بدون گالری/فایل/اسکرین‌شات |
| `presentation/student/biometric/BiometricStepScreen.kt` | مرحله ۲: BiometricPrompt با حالت‌های خطا |
| `presentation/student/audio/AudioVerificationScreen.kt` | مرحله ۳: شنیدن چالش + Foreground Enforcement سخت‌گیرانه |
| `presentation/student/result/AttendanceResultScreen.kt` | صفحه موفقیت پس از تأیید سرور |
| `presentation/student/history/HistoryScreen.kt` | تاریخچه فقط مال خود دانشجو |

### تست‌ها (`app/src/test/...`)
| فایل | پوشش |
|---|---|
| `TokenSecurityTest.kt` | امضای HMAC، دستکاری، جعل با کلید غلط، انقضا، ReplayGuard (مصرف یک‌باره/TTL/چنددانشجو) |
| `AudioCodecTest.kt` | رفت‌وبرگشت Encode→Decode، همه توکن‌ها، نویز کلاس، استریم با سکوت اولیه، رد سیگنال بی‌چالش و ناقص، رد توکن خراب، ثبات تایم‌لاین |
| `SessionAndAttendanceTest.kt` | Role سمت سرور، QR سالم/دستکاری‌شده/منقضی/Replay/جلسه بسته/بازه منقضی، submit بدون QR، فلو کامل موفق، UNIQUE حضور، Audio غلط/منقضی/جلسه اشتباه، بیومتریک نهفته، Device Mismatch، مانیتور و گزارش، حریم تاریخچه دانشجو |

### منابع (`res/`)
| فایل | توضیح |
|---|---|
| `res/font/vazirmatn_regular.ttf` · `vazirmatn_bold.ttf` | فونت فارسی (LGPL از پروژه Vazirmatn) |
| `res/values/strings.xml` · `themes.xml` · `colors.xml` | نام اپ، تم، رنگ پنجره |
| `res/values-night/colors.xml` | پس‌زمینه تاریک |
| `res/drawable/ic_launcher.xml` | آیکون وکتور |

---

## ۵) اتصال Backend واقعی

معماری طوری نوشته شده که **بدون تغییر UI** سوییچ شود:

1. در `app/build.gradle.kts`: `USE_MOCK_BACKEND` را `false` کنید و `BASE_URL` واقعی بدهید.
2. سرور باید این قرارداد را پیاده کند (همان `AttendanceApi`):
   - `POST auth/login` → `{token, user{...}}`
   - `GET professor/dashboard|courses|sessions/active|reports|reports/{id}`
   - `POST professor/sessions` · `GET professor/sessions/{id}/qr-token|audio-challenge|monitor` · `POST professor/sessions/{id}/close`
   - `GET student/home|history` · `POST attendance/verify-qr|verify-audio` · `POST devices/register` · `POST security/integrity`
3. خطاها: HTTP != 2xx با بدنه `{"error": "QR_EXPIRED"}` (نام‌های enum از `AppErrorType`).
4. منطق `MockBackend` + `TokenSigner` + `ReplayGuard` دقیقاً همان چیزی است که باید سمت سرور پیاده شود — می‌توانید آن را مستند پیاده‌سازی Backend قرار دهید.
5. Play Integrity: اپ را به Firebase/Google Cloud متصل، Play Integrity API را فعال و اپ را در Play Console (Internal Testing کافی است) منتشر کنید؛ اعتبارسنجی توکن سمت سرور انجام می‌شود.

## ۶) نکات بهینه‌سازی میدانی

- آستانه‌های دیکودر (`PREAMBLE_SNR/FRAME_SNR/RATIO/ABS_MIN`) در `AudioChallengeDecoder` برای سالن‌های نوفه ممکن است نیاز به کالیبراسیون داشته باشد — تست‌های `AudioCodecTest` الگوی تنظیم را نشان می‌دهند.
- بلندگو/میکروفون ارزان ممکن است فرکانس‌های بالای ۴kHz را تضعیف کنند؛ بازه انتخابی ۱–۴kHz عمداً محافظه‌کارانه است.
- برای کلاس‌های بزرگ، Runtime Token Rotation و صدور چالش باید همان‌طور که اینجاست روی سرور مرکزی باشد تا دو موبایل استاد هم‌زمان سازگار بمانند.

## ۷) نقشه راه پیشنهادی

- WebSocket/Push برای به‌روزرسانی مانیتور زنده به‌جای Polling
- شاردینگ توکن برای سالن‌های چندصدنفره
- گزارش‌گیری CSV/PDF از سمت سرور
- اضافه‌شدن Late/Excused به AttendanceStatus با گردش‌کار تأیید استاد
