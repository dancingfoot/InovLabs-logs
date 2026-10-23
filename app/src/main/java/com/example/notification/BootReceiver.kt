package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.model.AttendanceStatus
import com.example.data.model.ClassEvent
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.CalendarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val repo = AttendanceRepository(context, db.attendanceDao())
                    val calendarRepo = CalendarRepository(context)

                    val isDemo = repo.isCalendarDemoMode()
                    val selectedCalId = repo.getSelectedCalendarId()
                    val now = System.currentTimeMillis()

                    val events: List<ClassEvent> = if (isDemo) {
                        CalendarRepository.getSampleTodayEvents(now)
                    } else {
                        calendarRepo.getEventsForDate(selectedCalId, now)
                    }

                    // Annotate status from Room DB
                    val annotatedEvents = mutableListOf<ClassEvent>()
                    for (event in events) {
                        val record = db.attendanceDao().getRecordByEventId(event.id)
                        if (record != null) {
                            annotatedEvents.add(
                                event.copy(
                                    attendanceStatus = AttendanceStatus.ATTENDED,
                                    loggedRecordId = record.id
                                )
                            )
                        } else {
                            annotatedEvents.add(event)
                        }
                    }

                    NotificationScheduler.syncAllRemindersForToday(
                        context = context,
                        events = annotatedEvents,
                        classEndEnabled = repo.isClassEndNotificationEnabled(),
                        dailyDigestEnabled = repo.isDailyDigestEnabled(),
                        digestHour = repo.getDailyDigestHour(),
                        digestMinute = repo.getDailyDigestMinute()
                    )
                } catch (_: Exception) {
                    // Ignore errors during boot recovery
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
