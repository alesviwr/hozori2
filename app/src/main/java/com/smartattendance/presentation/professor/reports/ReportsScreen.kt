package com.smartattendance.presentation.professor.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.smartattendance.core.ui.EmptyState
import com.smartattendance.core.ui.LabeledRow
import com.smartattendance.core.ui.StatusChip
import com.smartattendance.core.util.Fa
import com.smartattendance.core.util.Formatters
import com.smartattendance.domain.model.ReportDetail
import com.smartattendance.domain.model.ReportSummary
import com.smartattendance.domain.usecase.GetReportDetailUseCase
import com.smartattendance.domain.usecase.GetReportsUseCase
import com.smartattendance.domain.usecase.ObserveCachedReportsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    observeCached: ObserveCachedReportsUseCase,
    private val getReports: GetReportsUseCase,
) : ViewModel() {

    /** کش Room برای نمایش فوری + رفرش از سرور */
    val cachedReports = observeCached()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init { refresh() }

    fun refresh() = viewModelScope.launch { runCatching { getReports() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    vm: ReportsViewModel = hiltViewModel(),
) {
    val reports by vm.cachedReports.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Fa.REPORTS_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Fa.BACK)
                    }
                },
            )
        },
    ) { padding ->
        if (reports.isEmpty()) {
            EmptyState(Icons.Filled.Assessment, Fa.NO_REPORTS, modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(reports, key = { it.sessionId }) { report ->
                    ReportCard(report) { onOpenDetail(report.sessionId) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportCard(report: ReportSummary, onClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(report.courseName, style = MaterialTheme.typography.titleMedium)
                Text(
                    report.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${Fa.PRESENT}: ${Fa.digits(report.presentCount)} · ${Fa.ABSENT}: ${Fa.digits(report.absentCount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ───────────────────── جزئیات گزارش ─────────────────────

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getReportDetail: GetReportDetailUseCase,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _detail = MutableStateFlow<ReportDetail?>(null)
    val detail: StateFlow<ReportDetail?> = _detail.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { getReportDetail(sessionId) }.onSuccess { _detail.value = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    onBack: () -> Unit,
    vm: ReportDetailViewModel = hiltViewModel(),
) {
    val detail by vm.detail.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Fa.REPORT_DETAIL) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Fa.BACK)
                    }
                },
            )
        },
    ) { padding ->
        val d = detail ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(d.summary.courseName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            d.summary.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(vertical = 6.dp))
                        LabeledRow(Fa.PRESENT, Fa.digits(d.summary.presentCount))
                        LabeledRow(Fa.ABSENT, Fa.digits(d.summary.absentCount))
                    }
                }
            }
            item { Text(Fa.MONITOR, style = MaterialTheme.typography.titleMedium) }
            items(d.rows, key = { it.studentId }) { row ->
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(row.studentName, style = MaterialTheme.typography.titleSmall)
                            row.timestamp?.let {
                                Text(
                                    "${Fa.ATTENDANCE_TIME}: ${Formatters.clock(it)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        StatusChip(row.status.name)
                    }
                }
            }
        }
    }
}
