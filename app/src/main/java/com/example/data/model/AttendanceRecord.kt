package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lessonNumber: Int, // Nº Aula (1, 2, 3...)
    val docenteInovLabs: String, // Docente InovLabs
    val professorEscola: String, // Professor Escola (or School Name from event)
    val date: String, // Data (DD/MM/YYYY)
    val sumario: String, // Sumário (Class title / summary)
    val minutos: Int, // Minutos (e.g. 60, 90)
    val obs: String = "", // Obs (Observations / Notes)
    val eventId: Long = 0,
    val eventTitle: String = "", // Event Title from Calendar
    val sheetName: String = "", // Specific Sheet / Tab Name in Google Spreadsheet
    val timestamp: Long = System.currentTimeMillis(),
    val syncedToGoogleSheets: Boolean = false
)

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isPrimary: Boolean = false,
    val color: Int = 0
)

data class ClassEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val schoolName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val location: String = "",
    val description: String = "",
    val attendanceStatus: AttendanceStatus = AttendanceStatus.PENDING,
    val loggedRecordId: Long? = null
)

enum class AttendanceStatus {
    PENDING,
    ATTENDED,
    NOT_ATTENDED
}
