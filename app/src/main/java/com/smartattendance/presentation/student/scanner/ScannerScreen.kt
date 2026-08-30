package com.smartattendance.presentation.student.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NoPhotography
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.smartattendance.core.ui.ErrorBanner
import com.smartattendance.core.util.Fa
import com.smartattendance.core.util.persianMessage
import com.smartattendance.domain.repository.mapThrowable
import com.smartattendance.domain.usecase.VerifyQrUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

/** وضعیت اسکنر — فقط تحلیل زنده؛ هیچ مسیر گالری/فایل وجود ندارد */
sealed interface ScannerState {
    data object Idle : ScannerState
    data object Verifying : ScannerState
    data class Verified(val sessionId: String, val courseName: String) : ScannerState
    data class Error(val type: com.smartattendance.core.util.AppErrorType) : ScannerState
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val verifyQr: VerifyQrUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ScannerState>(ScannerState.Idle)
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    /** جلوگیری از پردازش هم‌زمان چند فریم */
    @Volatile
    private var busy = false

    fun onQrDetected(payload: String) {
        if (busy || payload.isBlank()) return
        busy = true
        viewModelScope.launch {
            _state.update { ScannerState.Verifying }
            runCatching { verifyQr(payload) }
                .onSuccess { result -> _state.update { ScannerState.Verified(result.sessionId, result.courseName) } }
                .onFailure { t ->
                    val err = mapThrowable(t)
                    _state.update { ScannerState.Error(err) }
                    // پس از ۲.۵ ثانیه اجازه اسکن مجدد داده می‌شود
                    kotlinx.coroutines.delay(2500)
                    _state.update { ScannerState.Idle }
                    busy = false
                }
        }
    }

    fun resetBusy() {
        busy = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onVerified: (sessionId: String, courseName: String) -> Unit,
    onBack: () -> Unit,
    vm: ScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(state) {
        val s = state
        if (s is ScannerState.Verified) onVerified(s.sessionId, s.courseName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Fa.SCANNER_TITLE) },
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                !hasPermission -> PermissionRationale { permissionLauncher.launch(Manifest.permission.CAMERA) }

                state is ScannerState.Verified -> {
                    CircularProgressIndicator()
                    Text(Fa.LOADING)
                }

                else -> {
                    // ───────── دوربین زنده ─────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        LiveCameraPreview(
                            enabled = state is ScannerState.Idle,
                            onQr = vm::onQrDetected,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (state is ScannerState.Verifying) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Text(Fa.VERIFYING_QR, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    }

                    Text(
                        Fa.SCANNER_HINT,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    (state as? ScannerState.Error)?.let { err ->
                        ErrorBanner(err.type.persianMessage(), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Filled.NoPhotography, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Text(Fa.CAMERA_PERMISSION_NEEDED, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRequest) { Text(Fa.GRANT_PERMISSION) }
    }
}

/**
 * دوربین زنده با CameraX + ML Kit.
 * ⛔ بدون GalleryPicker / FilePicker / Screenshot — فقط فریم‌های زنده دوربین.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun LiveCameraPreview(
    enabled: Boolean,
    onQr: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                post {
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        try {
                            val provider = providerFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(surfaceProvider)
                            }

                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            analysis.setAnalyzer(analyzerExecutor) { proxy: ImageProxy ->
                                analyzeFrame(scanner, proxy, onQr)
                            }

                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        } catch (_: Exception) {
                            // دوربین در دسترس نیست — UI خطای سطح بالاتر نشان داده می‌شود
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        },
    )
}

@OptIn(ExperimentalGetImage::class)
private fun analyzeFrame(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    proxy: ImageProxy,
    onQr: (String) -> Unit,
) {
    val mediaImage = proxy.image
    if (mediaImage == null) {
        proxy.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { codes ->
            codes.firstOrNull()?.rawValue?.let { value -> onQr(value) }
        }
        .addOnCompleteListener { proxy.close() }
}
