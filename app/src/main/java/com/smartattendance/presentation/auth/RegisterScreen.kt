package com.smartattendance.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.smartattendance.core.util.Fa
import com.smartattendance.core.util.persianMessage
import com.smartattendance.domain.model.Role
import com.smartattendance.domain.repository.LoginResult
import com.smartattendance.domain.repository.mapThrowable
import com.smartattendance.domain.usecase.LoginUseCase
import com.smartattendance.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val studentNumber: String = "",
    val role: Role = Role.STUDENT,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onNameChange(v: String) = _state.update { it.copy(name = v, error = null) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onStudentNumberChange(v: String) = _state.update { it.copy(studentNumber = v, error = null) }
    fun onRoleChange(v: Role) = _state.update { it.copy(role = v, error = null) }

    fun register(onSuccess: (Role) -> Unit) {
        val s = _state.value
        if (s.name.isBlank() || s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "اطلاعات را کامل وارد کنید") }
            return
        }
        if (s.password.length < 6) {
            _state.update { it.copy(error = Fa.PASSWORD_SHORT) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                registerUseCase(s.name, s.email, s.password, s.role, s.studentNumber.ifBlank { null })
                val result: LoginResult = loginUseCase(s.email, s.password)
                result
            }.onSuccess { result ->
                _state.update { it.copy(loading = false) }
                onSuccess(result.user.role)
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = mapThrowable(t).persianMessage()) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegistered: (Role) -> Unit,
    onBack: () -> Unit,
    vm: RegisterViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Fa.REGISTER_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Fa.BACK)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                Fa.REGISTER_SUBTITLE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(Fa.CHOOSE_ROLE, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = state.role == Role.STUDENT,
                            onClick = { vm.onRoleChange(Role.STUDENT) },
                            label = { Text(Fa.ROLE_STUDENT) },
                        )
                        FilterChip(
                            selected = state.role == Role.PROFESSOR,
                            onClick = { vm.onRoleChange(Role.PROFESSOR) },
                            label = { Text(Fa.ROLE_PROFESSOR) },
                        )
                    }

                    OutlinedTextField(
                        value = state.name,
                        onValueChange = vm::onNameChange,
                        label = { Text(Fa.FULL_NAME) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = vm::onEmailChange,
                        label = { Text("ایمیل") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = vm::onPasswordChange,
                        label = { Text(Fa.PASSWORD) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.role == Role.STUDENT) {
                        OutlinedTextField(
                            value = state.studentNumber,
                            onValueChange = vm::onStudentNumberChange,
                            label = { Text(Fa.STUDENT_NUMBER) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    state.error?.let { ErrorBanner(it) }

                    PrimaryButton(
                        text = Fa.REGISTER,
                        loading = state.loading,
                        onClick = {
                            vm.register { role ->
                                onRegistered(role)
                            }
                        },
                    )
                }
            }
        }
    }
}
