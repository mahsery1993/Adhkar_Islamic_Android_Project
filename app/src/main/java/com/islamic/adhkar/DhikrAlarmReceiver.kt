package com.islamic.adhkar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import java.util.Calendar

class DhikrAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("adhkar_prefs", Context.MODE_PRIVATE)
        val action = intent?.action ?: ""

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            if (prefs.getBoolean("reminders_enabled", true)) {
                scheduleNextReminder(context, prefs.getInt("interval_minutes", 30))
            }
            scheduleAllDailyAlarmsFromPrefs(context)
            return
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ZadAlDhakireen::DhikrReminderWakeLock"
        )
        wakeLock.acquire(5000L)

        try {
            if (action == ACTION_MORNING_ADHKAR) {
                if (prefs.getBoolean("morning_alarm_enabled", true)) {
                    val item = DhikrItem(
                        "m_daily",
                        "morning",
                        "☀️ تذكير أذكار الصباح",
                        "حان الآن موعد قراءة أذكار الصباح • «اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ»",
                        1,
                        "افتح التطبيق لقراءة أذكار الصباح اليومية"
                    )
                    NotificationHelper.showDhikrPopupNotification(context, item)
                    val mTime = prefs.getString("morning_alarm_time", "06:00") ?: "06:00"
                    scheduleDailyTimeAlarm(context, mTime, ACTION_MORNING_ADHKAR, REQUEST_CODE_MORNING)
                }
                return
            }

            if (action == ACTION_EVENING_ADHKAR) {
                if (prefs.getBoolean("evening_alarm_enabled", true)) {
                    val item = DhikrItem(
                        "e_daily",
                        "evening",
                        "🌙 تذكير أذكار المساء",
                        "حان الآن موعد قراءة أذكار المساء • «اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ»",
                        1,
                        "افتح التطبيق لقراءة أذكار المساء اليومية"
                    )
                    NotificationHelper.showDhikrPopupNotification(context, item)
                    val eTime = prefs.getString("evening_alarm_time", "17:30") ?: "17:30"
                    scheduleDailyTimeAlarm(context, eTime, ACTION_EVENING_ADHKAR, REQUEST_CODE_EVENING)
                }
                return
            }

            val enabled = prefs.getBoolean("reminders_enabled", true)
            if (!enabled) return
            val intervalMinutes = prefs.getInt("interval_minutes", 30)

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
            scheduleNextReminder(context, intervalMinutes)
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 2026
        private const val REQUEST_CODE_MORNING = 2027
        private const val REQUEST_CODE_EVENING = 2028

        const val ACTION_SHOW_DHIKR = "com.islamic.adhkar.ACTION_SHOW_DHIKR"
        const val ACTION_MORNING_ADHKAR = "com.islamic.adhkar.ACTION_MORNING_ADHKAR"
        const val ACTION_EVENING_ADHKAR = "com.islamic.adhkar.ACTION_EVENING_ADHKAR"

        fun scheduleAllDailyAlarmsFromPrefs(context: Context) {
            val prefs = context.getSharedPreferences("adhkar_prefs", Context.MODE_PRIVATE)
            val mEnabled = prefs.getBoolean("morning_alarm_enabled", true)
            val mTime = prefs.getString("morning_alarm_time", "06:00") ?: "06:00"
            val eEnabled = prefs.getBoolean("evening_alarm_enabled", true)
            val eTime = prefs.getString("evening_alarm_time", "17:30") ?: "17:30"

            if (mEnabled) {
                scheduleDailyTimeAlarm(context, mTime, ACTION_MORNING_ADHKAR, REQUEST_CODE_MORNING)
            } else {
                cancelSpecificAlarm(context, ACTION_MORNING_ADHKAR, REQUEST_CODE_MORNING)
            }

            if (eEnabled) {
                scheduleDailyTimeAlarm(context, eTime, ACTION_EVENING_ADHKAR, REQUEST_CODE_EVENING)
            } else {
                cancelSpecificAlarm(context, ACTION_EVENING_ADHKAR, REQUEST_CODE_EVENING)
            }
        }

        fun scheduleDailyTimeAlarm(context: Context, timeHHMM: String, actionName: String, reqCode: Int) {
            val parts = timeHHMM.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 6
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now) || timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DhikrAlarmReceiver::class.java).apply {
                action = actionName
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleExactHelper(alarmManager, target.timeInMillis, pendingIntent)
        }

        fun cancelSpecificAlarm(context: Context, actionName: String, reqCode: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DhikrAlarmReceiver::class.java).apply {
                action = actionName
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        fun scheduleNextReminder(context: Context, intervalMinutes: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DhikrAlarmReceiver::class.java).apply {
                action = ACTION_SHOW_DHIKR
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val safeMinutes = if (intervalMinutes < 1) 1 else intervalMinutes
            val triggerAtMillis = System.currentTimeMillis() + (safeMinutes * 60 * 1000L)
            scheduleExactHelper(alarmManager, triggerAtMillis, pendingIntent)
        }

        private fun scheduleExactHelper(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
            } catch (e: Exception) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }

        fun cancelReminder(context: Context) {
            cancelSpecificAlarm(context, ACTION_SHOW_DHIKR, REQUEST_CODE)
        }
    }
}
