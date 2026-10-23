package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.AttendanceStatus
import com.example.data.model.ClassEvent
import java.util.Calendar

object NotificationScheduler {

    private const val DAILY_DIGEST_REQUEST_CODE = 88888

    fun scheduleClassEndReminder(context: Context, event: ClassEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        // Only schedule if class finishes in the future
        if (event.endTimeMillis <= now) return
        if (event.attendanceStatus == AttendanceStatus.ATTENDED) return

        val intent = Intent(context, ClassNotificationReceiver::class.java).apply {
            action = ClassNotificationReceiver.ACTION_CLASS_END
            putExtra(ClassNotificationReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(ClassNotificationReceiver.EXTRA_EVENT_TITLE, event.title)
            putExtra(ClassNotificationReceiver.EXTRA_SCHOOL_NAME, event.schoolName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.endTimeMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.endTimeMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.endTimeMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, event.endTimeMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            // In case of permission restriction on exact alarms, fallback to standard set
            alarmManager.set(AlarmManager.RTC_WAKEUP, event.endTimeMillis, pendingIntent)
        }
    }

    fun cancelClassReminder(context: Context, eventId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ClassNotificationReceiver::class.java).apply {
            action = ClassNotificationReceiver.ACTION_CLASS_END
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleDailyDigest(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If target time today has already passed, schedule for tomorrow
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, ClassNotificationReceiver::class.java).apply {
            action = ClassNotificationReceiver.ACTION_DAILY_DIGEST
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_DIGEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
        }
    }

    fun cancelDailyDigest(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ClassNotificationReceiver::class.java).apply {
            action = ClassNotificationReceiver.ACTION_DAILY_DIGEST
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_DIGEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun syncAllRemindersForToday(
        context: Context,
        events: List<ClassEvent>,
        classEndEnabled: Boolean,
        dailyDigestEnabled: Boolean,
        digestHour: Int,
        digestMinute: Int
    ) {
        // 1. Sync Class End Reminders
        if (classEndEnabled) {
            events.forEach { event ->
                if (event.attendanceStatus == AttendanceStatus.ATTENDED) {
                    cancelClassReminder(context, event.id)
                } else {
                    scheduleClassEndReminder(context, event)
                }
            }
        } else {
            events.forEach { event ->
                cancelClassReminder(context, event.id)
            }
        }

        // 2. Sync Daily Digest
        if (dailyDigestEnabled) {
            scheduleDailyDigest(context, digestHour, digestMinute)
        } else {
            cancelDailyDigest(context)
        }
    }
}
