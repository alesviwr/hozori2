package com.smartattendance.presentation.professor.createsession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.smartattendance.core.ui.ErrorBanner
import com.smartattendance.core.ui.LabeledRow
import com.smartattendance.core.ui.PrimaryButton
import com.smartattendance.core.util.Fa
import com.smartattendance.core.util.persianMessage
import com.smartattendance.domain.model.Course
import com.smartattendance.domain.usecase.CreateCourseUseCase
import com.smartattendance.domain.usecase.CreateSessionUseCase
import com.smartattendance.domain.usecase.GetCoursesUseCase
import com.smartattendance.domain.repository.mapThrowable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateSessionUiState(
    val courses: List<Course> = emptyList(),
    val selectedCourse: Course? = null,
    val building: String = "",
    val room: String = "",
    val windowMinutes: Int = 5,
    val loading: Boolean = false,
    val showCourseDialog: Boolean = false,
    val newCourseName: String = "",
    val newCourseBuilding: String = "",
    val newCourseRoom: String = "",
    val creatingCourse: Boolean = false,
)

@HiltViewModel
class CreateSessionViewModel @Inject constructor(
    private val getCourses: GetCoursesUseCase,
    private val createCourse: CreateCourseUseCase,
    private val createSession: CreateSessionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateSessionUiState())
    val state: StateFlow<CreateSessionUiState> = _state.asStateFlow()

    var errorMessage = MutableStateFlow<String?>(null)
        private set

    init {
        viewModelScope.launch {
            runCatching { getCourses() }.onSuccess { list ->
                _state.update { it.copy(courses = list) }
                list.firstOrNull()?.let { selectCourse(it) }
            }
        }
    }

    fun selectCourse(course: Course) = _state.update {
        it.copy(selectedCourse = course, building = course.building, room = course.room)
    }

    fun setBuilding(value: String) = _state.update { it.copy(building = value) }
    fun setRoom(value: String) = _state.update { it.copy(room = value) }
    fun setWindowMinutes(value: Int) = _state.update { it.copy(windowMinutes = value) }

    fun openCourseDialog() = _state.update {
        it.copy(showCourseDialog = true, newCourseName = "", newCourseBuilding = "", newCourseRoom = "")
    }

    fun closeCourseDialog() = _state.update { it.copy(showCourseDialog = false) }

    fun setNewCourseName(v: String) = _state.update { it.copy(newCourseName = v) }
    fun setNewCourseBuilding(v: String) = _state.update { it.copy(newCourseBuilding = v) }
    fun setNewCourseRoom(v: String) = _state.update { it.copy(newCourseRoom = v) }

    fun submitNewCourse() {
        val s = _state.value
        if (s.newCourseName.isBlank()) {
            errorMessage.value = Fa.COURSE_NAME_REQUIRED
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(creatingCourse = true) }
            errorMessage.value = null
            runCatching { createCourse(s.newCourseName, s.newCourseBuilding, s.newCourseRoom) }
                .onSuccess { course ->
                    val refreshed = runCatching { getCourses() }.getOrDefault(s.courses + course)
                    _state.update {
                        it.copy(
                            creatingCourse = false,
                            showCourseDialog = false,
                            courses = refreshed,
                            selectedCourse = course,
                            building = course.building,
                            room = course.room,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(creatingCourse = false) }
                    errorMessage.value = mapThrowable(t).persianMessage()
                }
        }
    }

    fun start(onStarted: (String) -> Unit) {
        val s = _state.value
        if (s.selectedCourse == null) {
            errorMessage.value = "درس را انتخاب کنید"
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            errorMessage.value = null
            runCatching {
                createSession(
                    com.smartattendance.domain.model.CreateSessionRequest(
                        courseId = s.selectedCourse!!.id,
                        building = s.building,
                        room = s.room,
                        windowMinutes = s.windowMinutes,
                    ),
                )
            }.onSuccess { session ->
                _state.update { it.copy(loading = false) }
                onStarted(session.id)
            }.onFailure { t ->
                val err = mapThrowable(t)
                _state.update { it.copy(loading = false) }
                errorMessage.value = err.persianMessage()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(
    onSessionStarted: (String) -> Unit,
    onBack: () -> Unit,
    vm: CreateSessionViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val errorText by vm.errorMessage.collectAsStateWithLifecycle()
    var dropdownOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Fa.CREATE_SESSION_TITLE) },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // انتخاب درس
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                ExposedDropdownMenuBox(
                    expanded = dropdownOpen,
                    onExpandedChange = { dropdownOpen = it },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = state.selectedCourse?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(Fa.COURSE) },
                        trailingIcon = { Text(if (dropdownOpen) "▲" else "▼") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                        state.courses.forEach { course ->
                            DropdownMenuItem(
                                text = { Text("${course.name} (${course.building} - ${course.room})") },
                                onClick = {
                                    vm.selectCourse(course)
                                    dropdownOpen = false
                                },
                            )
                        }
                    }
                }
                androidx.compose.material3.Button(onClick = vm::openCourseDialog) {
                    Text(Fa.NEW_COURSE)
                }
            }

            OutlinedTextField(
                value = state.building,
                onValueChange = vm::setBuilding,
                label = { Text(Fa.BUILDING) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.room,
                onValueChange = vm::setRoom,
                label = { Text(Fa.ROOM) },
                modifier = Modifier.fillMaxWidth(),
            )

            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(Fa.ATTENDANCE_WINDOW, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(5, 10, 15).forEach { minutes ->
                            FilterChip(
                                selected = state.windowMinutes == minutes,
                                onClick = { vm.setWindowMinutes(minutes) },
                                label = { Text("${Fa.digits(minutes)} ${Fa.MINUTES}") },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledRow("مدت پخش QR گردشی", "تا پایان جلسه")
                    LabeledRow("تغییر Audio Challenge", "هر ۱۲ ثانیه")
                }
            }

            errorText?.let { ErrorBanner(it) }

            PrimaryButton(
                text = Fa.START_SESSION,
                loading = state.loading,
                onClick = { vm.start(onSessionStarted) },
            )
        }
    }

    if (state.showCourseDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = vm::closeCourseDialog,
            title = { Text(Fa.NEW_COURSE) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.newCourseName,
                        onValueChange = vm::setNewCourseName,
                        label = { Text(Fa.COURSE_NAME) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.newCourseBuilding,
                        onValueChange = vm::setNewCourseBuilding,
                        label = { Text(Fa.BUILDING) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.newCourseRoom,
                        onValueChange = vm::setNewCourseRoom,
                        label = { Text(Fa.ROOM) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = vm::submitNewCourse,
                    enabled = !state.creatingCourse,
                ) { Text(if (state.creatingCourse) Fa.LOADING else Fa.SAVE) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = vm::closeCourseDialog) { Text("انصراف") }
            },
        )
    }
}
