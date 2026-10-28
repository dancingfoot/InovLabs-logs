package com.example.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_ID = "inovlabs_class_reminders"
    const val CHANNEL_NAME = "Lembretes de Aulas (InovLabs)"
    const val CHANNEL_DESC = "Avisos automáticos no fim das aulas e lembretes diários"

    const val EXTRA_OPEN_EVENT_ID = "extra_open_event_id"
    const val TEST_NOTIFICATION_ID = 99999
    const val DAILY_DIGEST_NOTIFICATION_ID = 99998

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showClassEndNotification(
        context: Context,
        eventId: Long,
        eventTitle: String,
        schoolName: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_EVENT_ID, eventId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displaySchool = schoolName.ifBlank { eventTitle }
        val title = "Aula Concluída: $displaySchool"
        val message = "Terminou a aula. Toca aqui para preencher o sumário e registar presença."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(eventId.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked or not granted
        }
    }

    fun showDailyDigestNotification(
        context: Context,
        pendingCount: Int,
        pendingClassTitles: List<String>
    ) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            DAILY_DIGEST_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (pendingCount == 1) {
            "Tens 1 aula pendente hoje"
        } else {
            "Tens $pendingCount aulas por registar hoje"
        }

        val message = if (pendingClassTitles.isNotEmpty()) {
            "Aulas pendentes: ${pendingClassTitles.take(3).joinToString(", ")}${if (pendingClassTitles.size > 3) "..." else ""}. Toca para abrir o InovLabs."
        } else {
            "Não te esqueças de validar os sumários e presenças das tuas aulas de hoje."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(DAILY_DIGEST_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked or not granted
        }
    }

    fun showTestNotification(context: Context) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            TEST_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle("🔔 Notificação de Teste - InovLabs")
            .setContentText("As permissões e alertas estão a funcionar perfeitamente no teu telemóvel!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("As permissões e alertas estão a funcionar perfeitamente! Vais receber avisos ao terminar cada aula e no teu horário configurado."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked or not granted
        }
    }

    fun showDuplicateEntryNotification(
        context: Context,
        eventId: Long,
        schoolOrName: String,
        date: String,
        message: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_EVENT_ID, eventId)
        }

        val notifId = (eventId.hashCode() and 0x7FFFFFFF) + 1000
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⚠️ Aula Já Registada: $schoolOrName"
        val bodyText = if (message.isNotBlank()) message else "Já existe uma aula registada para '$schoolOrName' na data $date no Google Sheets."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$bodyText\nToque para ver opções de atualização ou duplicação."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
            // Permission revoked or not granted
        }
    }

    fun cancelNotification(context: Context, id: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(id)
        } catch (_: Exception) {}
    }
}
