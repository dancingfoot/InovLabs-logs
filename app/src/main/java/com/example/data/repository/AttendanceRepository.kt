package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.AttendanceDao
import com.example.data.model.AttendanceRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AttendanceRepository(
    private val context: Context,
    private val attendanceDao: AttendanceDao
) {
    companion object {
        const val DEFAULT_WEBHOOK_URL =
            "https://script.google.com/macros/s/AKfycbzOdRgX5FErlY6dSgrI7yrsnUTSQVy2-Flno6xOjWgw0bELo6Jh7e6hYpJ8phyUgiBX/exec"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("class_attendance_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val allRecords: Flow<List<AttendanceRecord>> = attendanceDao.getAllRecords()
    val totalRecordsCount: Flow<Int> = attendanceDao.getRecordCount()
    val totalMinutes: Flow<Int?> = attendanceDao.getTotalMinutes()

    suspend fun getRecordByEventId(eventId: Long): AttendanceRecord? = withContext(Dispatchers.IO) {
        attendanceDao.getRecordByEventId(eventId)
    }

    suspend fun insertAll(records: List<AttendanceRecord>) = withContext(Dispatchers.IO) {
        records.forEach { attendanceDao.insertRecord(it) }
    }

    suspend fun logAttendance(
        eventId: Long,
        professorEscola: String,
        sumario: String,
        minutos: Int,
        obs: String = "",
        sheetName: String = "",
        eventTitle: String = "",
        customDate: String? = null,
        eventDateMillis: Long = 0L,
        allowDuplicate: Boolean = false
    ): AttendanceRecord = withContext(Dispatchers.IO) {
        val targetSheet = sheetName.ifBlank { professorEscola.ifBlank { eventTitle } }
        val docenteName = getDocenteName()
        val formattedDate = customDate?.ifBlank { null } ?: (
            if (eventDateMillis > 0L) {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(eventDateMillis))
            } else {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            }
        )

        // 1. DEDUPLICATION CHECK:
        // If allowDuplicate is true, we intentionally skip deduplication to create a fresh line
        val existingRecord = if (allowDuplicate) {
            null
        } else if (eventId > 0) {
            attendanceDao.getRecordByEventIdAndDate(eventId, formattedDate)
                ?: attendanceDao.findDuplicateRecord(formattedDate, targetSheet, sumario)
        } else {
            attendanceDao.findDuplicateRecord(formattedDate, targetSheet, sumario)
        }

        val lessonNum = existingRecord?.lessonNumber ?: (
            (attendanceDao.getMaxLessonNumberForSheet(targetSheet) ?: attendanceDao.getMaxLessonNumber() ?: 0) + 1
        )

        val isUpdate = existingRecord != null && !allowDuplicate

        var synced = false
        val webhookUrl = getGoogleSheetWebhookUrl()
        if (webhookUrl.isNotBlank()) {
            synced = sendToGoogleSheetWebhook(
                webhookUrl = webhookUrl,
                sheetName = targetSheet,
                eventTitle = eventTitle,
                lessonNumber = lessonNum,
                docenteInovLabs = docenteName,
                professorEscola = professorEscola,
                date = formattedDate,
                sumario = sumario,
                minutos = minutos,
                obs = obs,
                isUpdate = isUpdate,
                eventId = eventId,
                allowDuplicate = allowDuplicate
            )
        }

        val record = AttendanceRecord(
            id = if (allowDuplicate) 0L else (existingRecord?.id ?: 0L),
            lessonNumber = lessonNum,
            docenteInovLabs = docenteName,
            professorEscola = professorEscola,
            date = formattedDate,
            sumario = sumario,
            minutos = minutos,
            obs = obs,
            eventId = eventId,
            eventTitle = eventTitle,
            sheetName = targetSheet,
            syncedToGoogleSheets = synced
        )

        if (existingRecord != null && !allowDuplicate) {
            attendanceDao.updateRecord(record)
            record
        } else {
            val id = attendanceDao.insertRecord(record)
            record.copy(id = id)
        }
    }

    suspend fun deleteRecord(record: AttendanceRecord): Boolean = withContext(Dispatchers.IO) {
        val webhookUrl = getGoogleSheetWebhookUrl()
        var sheetDeleted = false
        if (webhookUrl.isNotBlank()) {
            val targetSheet = record.sheetName.ifBlank { record.professorEscola.ifBlank { record.eventTitle } }
            sheetDeleted = deleteFromGoogleSheetWebhook(
                webhookUrl = webhookUrl,
                sheetName = targetSheet,
                eventTitle = record.eventTitle,
                lessonNumber = record.lessonNumber,
                date = record.date,
                sumario = record.sumario,
                eventId = record.eventId
            )
        }
        attendanceDao.deleteRecord(record)
        sheetDeleted
    }

    suspend fun deleteRecordByEventId(eventId: Long) = withContext(Dispatchers.IO) {
        val records = attendanceDao.getRecordsByEventId(eventId)
        for (rec in records) {
            deleteRecord(rec)
        }
    }

    suspend fun clearAllRecords() = withContext(Dispatchers.IO) {
        attendanceDao.clearAll()
    }

    // Google Sheets Webhook HTTP call to delete a row on undo
    private suspend fun deleteFromGoogleSheetWebhook(
        webhookUrl: String,
        sheetName: String,
        eventTitle: String,
        lessonNumber: Int,
        date: String,
        sumario: String,
        eventId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("action", "deleteRow")
                put("sheetName", sheetName)
                put("tabName", sheetName)
                put("eventName", eventTitle)
                put("eventTitle", eventTitle)
                put("lessonNumber", lessonNumber)
                put("date", date)
                put("data", date)
                put("sumario", sumario)
                put("eventId", eventId)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 200..399
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Google Sheets Webhook HTTP call with duplicate prevention headers and uniqueId
    private suspend fun sendToGoogleSheetWebhook(
        webhookUrl: String,
        sheetName: String,
        eventTitle: String,
        lessonNumber: Int,
        docenteInovLabs: String,
        professorEscola: String,
        date: String,
        sumario: String,
        minutos: Int,
        obs: String,
        isUpdate: Boolean = false,
        eventId: Long = 0L,
        allowDuplicate: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val uniqueId = "${sheetName.replace(" ", "_")}_${date.replace("/", "_")}_${lessonNumber}"
            val json = JSONObject().apply {
                put("action", if (isUpdate) "updateRow" else "appendOrUpdateRow")
                put("sheetName", sheetName)
                put("tabName", sheetName)
                put("eventName", eventTitle.ifBlank { professorEscola })
                put("eventTitle", eventTitle)
                put("schoolName", professorEscola.ifBlank { sheetName })
                put("lessonNumber", lessonNumber)
                put("docenteInovLabs", docenteInovLabs)
                put("professorEscola", professorEscola)
                put("data", date)
                put("date", date)
                put("sumario", sumario)
                put("minutos", minutos)
                put("obs", obs)
                put("eventId", eventId)
                put("uniqueId", uniqueId)
                put("preventDuplicates", !allowDuplicate)
                put("checkDuplicate", !allowDuplicate)
                put("allowDuplicate", allowDuplicate)
                put("isUpdate", isUpdate)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 200..399
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun syncPendingRecordToSheets(record: AttendanceRecord): Boolean = withContext(Dispatchers.IO) {
        val webhookUrl = getGoogleSheetWebhookUrl()
        if (webhookUrl.isBlank()) return@withContext false

        val success = sendToGoogleSheetWebhook(
            webhookUrl = webhookUrl,
            sheetName = record.sheetName.ifBlank { record.professorEscola },
            eventTitle = record.eventTitle,
            lessonNumber = record.lessonNumber,
            docenteInovLabs = record.docenteInovLabs,
            professorEscola = record.professorEscola,
            date = record.date,
            sumario = record.sumario,
            minutos = record.minutos,
            obs = record.obs,
            isUpdate = true,
            eventId = record.eventId
        )

        if (success && !record.syncedToGoogleSheets) {
            attendanceDao.updateRecord(record.copy(syncedToGoogleSheets = true))
        }
        success
    }

    // Generate CSV formatted exactly matching user specification:
    // Row 1: Nº Aula,Docente InovLabs,Professor Escola,Data,Sumário,Minutos,Obs,,{Total Minutos},Total Minutos
    // Row 2: (data or empty),,,,,,,,,
    // Row 3: (data or empty),,,,,,,,,
    // Row 4: (data or empty),,,,,,,,{execucao},% Execução
    // Row 5: (data or empty),,,,,,,,{horasProjeto},Total de horas do Projeto
    fun generateCsv(
        records: List<AttendanceRecord>,
        totalMinutesSum: Int,
        projectHours: Int = getProjectHours()
    ): String {
        val sb = StringBuilder()
        val totalMinutesProject = projectHours * 60
        val execucaoPercent = if (totalMinutesProject > 0) {
            val pct = (totalMinutesSum.toDouble() / totalMinutesProject.toDouble()) * 100.0
            if (pct == pct.toInt().toDouble()) "${pct.toInt()}%" else String.format(Locale.US, "%.1f%%", pct)
        } else {
            "0%"
        }

        // Row 1: Header + Total Minutos
        sb.append("Nº Aula,Docente InovLabs,Professor Escola,Data,Sumário,Minutos,Obs,,${totalMinutesSum},Total Minutos\n")

        val maxRows = maxOf(records.size, 4)
        for (i in 0 until maxRows) {
            val record = records.getOrNull(i)
            val baseColumns = if (record != null) {
                val escapedDocente = escapeCsv(record.docenteInovLabs)
                val escapedEscola = escapeCsv(record.professorEscola)
                val escapedSumario = escapeCsv(record.sumario)
                val escapedObs = escapeCsv(record.obs)
                "${record.lessonNumber},$escapedDocente,$escapedEscola,${record.date},$escapedSumario,${record.minutos},$escapedObs"
            } else {
                ",,,,,,"
            }

            // Summary metrics on columns 9 and 10 for rows 4 and 5 (index 2 and 3 in zero-indexed list after header)
            when (i) {
                2 -> {
                    // Row 4 in 1-based CSV
                    sb.append("$baseColumns,,$execucaoPercent,% Execução\n")
                }
                3 -> {
                    // Row 5 in 1-based CSV
                    sb.append("$baseColumns,,$projectHours,Total de horas do Projeto\n")
                }
                else -> {
                    sb.append("$baseColumns,,\n")
                }
            }
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    fun exportCsvFile(
        records: List<AttendanceRecord>,
        totalMinutesSum: Int,
        projectHours: Int = getProjectHours()
    ): File? {
        return try {
            val csvContent = generateCsv(records, totalMinutesSum, projectHours)
            val file = File(context.cacheDir, "presencas_aulas_${System.currentTimeMillis()}.csv")
            FileOutputStream(file).use { out ->
                // Write UTF-8 BOM so Excel and Sheets recognize accents properly
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(csvContent.toByteArray(Charsets.UTF_8))
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Import CSV content matching user format
    suspend fun importCsvContent(csvContent: String): Pair<Int, String?> = withContext(Dispatchers.IO) {
        try {
            val lines = csvContent.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return@withContext Pair(0, "CSV is empty")

            val imported = mutableListOf<AttendanceRecord>()
            var foundHours: Int? = null

            for ((index, rawLine) in lines.withIndex()) {
                val tokens = parseCsvLine(rawLine)
                if (index == 0) {
                    // Header row check
                    continue
                }

                // Check for project hours on column 9 if present
                if (tokens.size >= 10 && tokens[9].contains("Total de horas do Projeto", ignoreCase = true)) {
                    tokens[8].filter { it.isDigit() }.toIntOrNull()?.let { foundHours = it }
                }

                // Check if row has valid lesson record
                val lessonNum = tokens.getOrNull(0)?.trim()?.toIntOrNull()
                val docente = tokens.getOrNull(1)?.trim() ?: ""
                val escola = tokens.getOrNull(2)?.trim() ?: ""
                val data = tokens.getOrNull(3)?.trim() ?: ""
                val sumario = tokens.getOrNull(4)?.trim() ?: ""
                val minutos = tokens.getOrNull(5)?.trim()?.toIntOrNull() ?: 0
                val obs = tokens.getOrNull(6)?.trim() ?: ""

                if (lessonNum != null && (escola.isNotBlank() || sumario.isNotBlank())) {
                    imported.add(
                        AttendanceRecord(
                            lessonNumber = lessonNum,
                            docenteInovLabs = docente.ifBlank { getDocenteName() },
                            professorEscola = escola,
                            date = data.ifBlank { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) },
                            sumario = sumario,
                            minutos = minutos,
                            obs = obs,
                            eventId = -1L,
                            syncedToGoogleSheets = false
                        )
                    )
                }
            }

            if (foundHours != null) {
                setProjectHours(foundHours!!)
            }

            if (imported.isNotEmpty()) {
                insertAll(imported)
                Pair(imported.size, null)
            } else {
                Pair(0, "Nenhuma linha válida de presença encontrada no CSV")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(0, "Erro ao processar ficheiro CSV: ${e.message}")
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }

    // Preferences
    fun getProjectHours(): Int {
        return prefs.getInt("project_total_hours", 150)
    }

    fun setProjectHours(hours: Int) {
        prefs.edit().putInt("project_total_hours", if (hours > 0) hours else 150).apply()
    }

    // Preferences
    fun getSelectedCalendarId(): Long? {
        val id = prefs.getLong("selected_calendar_id", -1L)
        return if (id == -1L) null else id
    }

    fun setSelectedCalendarId(calendarId: Long?) {
        if (calendarId == null) {
            prefs.edit().remove("selected_calendar_id").apply()
        } else {
            prefs.edit().putLong("selected_calendar_id", calendarId).apply()
        }
    }

    fun getSelectedCalendarName(): String {
        return prefs.getString("selected_calendar_name", "Todos os Calendários") ?: "Todos os Calendários"
    }

    fun setSelectedCalendarName(name: String) {
        prefs.edit().putString("selected_calendar_name", name).apply()
    }

    fun getDocenteName(): String {
        return prefs.getString("docente_name", "Docente InovLabs") ?: "Docente InovLabs"
    }

    fun setDocenteName(name: String) {
        prefs.edit().putString("docente_name", name.ifBlank { "Docente InovLabs" }).apply()
    }

    fun getGoogleSheetWebhookUrl(): String {
        val saved = prefs.getString("google_sheet_webhook_url", null)
        return if (saved.isNullOrBlank() || 
            saved.contains("AKfycbyVgHdSGCI3rV7nRABvh8ECY4z_rZpXImg8AgS6TnnhLyfdN7ZbTRvMFumHbL45V_sYBQ") ||
            saved.contains("AKfycbwYiQrBGgc3kdi1RiZUiq0S1upOAR1yIVJhUQo1Qy0VK2kSq2AFuGqv19dCvB9Ydpc8wQ")) {
            DEFAULT_WEBHOOK_URL
        } else {
            saved
        }
    }

    fun setGoogleSheetWebhookUrl(url: String) {
        prefs.edit().putString("google_sheet_webhook_url", url.trim()).apply()
    }

    fun getGoogleSheetViewUrl(): String {
        return prefs.getString("google_sheet_view_url", "") ?: ""
    }

    fun setGoogleSheetViewUrl(url: String) {
        prefs.edit().putString("google_sheet_view_url", url.trim()).apply()
    }

    fun isCalendarDemoMode(): Boolean {
        return prefs.getBoolean("calendar_demo_mode", false)
    }

    fun setCalendarDemoMode(demo: Boolean) {
        prefs.edit().putBoolean("calendar_demo_mode", demo).apply()
    }
}
