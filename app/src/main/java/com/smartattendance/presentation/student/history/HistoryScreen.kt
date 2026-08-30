package com.smartattendance.presentation.student.history

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
import androidx.compose.material.icons.filled.FactCheck
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.smartattendance.core.ui.EmptyState
import com.smartattendance.core.ui.StatusChip
import com.smartattendance.core.util.Fa
import com.smartattendance.domain.model.StudentAttendanceItem
import com.smartattendance.domain.usecase.GetHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * تاریخچه حضور دانشجو — فقط داده‌های خود دانشجو؛
 * سرور هرگز رکورد سایرین را برای این endpoint برنمی‌گرداند.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getHistory: GetHistoryUseCase,
) : ViewModel() {

    private val _items = MutableStateFlow<List<StudentAttendanceItem>>(emptyList())
    val items: StateFlow<List<StudentAttendanceItem>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { getHistory() }.onSuccess { _items.value = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    vm: HistoryViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Fa.HISTORY_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Fa.BACK)
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(Icons.Filled.FactCheck, "هنوز جلسه‌ای ثبت نشده است", modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items) { item ->
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
