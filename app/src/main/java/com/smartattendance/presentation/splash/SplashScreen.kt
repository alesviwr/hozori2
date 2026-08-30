package com.smartattendance.presentation.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartattendance.core.util.Fa
import com.smartattendance.domain.model.Role
import com.smartattendance.domain.usecase.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    /** تشخیص مقصد بر اساس Role ذخیره‌شده از سرور */
    suspend fun resolve(): Role? = getCurrentUser()?.role
}

@Composable
fun SplashScreen(
    onGoProfessor: () -> Unit,
    onGoStudent: () -> Unit,
    onGoLogin: () -> Unit,
    vm: SplashViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500) // نمایش برند
        when (vm.resolve()) {
            Role.PROFESSOR -> onGoProfessor()
            Role.STUDENT -> onGoStudent()
            null -> onGoLogin()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = Fa.APP_NAME, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            text = "ثبت حضور چندعاملی و ضدتقلب",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
    }
}
