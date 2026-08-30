package com.smartattendance.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.smartattendance.core.ui.ErrorBanner
import com.smartattendance.core.ui.PrimaryButton
import com.smartattendance.core.util.AppErrorType
import com.smartattendance.core.util.Fa
import com.smartattendance.core.util.persianMessage
import com.smartattendance.domain.model.Role
import com.smartattendance.domain.usecase.LoginUseCase
import com.smartattendance.domain.repository.mapThrowable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val loading: Boolean = false,
    val error: AppErrorType? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun togglePassword() = _state.update { it.copy(showPassword = !it.showPassword) }

    fun login(onSuccess: (Role) -> Unit) {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = AppErrorType.INVALID_CREDENTIALS) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { loginUseCase(s.email, s.password) }
                .onSuccess { result ->
                    _state.update { it.copy(loading = false) }
                    onSuccess(result.user.role) // Role همیشه از سرور
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = mapThrowable(t)) }
                }
        }
    }
}

@Composable
fun LoginScreen(
    onGoProfessor: () -> Unit,
    onGoStudent: () -> Unit,
    onGoRegister: () -> Unit = {},
    vm: LoginViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = Fa.LOGIN_TITLE, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            text = Fa.LOGIN_SUBTITLE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = vm::onEmailChange,
                    label = { Text(Fa.EMAIL) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = vm::onPasswordChange,
                    label = { Text(Fa.PASSWORD) },
                    singleLine = true,
                    visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = vm::togglePassword) {
                            Icon(
                                imageVector = if (state.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    ErrorBanner(it.persianMessage())
                }

                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    text = Fa.LOGIN,
                    loading = state.loading,
                    onClick = {
                        vm.login { role ->
                            if (role == Role.PROFESSOR) onGoProfessor() else onGoStudent()
                        }
                    },
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text = Fa.LOGIN_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.TextButton(
                    onClick = onGoRegister,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${Fa.NO_ACCOUNT} ${Fa.REGISTER}")
                }
            }
        }
    }
}
