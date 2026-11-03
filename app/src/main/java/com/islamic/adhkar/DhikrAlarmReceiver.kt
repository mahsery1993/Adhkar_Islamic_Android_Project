package com.islamic.adhkar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class DhikrAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("adhkar_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("reminders_enabled", true)
        if (!enabled) return

        val incMorning = prefs.getBoolean("cat_morning", true)
        val incEvening = prefs.getBoolean("cat_evening", true)
        val incJawami = prefs.getBoolean("cat_jawami", true)
        val incPrayer = prefs.getBoolean("cat_prayer", true)
        val incSleep = prefs.getBoolean("cat_sleep", true)
        val incRuqyah = prefs.getBoolean("cat_ruqyah", true)

        val dhikr = DhikrRepository.getRandomDhikr(
            incMorning, incEvening, incJawami, incPrayer, incSleep, incRuqyah
        )
        NotificationHelper.showDhikrPopupNotification(context, dhikr)

        val intervalMinutes = prefs.getInt("interval_minutes", 30)
        scheduleNextReminder(context, intervalMinutes)
    }

    companion object {
        private const val REQUEST_CODE = 2026

        fun scheduleNextReminder(context: Context, intervalMinutes: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DhikrAlarmReceiver::class.java).apply {
                action = "com.islamic.adhkar.ACTION_SHOW_DHIKR"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + (intervalMinutes * 60 * 1000L)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }

        fun cancelReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DhikrAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
