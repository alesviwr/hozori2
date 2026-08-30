package com.smartattendance.presentation.professor.dashboard

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
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.School
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
import com.smartattendance.core.theme.StatusPending
import com.smartattendance.core.theme.StatusPresent
import com.smartattendance.core.ui.PrimaryButton
import com.smartattendance.core.ui.StatCard
import com.smartattendance.core.util.Fa
import com.smartattendance.domain.model.DashboardData
import com.smartattendance.domain.usecase.GetDashboardUseCase
import com.smartattendance.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfessorDashboardViewModel @Inject constructor(
    private val getDashboard: GetDashboardUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<DashboardData?>(null)
    val state: StateFlow<DashboardData?> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { getDashboard() }.onSuccess { _state.value = it }
    }

    fun logout() = viewModelScope.launch { logoutUseCase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessorDashboardScreen(
    onCreateSession: () -> Unit,
    onOpenLive: (String) -> Unit,
    onOpenReports: () -> Unit,
    vm: ProfessorDashboardViewModel = hiltViewModel(),
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
                Text(Fa.PROF_GREETING, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    text = d.professorName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ───────── جلسه فعال ─────────
            item {
                val active = d.activeSession
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(Fa.ACTIVE_SESSIONS, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        if (active == null) {
                            Spacer(Modifier.height(8.dp))
                            Text(Fa.NO_ACTIVE_SESSION, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(14.dp))
                            PrimaryButton(text = Fa.START_ATTENDANCE, onClick = onCreateSession)
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(active.courseName, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${active.building} - کلاس ${Fa.digits(active.room)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatCard(Fa.PRESENT, d.presentCount, StatusPresent, Modifier.weight(1f))
                                StatCard(Fa.PENDING, d.pendingCount, StatusPending, Modifier.weight(1f))
                                StatCard(Fa.ABSENT, d.absentCount, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            PrimaryButton(text = Fa.RESUME_ATTENDANCE, onClick = { onOpenLive(active.id) })
                        }
                    }
                }
            }

            // ───────── کلاس‌های امروز ─────────
            item {
                Text(Fa.TODAY_COURSES, style = MaterialTheme.typography.titleMedium)
            }
            items(d.todayCourses) { course ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(course.courseName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                course.room,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ───────── جلسات اخیر ─────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Fa.RECENT_SESSIONS, style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onOpenReports) { Text(Fa.VIEW_REPORTS) }
                }
            }
            items(d.recentSessions) { report ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenReports,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(report.courseName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${report.date} · ${Fa.PRESENT}: ${Fa.digits(report.presentCount)} · ${Fa.ABSENT}: ${Fa.digits(report.absentCount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
