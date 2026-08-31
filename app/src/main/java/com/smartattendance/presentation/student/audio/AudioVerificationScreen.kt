package com.smartattendance.presentation.student.audio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.smartattendance.audio.AudioChallengeDecoder
import com.smartattendance.audio.MicRecorder
import com.smartattendance.core.ui.ErrorBanner
import com.smartattendance.core.ui.PrimaryButton
import com.smartattendance.core.util.AppErrorType
import com.smartattendance.core.util.Fa
import com.smartattendance.core.util.Formatters
import com.smartattendance.core.util.persianMessage
import com.smartattendance.domain.model.IntegrityVerdict
import com.smartattendance.domain.repository.mapThrowable
import com.smartattendance.domain.usecase.SubmitAudioTokenUseCase
import com.smartattendance.security.integrity.IntegrityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** فازهای صفحه Audio Verification */
sealed interface AudioPhase {
    /** در حال شنیدن با شمارش معکوس (میلی‌ثانیه باقی‌مانده) */
    data class Listening(val remainingMs: Long) : AudioPhase

    /** توکن پیدا شد؛ در انتظار تأیید سرور */
    data object Verifying : AudioPhase

    /** حضور ثبت شد */
    data object Success : AudioPhase

    /** خطا — نیاز به شروع مجدد */
    data class Error(val type: AppErrorType) : AudioPhase
}

/**
 * ⚠️ قلب سیاست ضدتقلب سمت کلاینت:
 *
 * فرآیند شنیدن فقط وقتی معتبر است که اپ Foreground و صفحه قابل مشاهده باشد.
 * هر یک از موارد زیر → توقف میکروفون + ابطال چالش + شروع مجدد اجباری:
 *   Home / Minimize / رفتن به اپ دیگر / ترک صفحه / قفل صفحه / Split-Screen (از دست دادن فوکوس)
 */
@HiltViewModel
class AudioVerificationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val micRecorder: MicRecorder,
    private val submitAudioToken: SubmitAudioTokenUseCase,
    private val integrityChecker: IntegrityChecker,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _phase = MutableStateFlow<AudioPhase>(AudioPhase.Listening(TIMEOUT_MS))
    val phase: StateFlow<AudioPhase> = _phase.asStateFlow()

    private var listenJob: Job? = null
    private val decoder = AudioChallengeDecoder()

    // توجه: قبلاً اینجا start() مستقیم صدا زده می‌شد، یعنی میکروفون قبل از اینکه Compose
    // اصلاً فرصت درخواست مجوز RECORD_AUDIO را داشته باشد، تلاش می‌کرد ضبط کند و همیشه شکست
    // می‌خورد. حالا start() فقط بعد از تأیید مجوز در UI صدا زده می‌شود.

    /** شروع / شروع مجدد شنیدن */
    fun start() {
        if (sessionId.isBlank()) {
            _phase.value = AudioPhase.Error(AppErrorType.QR_INVALID)
            return
        }
        listenJob?.cancel()
        decoder.reset()
        _phase.value = AudioPhase.Listening(TIMEOUT_MS)

        listenJob = viewModelScope.launch(Dispatchers.Default) {
            val tokenDeferred = CompletableDeferred<String>()
            val deadline = System.currentTimeMillis() + TIMEOUT_MS

            // بهینه‌سازی تاخیر: چک Play Integrity را همزمان با شروع شنیدن اجرا کن، نه بعد از
            // پیدا شدن توکن. چون شنیدن/دیکود خودش چند ثانیه طول می‌کشد، وقتی توکن پیدا شد
            // این چک معمولاً از قبل تمام شده و submit() دیگر منتظرش نمی‌ماند.
            val verdictDeferred = async(Dispatchers.IO) {
                integrityChecker.verdict(nonce = "attendance:$sessionId")
            }

            val listen = launch {
                runCatching {
                    micRecorder.frames()
                        .flowOn(Dispatchers.IO)
                        .collect { frame ->
                            val token = decoder.feed(frame)
                            if (token != null && !tokenDeferred.isCompleted) {
                                tokenDeferred.complete(token)
                            }
                        }
                }.onFailure {
                    if (_phase.value is AudioPhase.Listening) {
                        // این خطا از AudioRecord/میکروفون است، نه شبکه — قبلاً اشتباهاً NETWORK_ERROR
                        // برچسب می‌خورد که کاربر را گمراه می‌کرد («اینترنتت رو چک کن» درحالی‌که
                        // مشکل واقعی نبودِ مجوز میکروفون یا اشغال‌بودن آن بود).
                        _phase.value = AudioPhase.Error(AppErrorType.MIC_UNAVAILABLE)
                    }
                }
            }

            val token = withTimeoutOrNull(TIMEOUT_MS) {
                launch {
                    while (isActive && !tokenDeferred.isCompleted) {
                        if (_phase.value is AudioPhase.Listening) {
                            _phase.value = AudioPhase.Listening(
                                (deadline - System.currentTimeMillis()).coerceAtLeast(0),
                            )
                        }
                        delay(250)
                    }
                }
                tokenDeferred.await()
            }

            listen.cancel()

            when {
                // لغو خارجی (ترک صفحه / بک‌گراند) — هیچ کاری نکن، abort خودش Error گذاشته
                _phase.value !is AudioPhase.Listening && _phase.value !is AudioPhase.Error -> Unit

                token != null -> {
                    _phase.value = AudioPhase.Verifying
                    submit(token, verdictDeferred)
                }

                _phase.value is AudioPhase.Listening ->
                    _phase.value = AudioPhase.Error(AppErrorType.AUDIO_TIMEOUT)
            }

            // اگر توکن با شکست/timeout تمام شد، دیگر نیازی به نتیجه‌ی integrity نیست
            if (token == null) verdictDeferred.cancel()
        }
    }

    /** ارسال توکن استخراج‌شده + اظهار بیومتریک + نتیجه Integrity به سرور */
    private suspend fun submit(audioToken: String, verdictDeferred: Deferred<IntegrityVerdict>) {
        val verdict = verdictDeferred.await()
        runCatching {
            submitAudioToken(
                sessionId = sessionId,
                audioToken = audioToken,
                biometricAttested = true, // فقط پس از موفقیت BiometricPrompt به این صفحه می‌رسیم
                integrityVerdict = verdict,
            )
        }.onSuccess {
            _phase.value = AudioPhase.Success
        }.onFailure { t ->
            _phase.value = AudioPhase.Error(mapThrowable(t))
        }
    }

    // ─────────── Lifecycle Enforcement ───────────

    /** ON_PAUSE / ON_STOP / ترک صفحه */
    fun onLeftForeground() {
        if (_phase.value is AudioPhase.Listening || _phase.value is AudioPhase.Verifying) {
            abort(AppErrorType.BACKGROUND_DETECTED)
        }
    }

    /** از دست دادن فوکوس پنجره (Multi-Window / Split-Screen / دیالوگ سیستم) */
    fun onFocusLost() {
        if (_phase.value is AudioPhase.Listening || _phase.value is AudioPhase.Verifying) {
            abort(AppErrorType.BACKGROUND_DETECTED)
        }
    }

    private fun abort(type: AppErrorType) {
        listenJob?.cancel()
        micRecorder.stop()
        decoder.reset()
        _phase.value = AudioPhase.Error(type)
    }

    /** ترک نهایی صفحه */
    fun cancelCapture() {
        listenJob?.cancel()
        micRecorder.stop()
    }

    override fun onCleared() {
        cancelCapture()
        super.onCleared()
    }

    companion object {
        const val TIMEOUT_MS = 15_000L
    }
}

@Composable
private fun MicPermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Filled.MicOff, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Text("برای شنیدن Challenge، دسترسی میکروفون لازم است", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRequest) { Text("اجازه دسترسی به میکروفون") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioVerificationScreen(
    sessionId: String,
    courseName: String,
    biometricOk: Boolean,
    onVerified: (sessionId: String) -> Unit,
    onBack: () -> Unit,
    onSecurityReset: () -> Unit = onBack,
    vm: AudioVerificationViewModel = hiltViewModel(),
) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val context = LocalContext.current

    // ───────── مجوز میکروفون — قبلاً اصلاً درخواست نمی‌شد ─────────
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasMicPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    // به‌محض گرفتن مجوز، شنیدن را شروع کن (فقط یک‌بار)
    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission) vm.start()
    }

    // ناوبری موفقیت
    LaunchedEffect(phase) {
        if (phase is AudioPhase.Success) {
            delay(600)
            onVerified(sessionId)
        }
    }

    // اگر ترک صفحه/بک‌گراند/از دست دادن فوکوس شناسایی شد، به‌جای «تلاش مجدد» محلی،
    // کاربر مستقیم به مرحله‌ی اسکن QR برگردانده می‌شود — دقیقاً همان چیزی که باید اتفاق بیفتد.
    LaunchedEffect(phase) {
        val p = phase
        if (p is AudioPhase.Error && p.type == AppErrorType.BACKGROUND_DETECTED) {
            onSecurityReset()
        }
    }

    // ───────── سخت‌گیری Lifecycle: هر ترک صفحه/بک‌گراند = ابطال ─────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> vm.onLeftForeground()
                Lifecycle.Event.ON_STOP -> vm.onLeftForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.cancelCapture()
        }
    }

    // ───────── سخت‌گیری Focus: Split-Screen، Multi-Window و پایین‌کشیدن نوتیفیکیشن ─────────
    DisposableEffect(view) {
        val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (!hasFocus) vm.onFocusLost()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
        }
    }

    // ⚠️ اگر دانشجو بدون بیومتریک موفق به این صفحه برسد (ناوبری دستی) → رد
    LaunchedEffect(biometricOk) {
        if (!biometricOk) vm.onLeftForeground()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Fa.AUDIO_STEP) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Fa.BACK,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val p = phase
            when {
                !hasMicPermission -> MicPermissionRationale { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

                p is AudioPhase.Listening -> {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(96.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(Fa.LISTENING, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        courseName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(22.dp))
                    CircularProgressIndicator(modifier = Modifier.size(44.dp), strokeWidth = 4.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "${Fa.digits(Formatters.countdown(p.remainingMs))} ${Fa.SECONDS_LEFT}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        Fa.AUDIO_DONT_LEAVE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                p is AudioPhase.Verifying -> {
                    CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 5.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(Fa.LOADING, style = MaterialTheme.typography.titleMedium)
                }

                p is AudioPhase.Success -> {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(96.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(Fa.SUCCESS_TITLE, style = MaterialTheme.typography.headlineSmall)
                }

                p is AudioPhase.Error -> {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(88.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    ErrorBanner(p.type.persianMessage(), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = if (p.type == AppErrorType.BACKGROUND_DETECTED) Fa.RESTART_AUDIO else Fa.RETRY,
                        onClick = vm::start,
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(text = Fa.BACK, onClick = onBack)
                }
            }
        }
    }
}
