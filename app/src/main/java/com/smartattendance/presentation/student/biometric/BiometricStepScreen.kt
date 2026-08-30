package com.smartattendance.presentation.student.biometric

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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartattendance.core.ui.ErrorBanner
import com.smartattendance.core.ui.PrimaryButton
import com.smartattendance.core.util.AppErrorType
import com.smartattendance.core.util.Fa
import com.smartattendance.core.util.persianMessage
import com.smartattendance.navigation.Routes
import com.smartattendance.security.biometric.BiometricAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * وضعیت مرحله بیومتریک.
 * نتیجه بیومتریک فقط «اظهار موفقیت» است؛ اعتبار نهایی با ترکیب عوامل روی سرور سنجیده می‌شود.
 */
sealed interface BiometricUiState {
    data object Ready : BiometricUiState
    data object Success : BiometricUiState
    data class Failed(val type: AppErrorType, val detail: String? = null) : BiometricUiState
}

@HiltViewModel
class BiometricStepViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val authenticator: BiometricAuthenticator,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _state = MutableStateFlow<BiometricUiState>(BiometricUiState.Ready)
    val state: StateFlow<BiometricUiState> = _state.asStateFlow()

    fun onResult(success: Boolean, error: String?) {
        _state.value = if (success) {
            BiometricUiState.Success
        } else {
            BiometricUiState.Failed(AppErrorType.BIOMETRIC_FAILED, error)
        }
    }

    fun reset() {
        _state.value = BiometricUiState.Ready
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricStepScreen(
    courseName: String,
    onVerified: (sessionId: String, courseName: String) -> Unit,
    onBack: () -> Unit,
    vm: BiometricStepViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var triggered by remember { mutableStateOf(false) }

    val activity = context as? androidx.fragment.app.FragmentActivity

    // شروع BiometricPrompt
    LaunchedEffect(Unit) {
        if (!triggered && activity != null) {
            triggered = true
            if (vm.authenticator.canAuthenticate(activity)) {
                vm.authenticator.authenticate(
                    activity = activity,
                    title = Fa.BIOMETRIC_TITLE,
                    subtitle = "${Fa.BIOMETRIC_SUBTITLE} — $courseName",
                    negativeText = "انصراف",
                    onSuccess = { vm.onResult(true, null) },
                    onError = { vm.onResult(false, it) },
                )
            } else {
                vm.onResult(false, AppErrorType.BIOMETRIC_UNAVAILABLE.persianMessage())
            }
        }
    }

    LaunchedEffect(state) {
        if (state is BiometricUiState.Success) onVerified(vm.sessionId, courseName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Fa.BIOMETRIC_STEP) },
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
            Icon(
                Icons.Filled.Fingerprint,
                contentDescription = null,
                tint = if (state is BiometricUiState.Success) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(Fa.BIOMETRIC_SUBTITLE, style = MaterialTheme.typography.titleMedium)
            Text(
                courseName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            val failed = state as? BiometricUiState.Failed
            failed?.let {
                Spacer(Modifier.height(18.dp))
                ErrorBanner(it.detail ?: it.type.persianMessage(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = Fa.RETRY,
                    onClick = {
                        vm.reset()
                        triggered = false
                    },
                )
            }
        }
    }
}
