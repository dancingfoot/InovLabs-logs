package com.example.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.example.data.model.AttendanceStatus
import com.example.data.model.CalendarInfo
import com.example.data.model.ClassEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

class CalendarRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun getDeviceCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val calendars = mutableListOf<CalendarInfo>()
        val uri: Uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_COLOR
        )

        try {
            val cursor: Cursor? = contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndex(CalendarContract.Calendars._ID)
                val nameCol = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountCol = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val primaryCol = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val colorCol = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)

                while (it.moveToNext()) {
                    val id = if (idCol != -1) it.getLong(idCol) else 0L
                    val name = if (nameCol != -1) it.getString(nameCol) ?: "Calendar" else "Calendar"
                    val account = if (accountCol != -1) it.getString(accountCol) ?: "" else ""
                    val isPrimary = if (primaryCol != -1) it.getInt(primaryCol) == 1 else false
                    val color = if (colorCol != -1) it.getInt(colorCol) else 0

                    calendars.add(
                        CalendarInfo(
                            id = id,
                            displayName = name,
                            accountName = account,
                            isPrimary = isPrimary,
                            color = color
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Calendar permission not granted
        } catch (e: Exception) {
            e.printStackTrace()
        }

        calendars
    }

    suspend fun getTodayEvents(calendarId: Long?): List<ClassEvent> {
        return getEventsForDate(calendarId, System.currentTimeMillis())
    }

    suspend fun getEventsForDate(calendarId: Long?, dateMillis: Long): List<ClassEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<ClassEvent>()

        val calendar = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDayMillis = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDayMillis = calendar.timeInMillis

        // Strategy 1: Use Instances content URI for accurate recurring/single instances today
        try {
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startOfDayMillis)
            ContentUris.appendId(builder, endOfDayMillis)

            val projection = arrayOf(
                CalendarContract.Instances._ID,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DESCRIPTION
            )

            var selection: String? = null
            var selectionArgs: Array<String>? = null
            if (calendarId != null && calendarId > 0) {
                selection = "${CalendarContract.Instances.CALENDAR_ID} = ?"
                selectionArgs = arrayOf(calendarId.toString())
            }

            val cursor: Cursor? = contentResolver.query(
                builder.build(),
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.use {
                val instIdCol = it.getColumnIndex(CalendarContract.Instances._ID)
                val eventIdCol = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val calIdCol = it.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
                val titleCol = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val startCol = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endCol = it.getColumnIndex(CalendarContract.Instances.END)
                val locCol = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val descCol = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)

                while (it.moveToNext()) {
                    val instId = if (instIdCol != -1) it.getLong(instIdCol) else 0L
                    val eventId = if (eventIdCol != -1) it.getLong(eventIdCol) else 0L
                    val id = if (instId > 0) instId else eventId
                    val calId = if (calIdCol != -1) it.getLong(calIdCol) else 0L
                    val rawTitle = if (titleCol != -1) it.getString(titleCol) else null
                    val title = if (!rawTitle.isNullOrBlank()) rawTitle else "Aula Sem Título"
                    val startTime = if (startCol != -1) it.getLong(startCol) else startOfDayMillis
                    val endTime = if (endCol != -1) it.getLong(endCol) else (startTime + 3600000L)
                    val location = if (locCol != -1) it.getString(locCol) ?: "" else ""
                    val description = if (descCol != -1) it.getString(descCol) ?: "" else ""

                    if (startTime < endOfDayMillis && endTime > startOfDayMillis) {
                        events.add(
                            ClassEvent(
                                id = id,
                                calendarId = calId,
                                title = title,
                                schoolName = extractSchoolName(title),
                                startTimeMillis = startTime,
                                endTimeMillis = endTime,
                                location = location,
                                description = description,
                                attendanceStatus = AttendanceStatus.PENDING
                            )
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("CalendarRepository", "SecurityException querying Instances table", e)
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Exception querying Instances table", e)
        }

        // Strategy 2: If Instances returned nothing, try querying CalendarContract.Events table directly
        if (events.isEmpty()) {
            try {
                val eventProjection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.CALENDAR_ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DESCRIPTION
                )

                val whereClauses = mutableListOf<String>()
                val args = mutableListOf<String>()

                whereClauses.add("(${CalendarContract.Events.DELETED} = 0 OR ${CalendarContract.Events.DELETED} IS NULL)")

                if (calendarId != null && calendarId > 0) {
                    whereClauses.add("${CalendarContract.Events.CALENDAR_ID} = ?")
                    args.add(calendarId.toString())
                }

                // Fallback: Strictly match single/non-recurring events whose start time is within the selected day
                whereClauses.add("${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?")
                args.add(startOfDayMillis.toString())
                args.add(endOfDayMillis.toString())

                val cursor = contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    eventProjection,
                    whereClauses.joinToString(" AND "),
                    args.toTypedArray(),
                    "${CalendarContract.Events.DTSTART} ASC"
                )

                cursor?.use {
                    val idCol = it.getColumnIndex(CalendarContract.Events._ID)
                    val calIdCol = it.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                    val titleCol = it.getColumnIndex(CalendarContract.Events.TITLE)
                    val startCol = it.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endCol = it.getColumnIndex(CalendarContract.Events.DTEND)
                    val locCol = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                    val descCol = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)

                    while (it.moveToNext()) {
                        val id = if (idCol != -1) it.getLong(idCol) else 0L
                        val calId = if (calIdCol != -1) it.getLong(calIdCol) else 0L
                        val rawTitle = if (titleCol != -1) it.getString(titleCol) else null
                        val title = if (!rawTitle.isNullOrBlank()) rawTitle else "Aula Sem Título"
                        val startTime = if (startCol != -1) it.getLong(startCol) else startOfDayMillis
                        val endTime = if (endCol != -1 && !it.isNull(endCol)) it.getLong(endCol) else (startTime + 3600000L)
                        val location = if (locCol != -1) it.getString(locCol) ?: "" else ""
                        val description = if (descCol != -1) it.getString(descCol) ?: "" else ""

                        events.add(
                            ClassEvent(
                                id = id,
                                calendarId = calId,
                                title = title,
                                schoolName = extractSchoolName(title),
                                startTimeMillis = startTime,
                                endTimeMillis = endTime,
                                location = location,
                                description = description,
                                attendanceStatus = AttendanceStatus.PENDING
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("CalendarRepository", "Exception querying Events table fallback", e)
            }
        }

        events
    }

    suspend fun createCalendarEvent(
        title: String,
        startHour: Int = 10,
        startMinute: Int = 0,
        durationMinutes: Int = 60,
        calendarId: Long,
        dateMillis: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = cal.timeInMillis
        val endMillis = startMillis + (durationMinutes * 60 * 1000L)

        val targetCalId = if (calendarId > 0) calendarId else {
            getDeviceCalendars().firstOrNull()?.id ?: 1L
        }

        try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, "Aula: $title")
                put(CalendarContract.Events.CALENDAR_ID, targetCalId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val newId = uri?.lastPathSegment?.toLongOrNull() ?: -1L
            newId
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error creating calendar event", e)
            -1L
        }
    }

    suspend fun createSampleCalendarEvent(
        title: String,
        startHour: Int,
        startMinute: Int,
        durationMinutes: Int,
        calendarId: Long,
        dateMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return createCalendarEvent(title, startHour, startMinute, durationMinutes, calendarId, dateMillis) > 0
    }

    companion object {
        fun extractSchoolName(title: String): String {
            val clean = title.trim()
            val lower = clean.lowercase(java.util.Locale.ROOT)
            return when {
                clean.contains(" - ") -> clean.substringBefore(" - ").trim()
                clean.contains(" – ") -> clean.substringBefore(" – ").trim()
                clean.contains(" — ") -> clean.substringBefore(" — ").trim()
                clean.contains(" : ") -> clean.substringBefore(" : ").trim()
                clean.contains(":") -> clean.substringBefore(":").trim()
                clean.contains(" | ") -> clean.substringBefore(" | ").trim()
                clean.contains("/") -> clean.substringBefore("/").trim()
                clean.contains("-") && !clean.startsWith("-") -> clean.substringBefore("-").trim()
                lower.contains(" at ") -> clean.substring(lower.indexOf(" at ") + 4).trim()
                lower.contains(" em ") -> clean.substring(lower.indexOf(" em ") + 4).trim()
                lower.contains(" na ") -> clean.substring(lower.indexOf(" na ") + 4).trim()
                lower.contains(" no ") -> clean.substring(lower.indexOf(" no ") + 4).trim()
                else -> clean
            }
        }

        fun extractClassSummary(title: String): String {
            val clean = title.trim()
            return when {
                clean.contains(" - ") -> clean.substringAfter(" - ").trim()
                clean.contains(" – ") -> clean.substringAfter(" – ").trim()
                clean.contains(" — ") -> clean.substringAfter(" — ").trim()
                clean.contains(" : ") -> clean.substringAfter(" : ").trim()
                clean.contains(":") -> clean.substringAfter(":").trim()
                clean.contains(" | ") -> clean.substringAfter(" | ").trim()
                clean.contains("/") -> clean.substringAfter("/").trim()
                clean.contains("-") && !clean.startsWith("-") -> clean.substringAfter("-").trim()
                else -> clean
            }
        }

        fun getSampleTodayEvents(dateMillis: Long = System.currentTimeMillis()): List<ClassEvent> {
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            cal.set(Calendar.HOUR_OF_DAY, 10)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            val start1 = cal.timeInMillis
            val end1 = start1 + 90 * 60 * 1000L // 90 min

            cal.set(Calendar.HOUR_OF_DAY, 14)
            cal.set(Calendar.MINUTE, 30)
            cal.set(Calendar.SECOND, 0)
            val start2 = cal.timeInMillis
            val end2 = start2 + 60 * 60 * 1000L // 60 min

            return listOf(
                ClassEvent(
                    id = 1001L,
                    calendarId = 1L,
                    title = "CEPAO - Robótica Educativa",
                    schoolName = "CEPAO",
                    startTimeMillis = start1,
                    endTimeMillis = end1,
                    location = "Sala de Robótica",
                    description = "Aula de Robótica e Programação InovLabs"
                ),
                ClassEvent(
                    id = 1002L,
                    calendarId = 1L,
                    title = "LiceuOeiras - Programação Criativa",
                    schoolName = "LiceuOeiras",
                    startTimeMillis = start2,
                    endTimeMillis = end2,
                    location = "Laboratório de Informática",
                    description = "Aula de Programação Criativa e Modelação 3D"
                )
            )
        }
    }
}
