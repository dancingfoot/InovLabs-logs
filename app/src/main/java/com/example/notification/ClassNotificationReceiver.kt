package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.model.ClassEvent
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.CalendarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClassNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CLASS_END = "com.example.action.CLASS_END"
        const val ACTION_DAILY_DIGEST = "com.example.action.DAILY_DIGEST"
        const val ACTION_TEST = "com.example.action.TEST"

        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_SCHOOL_NAME = "extra_school_name"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val repo = AttendanceRepository(context, db.attendanceDao())

                when (intent.action) {
                    ACTION_CLASS_END -> {
                        if (!repo.isClassEndNotificationEnabled()) return@launch

                        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
                        if (eventId <= 0) return@launch

                        val existing = db.attendanceDao().getRecordByEventId(eventId)
                        if (existing != null) {
                            // Already marked/logged, do not notify
                            return@launch
                        }

                        val title = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Aula InovLabs"
                        val school = intent.getStringExtra(EXTRA_SCHOOL_NAME) ?: title

                        NotificationHelper.showClassEndNotification(
                            context = context,
                            eventId = eventId,
                            eventTitle = title,
                            schoolName = school
                        )
                    }

                    ACTION_DAILY_DIGEST -> {
                        if (repo.isDailyDigestEnabled()) {
                            val calendarRepo = CalendarRepository(context)
                            val isDemo = repo.isCalendarDemoMode()
                            val selectedCalId = repo.getSelectedCalendarId()
                            val now = System.currentTimeMillis()

                            val todayEvents: List<ClassEvent> = if (isDemo) {
                                CalendarRepository.getSampleTodayEvents(now)
                            } else {
                                calendarRepo.getEventsForDate(selectedCalId, now)
                            }

                            val pendingTitles = mutableListOf<String>()
                            for (event in todayEvents) {
                                val record = db.attendanceDao().getRecordByEventId(event.id)
                                if (record == null) {
                                    pendingTitles.add(event.schoolName.ifBlank { event.title })
                                }
                            }

                            if (pendingTitles.isNotEmpty()) {
                                NotificationHelper.showDailyDigestNotification(
                                    context = context,
                                    pendingCount = pendingTitles.size,
                                    pendingClassTitles = pendingTitles
                                )
                            }

                            // Re-schedule for the next day
                            NotificationScheduler.scheduleDailyDigest(
                                context = context,
                                hour = repo.getDailyDigestHour(),
                                minute = repo.getDailyDigestMinute()
                            )
                        }
                    }

                    ACTION_TEST -> {
                        NotificationHelper.showTestNotification(context)
                    }
                }
            } catch (_: Exception) {
                // Ignore background errors
            } finally {
                pendingResult.finish()
            }
        }
    }
}
