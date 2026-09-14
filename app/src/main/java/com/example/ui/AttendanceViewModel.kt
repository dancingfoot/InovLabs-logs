package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.CalendarInfo
import com.example.data.model.ClassEvent
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AttendanceUiState(
    val calendars: List<CalendarInfo> = emptyList(),
    val selectedCalendarId: Long? = null,
    val selectedCalendarName: String = "Todos os Calendários",
    val todayEvents: List<ClassEvent> = emptyList(),
    val isLoading: Boolean = false,
    val hasCalendarPermission: Boolean = false,
    val activePromptEvent: ClassEvent? = null,
    val isSpreadsheetOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isCalendarPickerOpen: Boolean = false,
    val isAddEventOpen: Boolean = false,
    val snackbarMessage: String? = null,
    val docenteName: String = "Docente InovLabs",
    val webhookUrl: String = "",
    val sheetViewUrl: String = "",
    val isDemoMode: Boolean = false,
    val currentDateFormatted: String = "",
    val selectedDateMillis: Long = System.currentTimeMillis(),
    val isSelectedDateToday: Boolean = true,
    val projectHours: Int = 150
)

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val calendarRepo = CalendarRepository(application)
    private val db = AppDatabase.getDatabase(application)
    private val attendanceRepo = AttendanceRepository(application, db.attendanceDao())

    private val _uiState = MutableStateFlow(
        AttendanceUiState(
            selectedCalendarId = attendanceRepo.getSelectedCalendarId(),
            selectedCalendarName = attendanceRepo.getSelectedCalendarName(),
            docenteName = attendanceRepo.getDocenteName(),
            webhookUrl = attendanceRepo.getGoogleSheetWebhookUrl(),
            sheetViewUrl = attendanceRepo.getGoogleSheetViewUrl(),
            isDemoMode = attendanceRepo.isCalendarDemoMode(),
            currentDateFormatted = getFormattedCurrentDate(),
            projectHours = attendanceRepo.getProjectHours()
        )
    )
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    val attendanceRecords: StateFlow<List<AttendanceRecord>> = attendanceRepo.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalMinutesSum: StateFlow<Int> = attendanceRepo.totalMinutes
        .combine(attendanceRecords) { minutes, _ -> minutes ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        refreshData()
    }

    fun setCalendarPermissionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasCalendarPermission = granted)
        loadCalendars()
        loadTodayEvents()
    }

    fun refreshData(showFeedbackToast: Boolean = false) {
        val isToday = isSameDay(_uiState.value.selectedDateMillis, System.currentTimeMillis())
        _uiState.value = _uiState.value.copy(
            currentDateFormatted = formatDisplayDate(_uiState.value.selectedDateMillis, isToday)
        )
        loadCalendars()
        loadTodayEvents(showFeedbackToast = showFeedbackToast)
    }

    fun changeSelectedDate(daysOffset: Int) {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = _uiState.value.selectedDateMillis
            add(java.util.Calendar.DAY_OF_YEAR, daysOffset)
        }
        val isToday = isSameDay(cal.timeInMillis, System.currentTimeMillis())
        _uiState.value = _uiState.value.copy(
            selectedDateMillis = cal.timeInMillis,
            isSelectedDateToday = isToday,
            currentDateFormatted = formatDisplayDate(cal.timeInMillis, isToday)
        )
        loadTodayEvents(showFeedbackToast = false)
    }

    fun goToToday() {
        val now = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            selectedDateMillis = now,
            isSelectedDateToday = true,
            currentDateFormatted = formatDisplayDate(now, true)
        )
        loadTodayEvents(showFeedbackToast = false)
    }

    fun setSelectedDate(millis: Long) {
        val isToday = isSameDay(millis, System.currentTimeMillis())
        _uiState.value = _uiState.value.copy(
            selectedDateMillis = millis,
            isSelectedDateToday = isToday,
            currentDateFormatted = formatDisplayDate(millis, isToday)
        )
        loadTodayEvents(showFeedbackToast = false)
    }

    fun loadCalendars() {
        viewModelScope.launch {
            val calendars = calendarRepo.getDeviceCalendars()
            _uiState.value = _uiState.value.copy(calendars = calendars)
        }
    }

    fun selectCalendar(calendar: CalendarInfo?) {
        val id = calendar?.id
        val name = calendar?.displayName ?: "Todos os Calendários"
        attendanceRepo.setSelectedCalendarId(id)
        attendanceRepo.setSelectedCalendarName(name)
        _uiState.value = _uiState.value.copy(
            selectedCalendarId = id,
            selectedCalendarName = name,
            isCalendarPickerOpen = false
        )
        loadTodayEvents(showFeedbackToast = true)
    }

    fun loadTodayEvents(showFeedbackToast: Boolean = false) {
        viewModelScope.launch {
            // Only show loader on initial empty load to keep UI interaction smooth and non-choppy
            if (_uiState.value.todayEvents.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            val records = attendanceRecords.value
            val selectedDate = _uiState.value.selectedDateMillis
            val selectedDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selectedDate))

            // Clean startup: only load demo events if test mode was explicitly enabled
            val rawEvents = if (_uiState.value.isDemoMode) {
                CalendarRepository.getSampleTodayEvents(selectedDate)
            } else if (_uiState.value.hasCalendarPermission) {
                calendarRepo.getEventsForDate(_uiState.value.selectedCalendarId, selectedDate)
            } else {
                emptyList()
            }

            // Sync with attendance status from Room DB for this specific day
            val mappedEvents = rawEvents.map { event ->
                val eventDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(event.startTimeMillis))
                val matchingRecord = records.firstOrNull { record ->
                    (record.eventId == event.id && (record.date == eventDateStr || record.date == selectedDateStr)) ||
                    (record.date == eventDateStr &&
                     (record.sheetName.equals(event.schoolName, ignoreCase = true) || record.professorEscola.equals(event.schoolName, ignoreCase = true)) &&
                     (record.sumario.equals(CalendarRepository.extractClassSummary(event.title), ignoreCase = true) || record.eventTitle.equals(event.title, ignoreCase = true)))
                }

                if (matchingRecord != null) {
                    event.copy(
                        attendanceStatus = AttendanceStatus.ATTENDED,
                        loggedRecordId = matchingRecord.id
                    )
                } else {
                    event
                }
            }

            _uiState.value = _uiState.value.copy(
                todayEvents = mappedEvents,
                isLoading = false,
                snackbarMessage = if (showFeedbackToast) {
                    if (mappedEvents.isEmpty()) {
                        "Nenhuma aula encontrada para este dia."
                    } else {
                        "Calendário atualizado: ${mappedEvents.size} aula(s) encontrada(s)."
                    }
                } else _uiState.value.snackbarMessage
            )
        }
    }

    fun markAttendedDirect(event: ClassEvent) {
        val targetSheet = event.schoolName.ifBlank { event.title }
        val sumario = CalendarRepository.extractClassSummary(event.title)
        val mins = ((event.endTimeMillis - event.startTimeMillis) / (1000 * 60)).toInt().coerceAtLeast(45)
        val eventDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(event.startTimeMillis))

        // Instant optimistic update (0ms UI lag, direct on card without any popup)
        val updated = _uiState.value.todayEvents.map {
            if (it.id == event.id) it.copy(attendanceStatus = AttendanceStatus.ATTENDED) else it
        }
        _uiState.value = _uiState.value.copy(
            todayEvents = updated,
            activePromptEvent = null,
            snackbarMessage = "✓ Registada com sucesso na aba '$targetSheet'"
        )

        // Background persistence and sync to Google Sheets
        viewModelScope.launch {
            val record = attendanceRepo.logAttendance(
                eventId = event.id,
                professorEscola = event.schoolName.ifBlank { event.title },
                sumario = sumario.ifBlank { "Aula lecionada" },
                minutos = mins,
                obs = "Presença confirmada",
                sheetName = targetSheet,
                eventTitle = event.title,
                customDate = eventDateStr,
                eventDateMillis = event.startTimeMillis
            )
            val refreshed = _uiState.value.todayEvents.map {
                if (it.id == event.id) it.copy(loggedRecordId = record.id) else it
            }
            _uiState.value = _uiState.value.copy(todayEvents = refreshed)
        }
    }

    fun resetAttendanceStatus(event: ClassEvent) {
        val updated = _uiState.value.todayEvents.map {
            if (it.id == event.id) it.copy(attendanceStatus = AttendanceStatus.PENDING, loggedRecordId = null) else it
        }
        _uiState.value = _uiState.value.copy(
            todayEvents = updated,
            activePromptEvent = null,
            snackbarMessage = "Registo reposto para pendente e removido da folha."
        )

        viewModelScope.launch {
            val record = if (event.loggedRecordId != null) {
                attendanceRecords.value.firstOrNull { it.id == event.loggedRecordId }
            } else {
                val eventDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(event.startTimeMillis))
                attendanceRecords.value.firstOrNull { it.eventId == event.id && it.date == eventDateStr }
                    ?: attendanceRecords.value.firstOrNull { it.eventId == event.id }
            }
            if (record != null) {
                attendanceRepo.deleteRecord(record)
            }
        }
    }

    fun showAttendancePrompt(event: ClassEvent) {
        _uiState.value = _uiState.value.copy(activePromptEvent = event)
    }

    fun dismissAttendancePrompt() {
        _uiState.value = _uiState.value.copy(activePromptEvent = null)
    }

    fun saveEditedEvent(
        event: ClassEvent,
        sumario: String,
        minutos: Int,
        obs: String,
        sheetName: String,
        customDate: String,
        docente: String,
        allowDuplicate: Boolean = false
    ) {
        val targetSheet = sheetName.ifBlank { event.schoolName.ifBlank { event.title } }

        // Optimistic UI update
        val updated = _uiState.value.todayEvents.map {
            if (it.id == event.id) it.copy(
                attendanceStatus = AttendanceStatus.ATTENDED,
                schoolName = targetSheet
            ) else it
        }
        _uiState.value = _uiState.value.copy(
            todayEvents = updated,
            activePromptEvent = null,
            snackbarMessage = if (allowDuplicate) {
                "✓ Nova linha (duplicado) guardada na aba '$targetSheet'"
            } else {
                "✓ Aula guardada na aba '$targetSheet' (sem duplicados)"
            }
        )

        viewModelScope.launch {
            if (docente.isNotBlank() && docente != _uiState.value.docenteName) {
                attendanceRepo.setDocenteName(docente)
                _uiState.value = _uiState.value.copy(docenteName = docente)
            }

            val eventDateStr = if (customDate.isNotBlank()) customDate else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(event.startTimeMillis))
            val record = attendanceRepo.logAttendance(
                eventId = event.id,
                professorEscola = targetSheet,
                sumario = sumario.ifBlank { CalendarRepository.extractClassSummary(event.title) },
                minutos = minutos,
                obs = obs,
                sheetName = targetSheet,
                eventTitle = event.title,
                customDate = eventDateStr,
                eventDateMillis = event.startTimeMillis,
                allowDuplicate = allowDuplicate
            )

            val refreshed = _uiState.value.todayEvents.map {
                if (it.id == event.id) it.copy(
                    attendanceStatus = AttendanceStatus.ATTENDED,
                    loggedRecordId = record.id,
                    schoolName = targetSheet
                ) else it
            }
            _uiState.value = _uiState.value.copy(todayEvents = refreshed)
        }
    }

    fun markNotAttended(event: ClassEvent) {
        val updated = _uiState.value.todayEvents.map {
            if (it.id == event.id) it.copy(attendanceStatus = AttendanceStatus.NOT_ATTENDED) else it
        }
        _uiState.value = _uiState.value.copy(
            todayEvents = updated,
            activePromptEvent = null,
            snackbarMessage = "Aula '${event.schoolName}' desconsiderada."
        )
    }

    fun markAttended(
        event: ClassEvent,
        sumario: String,
        minutos: Int,
        obs: String,
        sheetName: String = ""
    ) {
        viewModelScope.launch {
            val targetSheet = sheetName.ifBlank { event.schoolName.ifBlank { event.title } }
            val eventDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(event.startTimeMillis))
            val record = attendanceRepo.logAttendance(
                eventId = event.id,
                professorEscola = event.schoolName.ifBlank { event.title },
                sumario = sumario.ifBlank { CalendarRepository.extractClassSummary(event.title) },
                minutos = minutos,
                obs = obs,
                sheetName = targetSheet,
                eventTitle = event.title,
                customDate = eventDateStr,
                eventDateMillis = event.startTimeMillis
            )

            val updated = _uiState.value.todayEvents.map {
                if (it.id == event.id) it.copy(
                    attendanceStatus = AttendanceStatus.ATTENDED,
                    loggedRecordId = record.id
                ) else it
            }

            val syncNote = if (record.syncedToGoogleSheets) " e sincronizado na aba '$targetSheet' do Google Sheets!" else " e registado na aba '$targetSheet'!"

            _uiState.value = _uiState.value.copy(
                todayEvents = updated,
                activePromptEvent = null,
                snackbarMessage = "Aula #${record.lessonNumber} guardada para '$targetSheet'$syncNote"
            )
        }
    }

    fun deleteRecord(record: AttendanceRecord) {
        viewModelScope.launch {
            attendanceRepo.deleteRecord(record)
            val updated = _uiState.value.todayEvents.map {
                if (it.loggedRecordId == record.id || it.id == record.eventId) {
                    it.copy(attendanceStatus = AttendanceStatus.PENDING, loggedRecordId = null)
                } else it
            }
            _uiState.value = _uiState.value.copy(
                todayEvents = updated,
                snackbarMessage = "Eliminada linha #${record.lessonNumber}"
            )
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            attendanceRepo.clearAllRecords()
            val updated = _uiState.value.todayEvents.map {
                it.copy(attendanceStatus = AttendanceStatus.PENDING, loggedRecordId = null)
            }
            _uiState.value = _uiState.value.copy(
                todayEvents = updated,
                snackbarMessage = "Todas as linhas de presença foram limpas."
            )
        }
    }

    fun addManualClassEvent(
        schoolName: String,
        sumario: String,
        durationMinutes: Int,
        targetDateMillis: Long,
        customDateStr: String = "",
        docente: String = "",
        obs: String = "",
        markAsAttended: Boolean = true,
        markAsNotAttended: Boolean = false
    ) {
        viewModelScope.launch {
            if (docente.isNotBlank() && docente != _uiState.value.docenteName) {
                attendanceRepo.setDocenteName(docente)
                _uiState.value = _uiState.value.copy(docenteName = docente)
            }

            val targetDate = if (customDateStr.isNotBlank()) {
                try {
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(customDateStr)?.time ?: targetDateMillis
                } catch (e: Exception) {
                    targetDateMillis
                }
            } else {
                targetDateMillis
            }

            val calId = _uiState.value.selectedCalendarId ?: 0L
            val isTargetToday = isSameDay(targetDate, System.currentTimeMillis())
            val nowCal = java.util.Calendar.getInstance()

            val targetCal = java.util.Calendar.getInstance().apply {
                timeInMillis = targetDate
                if (isTargetToday) {
                    set(java.util.Calendar.HOUR_OF_DAY, nowCal.get(java.util.Calendar.HOUR_OF_DAY))
                    set(java.util.Calendar.MINUTE, nowCal.get(java.util.Calendar.MINUTE))
                } else {
                    set(java.util.Calendar.HOUR_OF_DAY, 10)
                    set(java.util.Calendar.MINUTE, 0)
                }
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val startMillis = targetCal.timeInMillis
            val endMillis = startMillis + (durationMinutes * 60 * 1000L)

            val fullTitle = if (sumario.isNotBlank()) {
                "${schoolName.trim()} - ${sumario.trim()}"
            } else {
                schoolName.trim()
            }

            var createdId = -1L
            if (_uiState.value.hasCalendarPermission) {
                createdId = calendarRepo.createCalendarEvent(
                    title = fullTitle,
                    startHour = targetCal.get(java.util.Calendar.HOUR_OF_DAY),
                    startMinute = targetCal.get(java.util.Calendar.MINUTE),
                    durationMinutes = durationMinutes,
                    calendarId = calId,
                    dateMillis = targetDate
                )
            }

            val eventId = if (createdId > 0) createdId else System.currentTimeMillis()
            val initialStatus = when {
                markAsAttended -> AttendanceStatus.ATTENDED
                markAsNotAttended -> AttendanceStatus.NOT_ATTENDED
                else -> AttendanceStatus.PENDING
            }

            val newEvent = ClassEvent(
                id = eventId,
                calendarId = calId,
                title = fullTitle,
                schoolName = schoolName.trim(),
                startTimeMillis = startMillis,
                endTimeMillis = endMillis,
                location = "Sala de Aula",
                attendanceStatus = initialStatus
            )

            val dateFormatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(targetDate))

            if (markAsAttended) {
                val record = attendanceRepo.logAttendance(
                    eventId = eventId,
                    professorEscola = schoolName.trim(),
                    sumario = sumario.trim().ifBlank { "Robótica Educativa" },
                    minutos = durationMinutes,
                    obs = obs.trim(),
                    sheetName = schoolName.trim(),
                    eventTitle = fullTitle,
                    customDate = dateFormatted,
                    eventDateMillis = startMillis,
                    allowDuplicate = false
                )
                _uiState.value = _uiState.value.copy(
                    todayEvents = listOf(newEvent.copy(loggedRecordId = record.id)) + _uiState.value.todayEvents.filter { it.id != newEvent.id },
                    isAddEventOpen = false,
                    snackbarMessage = "✓ Aula adicionada e presença registada na aba '${schoolName.trim()}'!"
                )
            } else if (markAsNotAttended) {
                _uiState.value = _uiState.value.copy(
                    todayEvents = listOf(newEvent) + _uiState.value.todayEvents.filter { it.id != newEvent.id },
                    isAddEventOpen = false,
                    snackbarMessage = "Aula '${schoolName.trim()}' adicionada como não assistida."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    todayEvents = listOf(newEvent) + _uiState.value.todayEvents.filter { it.id != newEvent.id },
                    isAddEventOpen = false,
                    snackbarMessage = "Aula '$fullTitle' agendada para $dateFormatted!"
                )
            }
            loadTodayEvents()
        }
    }

    fun addManualClassEvent(title: String, durationMinutes: Int, dateMillis: Long? = null) {
        val school = CalendarRepository.extractSchoolName(title).ifBlank { title }
        val sumario = CalendarRepository.extractClassSummary(title).ifBlank { "Robótica" }
        addManualClassEvent(
            schoolName = school,
            sumario = sumario,
            durationMinutes = durationMinutes,
            targetDateMillis = dateMillis ?: _uiState.value.selectedDateMillis,
            markAsAttended = false
        )
    }

    fun updateSettings(docenteName: String, webhookUrl: String, sheetViewUrl: String) {
        attendanceRepo.setDocenteName(docenteName)
        attendanceRepo.setGoogleSheetWebhookUrl(webhookUrl)
        attendanceRepo.setGoogleSheetViewUrl(sheetViewUrl)
        _uiState.value = _uiState.value.copy(
            docenteName = docenteName,
            webhookUrl = webhookUrl,
            sheetViewUrl = sheetViewUrl,
            isSettingsOpen = false,
            snackbarMessage = "Definições guardadas com sucesso."
        )
    }

    fun importCsv(csvContent: String) {
        viewModelScope.launch {
            val (count, error) = attendanceRepo.importCsvContent(csvContent)
            if (error != null) {
                _uiState.value = _uiState.value.copy(snackbarMessage = error)
            } else {
                _uiState.value = _uiState.value.copy(
                    projectHours = attendanceRepo.getProjectHours(),
                    snackbarMessage = "Importadas com sucesso $count linhas de presença!"
                )
                loadTodayEvents()
            }
        }
    }

    fun toggleDemoMode(enabled: Boolean) {
        attendanceRepo.setCalendarDemoMode(enabled)
        _uiState.value = _uiState.value.copy(isDemoMode = enabled)
        loadTodayEvents()
    }

    fun generateTestSchedule() {
        attendanceRepo.setCalendarDemoMode(true)
        _uiState.value = _uiState.value.copy(
            isDemoMode = true,
            snackbarMessage = "Aulas de teste geradas: CEPAO e LiceuOeiras."
        )
        loadTodayEvents()
    }

    fun clearTestSchedule() {
        attendanceRepo.setCalendarDemoMode(false)
        _uiState.value = _uiState.value.copy(
            isDemoMode = false,
            snackbarMessage = "Modo de teste desativado. Mostrando calendário normal."
        )
        loadTodayEvents()
    }

    fun setSpreadsheetOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isSpreadsheetOpen = open)
    }

    fun setSettingsOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isSettingsOpen = open)
    }

    fun setCalendarPickerOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isCalendarPickerOpen = open)
    }

    fun setAddEventOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isAddEventOpen = open)
    }

    fun clearSnackbarMessage() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun shareCsv(context: Context) {
        val file = attendanceRepo.exportCsvFile(
            records = attendanceRecords.value,
            totalMinutesSum = totalMinutesSum.value,
            projectHours = _uiState.value.projectHours
        )
        if (file == null) {
            _uiState.value = _uiState.value.copy(snackbarMessage = "Nenhum dado de presença para exportar.")
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "InovLabs_logs - ${getFormattedCurrentDate()}")
                putExtra(Intent.EXTRA_TEXT, "Ficheiro CSV com o registo de presenças das aulas InovLabs.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Partilhar Folha de Cálculo / CSV"))
        } catch (e: Exception) {
            // Fallback to direct text share if file provider is not configured
            val text = attendanceRepo.generateCsv(
                records = attendanceRecords.value,
                totalMinutesSum = totalMinutesSum.value,
                projectHours = _uiState.value.projectHours
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "InovLabs_logs")
            }
            context.startActivity(Intent.createChooser(sendIntent, "Partilhar Conteúdo CSV"))
        }
    }

    fun getCsvText(): String {
        return attendanceRepo.generateCsv(
            records = attendanceRecords.value,
            totalMinutesSum = totalMinutesSum.value,
            projectHours = _uiState.value.projectHours
        )
    }

    private fun getFormattedCurrentDate(): String {
        return formatDisplayDate(System.currentTimeMillis(), isToday = true)
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun formatDisplayDate(dateMillis: Long, isToday: Boolean): String {
        val sdf = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "PT"))
        val formatted = sdf.format(Date(dateMillis))
        val capitalized = formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "PT")) else it.toString() }
        return if (isToday) "Hoje • $capitalized" else capitalized
    }
}
