package com.smartattendance.presentation.student.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.smartattendance.core.ui.EmptyState
import com.smartattendance.core.ui.PrimaryButton
import com.smartattendance.core.ui.StatusChip
import com.smartattendance.core.util.Fa
import com.smartattendance.domain.model.StudentHomeData
import com.smartattendance.domain.usecase.GetStudentHomeUseCase
import com.smartattendance.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudentHomeViewModel @Inject constructor(
    private val getStudentHome: GetStudentHomeUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<StudentHomeData?>(null)
    val state: StateFlow<StudentHomeData?> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { getStudentHome() }.onSuccess { _state.value = it }
    }

    fun logout() = viewModelScope.launch { logoutUseCase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentHomeScreen(
    onStartAttendance: () -> Unit,
    onOpenHistory: () -> Unit,
    vm: StudentHomeViewModel = hiltViewModel(),
) {
    val data by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Fa.APP_NAME) },
                actions = {
                    IconButton(onClick = vm::logout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = Fa.LOGOUT)
                    }
                },
            )
        },
    ) { padding ->
        val d = data ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(Fa.STUDENT_GREETING, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${d.studentName} · ${Fa.digits(d.studentNumber)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ───────── جلسه فعال ─────────
            item {
                val active = d.activeSession
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (active != null) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            if (active != null) Fa.ACTIVE_FOUND else Fa.NO_ACTIVE,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (active != null) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (active != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                active.courseName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "${active.building} - کلاس ${Fa.digits(active.room)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                Fa.NO_ACTIVE_HINT,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(
                            text = if (active != null) Fa.REGISTER_ATTENDANCE else Fa.SCAN_QR,
                            onClick = onStartAttendance,
                        )
                    }
                }
            }

            // ───────── جلسات من ─────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Fa.MY_SESSIONS, style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onOpenHistory) { Text(Fa.VIEW_ALL) }
                }
            }

            if (d.recent.isEmpty()) {
                item { EmptyState(Icons.Filled.QrCodeScanner, "هنوز جلسه‌ای ثبت نشده است") }
            } else {
                items(d.recent) { item ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(item.courseName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    item.date,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusChip(item.status.name)
                        }
                    }
                }
            }
        }
    }
}
