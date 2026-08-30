package com.smartattendance.presentation.professor.livesession

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.smartattendance.audio.TonePlayer
import com.smartattendance.core.theme.StatusPending
import com.smartattendance.core.theme.StatusPresent
import com.smartattendance.core.ui.EmptyState
import com.smartattendance.core.ui.PrimaryButton
import com.smartattendance.core.ui.StatCard
import com.smartattendance.core.ui.StatusChip
import com.smartattendance.core.util.Fa
import com.smartattendance.core.util.Formatters
import com.smartattendance.domain.model.MonitorData
import com.smartattendance.domain.usecase.CloseSessionUseCase
import com.smartattendance.domain.usecase.GetMonitorUseCase
import com.smartattendance.domain.usecase.PollAudioChallengeUseCase
import com.smartattendance.domain.usecase.PollQrTokenUseCase
import com.smartattendance.qr.QrGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pollQrToken: PollQrTokenUseCase,
    private val pollAudioChallenge: PollAudioChallengeUseCase,
    private val getMonitor: GetMonitorUseCase,
    private val closeSession: CloseSessionUseCase,
    private val tonePlayer: TonePlayer,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _challengeLabel = MutableStateFlow("")
    val challengeLabel: StateFlow<String> = _challengeLabel.asStateFlow()

    private val _monitor = MutableStateFlow<MonitorData?>(null)
    val monitor: StateFlow<MonitorData?> = _monitor.asStateFlow()

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    init {
        // حلقه ۱: توکن QR گردشی (سرور هر ۳ ثانیه rotate می‌کند)
        viewModelScope.launch {
            while (isActive && !_closed.value) {
                runCatching { pollQrToken(sessionId) }.onSuccess { token ->
                    _qrBitmap.value = QrGenerator.generate(token.fullToken)
                    val wait = (token.expiresAt - System.currentTimeMillis()).coerceAtLeast(150L)
                    delay(wait)
                }.onFailure { delay(1200) }
            }
        }

        // حلقه ۲: پخش Audio Challenge (سرور هر ۱۲ ثانیه چالش تازه می‌سازد)
        viewModelScope.launch {
            while (isActive && !_closed.value) {
                runCatching { pollAudioChallenge(sessionId) }.onSuccess { challenge ->
                    _challengeLabel.value = challenge.challengeId
                    tonePlayer.play(challenge.token)
                    val wait = (challenge.expiresAt - System.currentTimeMillis()).coerceAtLeast(300L)
                    delay(wait)
                }.onFailure { delay(1200) }
            }
        }

        // حلقه ۳: مانیتور زنده + شمارش معکوس
        viewModelScope.launch {
            while (isActive && !_closed.value) {
                runCatching { getMonitor(sessionId) }.onSuccess { _monitor.value = it }
                delay(2500)
            }
        }
        viewModelScope.launch {
            while (isActive && !_closed.value) {
                _monitor.value?.let { _remainingMs.value = (it.session.expiresAt - System.currentTimeMillis()).coerceAtLeast(0) }
                delay(1000)
            }
        }
    }

    fun endSession() = viewModelScope.launch {
        _closed.value = true
        tonePlayer.stop()
        runCatching { closeSession(sessionId) }
    }

    override fun onCleared() {
        tonePlayer.stop()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSessionScreen(
    onClosed: () -> Unit,
    vm: LiveSessionViewModel = hiltViewModel(),
) {
    val qr by vm.qrBitmap.collectAsStateWithLifecycle()
    val challenge by vm.challengeLabel.collectAsStateWithLifecycle()
    val monitor by vm.monitor.collectAsStateWithLifecycle()
    val remaining by vm.remainingMs.collectAsStateWithLifecycle()
    val closed by vm.closed.collectAsStateWithLifecycle()
    var confirmClose by remember { mutableStateOf(false) }

    if (closed) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(150)
            onClosed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(monitor?.session?.courseName ?: Fa.LIVE_TITLE, style = MaterialTheme.typography.titleMedium)
                        if (monitor != null) {
                            Text(
                                "${Fa.REMAINING}: ${Formatters.countdown(remaining)} · ${monitor!!.session.building} - ${monitor!!.session.room}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ───────── QR گردش ─────────
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val bitmap = qr
                        if (bitmap == null) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.aspectRatio(1f).padding(48.dp),
                            )
                        } else {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Dynamic QR",
                                modifier = Modifier.aspectRatio(1f).fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            Fa.QR_ROTATES,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        if (challenge.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Text(
                                    "  ${Fa.AUDIO_CHALLENGE} #${challenge}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }

            // ───────── شمارنده‌ها ─────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Fa.PRESENT, monitor?.presentCount ?: 0, StatusPresent, Modifier.weight(1f))
                    StatCard(Fa.PENDING, monitor?.pendingCount ?: 0, StatusPending, Modifier.weight(1f))
                    StatCard(Fa.ABSENT, monitor?.absentCount ?: 0, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
            }

            // ───────── مانیتور دانشجویان ─────────
            item { Text(Fa.MONITOR, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth()) }

            val rows = monitor?.rows.orEmpty()
            if (rows.isEmpty()) {
                item { EmptyState(Icons.Filled.GraphicEq, "در انتظار اسکن دانشجویان...") }
            } else {
                items(rows) { row ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
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
                                        Formatters.clock(it),
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

            // ───────── پایان جلسه ─────────
            item {
                PrimaryButton(
                    text = Fa.CLOSE_SESSION,
                    onClick = { confirmClose = true },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(Fa.CLOSE_CONFIRM_TITLE) },
            text = { Text(Fa.CLOSE_CONFIRM_TEXT) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    vm.endSession()
                }) { Text("بله، پایان جلسه") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text(Fa.BACK) }
            },
        )
    }
}
