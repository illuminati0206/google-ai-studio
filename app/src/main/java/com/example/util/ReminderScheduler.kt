package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.receiver.OutfitReminderReceiver
import java.util.Calendar

object ReminderScheduler {

    fun computeReminderEpochMillis(
        dateIso: String,
        timeLabel: String,
        leadMinutes: Int
    ): Long {
        val dateParts = dateIso.split("-")
        val timeParts = timeLabel.split(":")
        val cal = Calendar.getInstance()
        if (dateParts.size == 3) {
            cal.set(Calendar.YEAR, dateParts[0].toIntOrNull() ?: cal.get(Calendar.YEAR))
            cal.set(Calendar.MONTH, ((dateParts[1].toIntOrNull() ?: 1) - 1).coerceIn(0, 11))
            cal.set(Calendar.DAY_OF_MONTH, (dateParts[2].toIntOrNull() ?: 1).coerceIn(1, 31))
        }
        cal.set(Calendar.HOUR_OF_DAY, timeParts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 10)
        cal.set(Calendar.MINUTE, timeParts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return (cal.timeInMillis - leadMinutes * 60_000L).coerceAtLeast(System.currentTimeMillis() + 60_000L)
    }

    fun scheduleStylingReminder(
        context: Context,
        eventId: String,
        eventTitle: String,
        outfitTitle: String,
        triggerAtMillis: Long
    ) {
        OutfitReminderReceiver.ensureNotificationChannel(context)
        val requestCode = eventId.hashCode()
        val intent = Intent(context, OutfitReminderReceiver::class.java).apply {
            putExtra(OutfitReminderReceiver.EXTRA_TITLE, "Styling Session: $eventTitle")
            putExtra(
                OutfitReminderReceiver.EXTRA_BODY,
                if (outfitTitle.isNotBlank()) {
                    "Time to prep \"$outfitTitle\" for $eventTitle. Open Atelier to review fit & accessories."
                } else {
                    "Your scheduled styling session for $eventTitle is coming up."
                }
            )
            putExtra(OutfitReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val safeTrigger = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 10_000L)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTrigger, pendingIntent)
    }

    fun cancelStylingReminder(context: Context, eventId: String) {
        val requestCode = eventId.hashCode()
        val intent = Intent(context, OutfitReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    fun triggerInstantTestNotification(
        context: Context,
        title: String,
        body: String
    ) {
        OutfitReminderReceiver.showAtelierNotification(
            context = context,
            title = title,
            body = body,
            notificationId = (System.currentTimeMillis() % 100000).toInt()
        )
    }
}
