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
import com.example.notification.NotificationHelper
import com.example.notification.NotificationScheduler
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

import com.example.data.repository.AttendanceLogResult

data class SheetNotFoundWarning(
    val eventId: Long,
    val attemptedSheetName: String,
    val message: String,
    val availableSheets: List<String> = emptyList()
)

data class DuplicateEntryWarning(
    val eventId: Long,
    val schoolOrName: String,
    val date: String,
    val message: String,
    val existingRow: Int,
    val existingSummary: String = "",
    val sumario: String = "",
    val minutos: Int = 90,
    val obs: String = "",
    val sheetName: String = "",
    val customDate: String = "",
    val docente: String = ""
)

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
    val projectHours: Int = 150,
    val isClassEndNotificationEnabled: Boolean = true,
    val isDailyDigestEnabled: Boolean = false,
    val dailyDigestHour: Int = 18,
    val dailyDigestMinute: Int = 0,
    val hasNotificationPermission: Boolean = false,
    val sheetNotFoundWarning: SheetNotFoundWarning? = null,
    val duplicateWarning: DuplicateEntryWarning? = null,
    val availableSheets: List<String> = emptyList()
)

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val calendarRepo = CalendarRepository(application)
    private val db = AppDatabase.getDatabase(application)
    private val attendanceRepo = AttendanceRepository(application, db.attendanceDao())
    private var pendingPromptEventId: Long? = null

    private val _uiState = MutableStateFlow(
        AttendanceUiState(
            selectedCalendarId = attendanceRepo.getSelectedCalendarId(),
            selectedCalendarName = attendanceRepo.getSelectedCalendarName(),
            docenteName = attendanceRepo.getDocenteName(),
            webhookUrl = attendanceRepo.getGoogleSheetWebhookUrl(),
            sheetViewUrl = attendanceRepo.getGoogleSheetViewUrl(),
            isDemoMode = attendanceRepo.isCalendarDemoMode(),
            currentDateFormatted = getFormattedCurrentDate(),
            projectHours = attendanceRepo.getProjectHours(),
            isClassEndNotificationEnabled = attendanceRepo.isClassEndNotificationEnabled(),
            isDailyDigestEnabled = attendanceRepo.isDailyDigestEnabled(),
            dailyDigestHour = attendanceRepo.getDailyDigestHour(),
            dailyDigestMinute = attendanceRepo.getDailyDigestMinute(),
            hasNotificationPermission = NotificationHelper.hasNotificationPermission(application)
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
        refreshAvailableSheets()
        viewModelScope.launch {
            attendanceRecords.collect { records ->
                val recordSheets = records.map { it.sheetName.ifBlank { it.professorEscola } }.filter { it.isNotBlank() }
                if (recordSheets.isNotEmpty()) {
                    val combined = (_uiState.value.availableSheets + recordSheets).distinct()
                    _uiState.value = _uiState.value.copy(availableSheets = combined)
                }
            }
        }
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

            val pendingId = pendingPromptEventId
            val eventToPrompt = if (pendingId != null) {
                mappedEvents.firstOrNull { it.id == pendingId }
            } else null
            if (eventToPrompt != null) {
                pendingPromptEventId = null
            }

            _uiState.value = _uiState.value.copy(
                todayEvents = mappedEvents,
                activePromptEvent = eventToPrompt ?: _uiState.value.activePromptEvent,
                isLoading = false,
                snackbarMessage = if (showFeedbackToast) {
                    if (mappedEvents.isEmpty()) {
                        "Nenhuma aula encontrada para este dia."
                    } else {
                        "Calendário atualizado: ${mappedEvents.size} aula(s) encontrada(s)."
                    }
                } else _uiState.value.snackbarMessage
            )

            if (isSameDay(selectedDate, System.currentTimeMillis())) {
                NotificationScheduler.syncAllRemindersForToday(
                    context = getApplication(),
                    events = mappedEvents,
                    classEndEnabled = _uiState.value.isClassEndNotificationEnabled,
                    dailyDigestEnabled = _uiState.value.isDailyDigestEnabled,
                    digestHour = _uiState.value.dailyDigestHour,
                    digestMinute = _uiState.value.dailyDigestMinute
                )
            }
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

        // Cancel scheduled alarm for this class since it is attended
        NotificationScheduler.cancelClassReminder(getApplication(), event.id)

        // Background persistence and sync to Google Sheets
        viewModelScope.launch {
            val result = attendanceRepo.logAttendance(
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
            handleSyncResult(event.id, targetSheet, result)
        }
    }

    fun refreshAvailableSheets() {
        viewModelScope.launch {
            val sheets = attendanceRepo.fetchAvailableSheetsFromWebhook()
            if (sheets.isNotEmpty()) {
                val combined = (sheets + _uiState.value.availableSheets).distinct()
                _uiState.value = _uiState.value.copy(availableSheets = combined)
            }
        }
    }

    private fun handleSyncResult(eventId: Long, attemptedSheetName: String, result: AttendanceLogResult) {
        val record = result.record
        val sync = result.syncResponse

        val refreshed = _uiState.value.todayEvents.map {
            if (it.id == eventId) it.copy(
                loggedRecordId = record.id,
                attendanceStatus = if (sync?.isSheetNotFound == true) AttendanceStatus.PENDING else AttendanceStatus.ATTENDED,
                schoolName = sync?.sheetUsed?.ifBlank { attemptedSheetName } ?: attemptedSheetName
            ) else it
        }

        if (sync == null) {
            _uiState.value = _uiState.value.copy(
                todayEvents = refreshed,
                snackbarMessage = "✓ Aula guardada localmente (sem webhook configurado)"
            )
            return
        }

        if (sync.isDuplicateEntry) {
            val schoolDisplay = sync.sheetUsed.ifBlank { attemptedSheetName.ifBlank { "Turma" } }
            val event = _uiState.value.todayEvents.find { it.id == eventId }
            val dateStr = if (event != null && event.startTimeMillis > 0) {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(event.startTimeMillis))
            } else {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            }

            NotificationHelper.showDuplicateEntryNotification(
                context = getApplication(),
                eventId = eventId,
                schoolOrName = schoolDisplay,
                date = dateStr,
                message = sync.message
            )

            val durationMins = if (event != null) {
                ((event.endTimeMillis - event.startTimeMillis) / (60 * 1000)).toInt().coerceIn(15, 240)
            } else 90

            _uiState.value = _uiState.value.copy(
                todayEvents = refreshed,
                duplicateWarning = DuplicateEntryWarning(
                    eventId = eventId,
                    schoolOrName = schoolDisplay,
                    date = dateStr,
                    message = sync.message,
                    existingRow = sync.existingRow,
                    existingSummary = sync.existingSummary,
                    sumario = record.sumario.ifBlank { event?.title ?: "" },
                    minutos = durationMins,
                    obs = record.obs,
                    sheetName = attemptedSheetName,
                    customDate = dateStr,
                    docente = _uiState.value.docenteName
                ),
                snackbarMessage = "⚠️ Entrada já existente nesta data. Notificação enviada!"
            )
        } else if (sync.isSheetNotFound) {
            val updatedSheets = if (sync.availableSheets.isNotEmpty()) {
                (sync.availableSheets + _uiState.value.availableSheets).distinct()
            } else {
                _uiState.value.availableSheets
            }
            _uiState.value = _uiState.value.copy(
                todayEvents = refreshed,
                availableSheets = updatedSheets,
                sheetNotFoundWarning = SheetNotFoundWarning(
                    eventId = eventId,
                    attemptedSheetName = attemptedSheetName,
                    message = sync.message,
                    availableSheets = sync.availableSheets
                ),
                snackbarMessage = "⚠️ Aba '$attemptedSheetName' não encontrada no Google Sheets! Por favor confirme ou altere o nome."
            )
        } else if (sync.success) {
            val updatedSheets = if (sync.availableSheets.isNotEmpty()) {
                (sync.availableSheets + _uiState.value.availableSheets).distinct()
            } else {
                _uiState.value.availableSheets
            }
            val rowMsg = if (sync.targetRow > 0) " (linha ${sync.targetRow})" else ""
            _uiState.value = _uiState.value.copy(
                todayEvents = refreshed,
                availableSheets = updatedSheets,
                sheetNotFoundWarning = null,
                duplicateWarning = null,
                snackbarMessage = "✓ Registada com sucesso na aba '${sync.sheetUsed}'$rowMsg!"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                todayEvents = refreshed,
                snackbarMessage = "⚠️ Guardada localmente, mas falhou envio: ${sync.message}"
            )
        }
    }

    fun dismissDuplicateWarning() {
        _uiState.value = _uiState.value.copy(duplicateWarning = null)
    }

    fun resolveDuplicateWithUpdate(warning: DuplicateEntryWarning) {
        val event = _uiState.value.todayEvents.find { it.id == warning.eventId } ?: return
        dismissDuplicateWarning()
        commitEditedEvent(
            event = event,
            sumario = warning.sumario.ifBlank { CalendarRepository.extractClassSummary(event.title) },
            minutos = warning.minutos,
            obs = warning.obs,
            sheetName = warning.sheetName,
            customDate = warning.customDate,
            docente = warning.docente.ifBlank { _uiState.value.docenteName },
            allowDuplicate = false,
            forceUpdate = true
        )
    }

    fun resolveDuplicateWithNewRow(warning: DuplicateEntryWarning) {
        val event = _uiState.value.todayEvents.find { it.id == warning.eventId } ?: return
        dismissDuplicateWarning()
        commitEditedEvent(
            event = event,
            sumario = warning.sumario.ifBlank { CalendarRepository.extractClassSummary(event.title) },
            minutos = warning.minutos,
            obs = warning.obs,
            sheetName = warning.sheetName,
            customDate = warning.customDate,
            docente = warning.docente.ifBlank { _uiState.value.docenteName },
            allowDuplicate = true,
            forceUpdate = false
        )
    }

    fun dismissSheetNotFoundWarning() {
        _uiState.value = _uiState.value.copy(sheetNotFoundWarning = null)
    }

    fun retryWithNewSheetName(eventId: Long, newSheetName: String) {
        val event = _uiState.value.todayEvents.find { it.id == eventId } ?: return
        dismissSheetNotFoundWarning()
        val durationMins = ((event.endTimeMillis - event.startTimeMillis) / (60 * 1000)).toInt().coerceIn(15, 240)
        commitEditedEvent(
            event = event,
            sumario = CalendarRepository.extractClassSummary(event.title),
            minutos = durationMins,
            obs = "",
            sheetName = newSheetName,
            customDate = "",
            docente = _uiState.value.docenteName,
            allowDuplicate = false
        )
    }

    fun openEditForWarning(warning: SheetNotFoundWarning) {
        val event = _uiState.value.todayEvents.find { it.id == warning.eventId }
        dismissSheetNotFoundWarning()
        if (event != null) {
            _uiState.value = _uiState.value.copy(activePromptEvent = event)
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

        if (_uiState.value.isClassEndNotificationEnabled && event.endTimeMillis > System.currentTimeMillis()) {
            NotificationScheduler.scheduleClassEndReminder(getApplication(), event)
        }

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

    fun saveEditedEventLocally(
        event: ClassEvent,
        sumario: String,
        minutos: Int,
        obs: String,
        sheetName: String,
        customDate: String,
        docente: String
    ) {
        val targetSheet = sheetName.ifBlank { event.schoolName.ifBlank { event.title } }
        val fullTitle = if (sumario.isNotBlank()) "${targetSheet.trim()} - ${sumario.trim()}" else targetSheet.trim()
        val durationMillis = minutos * 60 * 1000L
        val endMillis = event.startTimeMillis + durationMillis

        val updated = _uiState.value.todayEvents.map {
            if (it.id == event.id) it.copy(
                schoolName = targetSheet,
                title = fullTitle,
                endTimeMillis = endMillis
            ) else it
        }
        _uiState.value = _uiState.value.copy(
            todayEvents = updated,
            activePromptEvent = null,
            snackbarMessage = "✓ Alterações guardadas localmente."
        )

        viewModelScope.launch {
            if (docente.isNotBlank() && docente != _uiState.value.docenteName) {
                attendanceRepo.setDocenteName(docente)
                _uiState.value = _uiState.value.copy(docenteName = docente)
            }

            if (event.loggedRecordId != null) {
                val eventDateStr = if (customDate.isNotBlank()) customDate else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(event.startTimeMillis))
                val rec = attendanceRepo.saveRecordLocally(
                    eventId = event.id,
                    professorEscola = targetSheet,
                    sumario = sumario.ifBlank { CalendarRepository.extractClassSummary(event.title) },
                    minutos = minutos,
                    obs = obs,
                    sheetName = targetSheet,
                    eventTitle = fullTitle,
                    customDate = eventDateStr,
                    eventDateMillis = event.startTimeMillis
                )
                val refreshed = _uiState.value.todayEvents.map {
                    if (it.id == event.id) it.copy(loggedRecordId = rec.id) else it
                }
                _uiState.value = _uiState.value.copy(todayEvents = refreshed)
            }
        }
    }

    fun commitEditedEvent(
        event: ClassEvent,
        sumario: String,
        minutos: Int,
        obs: String,
        sheetName: String,
        customDate: String,
        docente: String,
        allowDuplicate: Boolean = false,
        forceUpdate: Boolean = false
    ) {
        saveEditedEvent(event, sumario, minutos, obs, sheetName, customDate, docente, allowDuplicate, forceUpdate)
    }

    fun saveEditedEvent(
        event: ClassEvent,
        sumario: String,
        minutos: Int,
        obs: String,
        sheetName: String,
        customDate: String,
        docente: String,
        allowDuplicate: Boolean = false,
        forceUpdate: Boolean = false
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
            } else if (forceUpdate) {
                "✓ Linha existente atualizada na aba '$targetSheet'"
            } else {
                "✓ Aula guardada na aba '$targetSheet' (sem duplicados)"
            }
        )

        // Cancel reminder alarm since class is logged
        NotificationScheduler.cancelClassReminder(getApplication(), event.id)

        viewModelScope.launch {
            if (docente.isNotBlank() && docente != _uiState.value.docenteName) {
                attendanceRepo.setDocenteName(docente)
                _uiState.value = _uiState.value.copy(docenteName = docente)
            }

            val eventDateStr = if (customDate.isNotBlank()) customDate else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(event.startTimeMillis))
            val result = attendanceRepo.logAttendance(
                eventId = event.id,
                professorEscola = targetSheet,
                sumario = sumario.ifBlank { CalendarRepository.extractClassSummary(event.title) },
                minutos = minutos,
                obs = obs,
                sheetName = targetSheet,
                eventTitle = event.title,
                customDate = eventDateStr,
                eventDateMillis = event.startTimeMillis,
                allowDuplicate = allowDuplicate,
                forceUpdate = forceUpdate
            )
            handleSyncResult(event.id, targetSheet, result)
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
            val result = attendanceRepo.logAttendance(
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
            handleSyncResult(event.id, targetSheet, result)
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
                val result = attendanceRepo.logAttendance(
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
                    todayEvents = listOf(newEvent.copy(loggedRecordId = result.record.id)) + _uiState.value.todayEvents.filter { it.id != newEvent.id },
                    isAddEventOpen = false
                )
                handleSyncResult(eventId, schoolName.trim(), result)
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

    fun updateSettings(
        docenteName: String,
        webhookUrl: String,
        sheetViewUrl: String,
        classEndEnabled: Boolean = _uiState.value.isClassEndNotificationEnabled,
        dailyDigestEnabled: Boolean = _uiState.value.isDailyDigestEnabled,
        dailyDigestHour: Int = _uiState.value.dailyDigestHour,
        dailyDigestMinute: Int = _uiState.value.dailyDigestMinute
    ) {
        attendanceRepo.setDocenteName(docenteName)
        attendanceRepo.setGoogleSheetWebhookUrl(webhookUrl)
        attendanceRepo.setGoogleSheetViewUrl(sheetViewUrl)
        attendanceRepo.setClassEndNotificationEnabled(classEndEnabled)
        attendanceRepo.setDailyDigestEnabled(dailyDigestEnabled)
        attendanceRepo.setDailyDigestTime(dailyDigestHour, dailyDigestMinute)

        _uiState.value = _uiState.value.copy(
            docenteName = docenteName,
            webhookUrl = webhookUrl,
            sheetViewUrl = sheetViewUrl,
            isClassEndNotificationEnabled = classEndEnabled,
            isDailyDigestEnabled = dailyDigestEnabled,
            dailyDigestHour = dailyDigestHour,
            dailyDigestMinute = dailyDigestMinute,
            isSettingsOpen = false,
            snackbarMessage = "Definições e notificações guardadas com sucesso."
        )

        refreshAvailableSheets()

        NotificationScheduler.syncAllRemindersForToday(
            context = getApplication(),
            events = _uiState.value.todayEvents,
            classEndEnabled = classEndEnabled,
            dailyDigestEnabled = dailyDigestEnabled,
            digestHour = dailyDigestHour,
            digestMinute = dailyDigestMinute
        )
    }

    fun sendTestNotification() {
        NotificationHelper.showTestNotification(getApplication())
        _uiState.value = _uiState.value.copy(snackbarMessage = "Notificação de teste enviada!")
    }

    fun setNotificationPermissionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasNotificationPermission = granted)
    }

    fun triggerEventPromptById(eventId: Long) {
        val event = _uiState.value.todayEvents.firstOrNull { it.id == eventId }
        if (event != null) {
            showAttendancePrompt(event)
        } else {
            pendingPromptEventId = eventId
        }
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
