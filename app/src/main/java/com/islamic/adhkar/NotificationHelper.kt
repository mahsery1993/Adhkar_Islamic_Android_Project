package com.islamic.adhkar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val CHANNEL_ID = "zad_aldhakireen_reminders_v2"
    private const val CHANNEL_NAME = "تذكيرات الأذكار اليومية"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات أذكار الصباح والمساء والتذكير الدوري بالله"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showDhikrPopupNotification(context: Context, dhikr: DhikrItem) {
        createNotificationChannel(context)

        // استنتاج القسم المستهدف بدقة حتى يفتح التطبيق مباشرة على قائمة أذكار المساء أو الصباح عند الضغط على الإشعار
        val resolvedCategory = when {
            dhikr.category == "evening" || dhikr.categoryTitle.contains("المساء") -> "evening"
            dhikr.category == "morning" || dhikr.categoryTitle.contains("الصباح") -> "morning"
            dhikr.category == "prayer" || dhikr.categoryTitle.contains("الصلاة") -> "prayer"
            dhikr.category == "sleep" || dhikr.categoryTitle.contains("النوم") -> "sleep"
            dhikr.category == "ruqyah" || dhikr.categoryTitle.contains("الرقية") -> "ruqyah"
            dhikr.category == "jawami" || dhikr.categoryTitle.contains("جوامع") -> "jawami"
            else -> dhikr.category
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.islamic.adhkar.OPEN_CATEGORY_$resolvedCategory"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_category", resolvedCategory)
            putExtra("target_category_title", dhikr.categoryTitle)
        }

        val reqCode = resolvedCategory.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            reqCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("زاد الذاكرين • ${dhikr.categoryTitle}")
            .setContentText(dhikr.text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${dhikr.text}\n\n💡 ${dhikr.reward}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (System.currentTimeMillis() % 10000).toInt()
        manager.notify(notificationId, builder.build())
    }
}
