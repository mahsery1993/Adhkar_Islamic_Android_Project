package com.islamic.adhkar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val CHANNEL_ID = "zad_aldhakireen_popup_v3"
    private const val CHANNEL_NAME = "الإشعارات المنبثقة للأذكار"
    private const val AUTO_DISMISS_MS = 12_000L // يختفي الإشعار تلقائياً بعد 12 ثانية

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات منبثقة أعلى الشاشة لأذكار الصباح والمساء والتذكير الدوري وتختفي تلقائياً"
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 180L, 80L, 180L)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(false)
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

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("زاد الذاكرين • ${dhikr.categoryTitle}")
            .setContentText(dhikr.text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${dhikr.text}\n\n💡 ${dhikr.reward}")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0L, 180L, 80L, 180L))
            .setTimeoutAfter(AUTO_DISMISS_MS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (System.currentTimeMillis() % 10000).toInt()
        manager.notify(notificationId, builder.build())

        // احتياطي للأجهزة الأقدم (Android 7.1 وأقل) لإخفاء الإشعار تلقائياً بعد انتهاء المدة
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    manager.cancel(notificationId)
                } catch (_: Exception) {}
            }, AUTO_DISMISS_MS)
        }
    }
}
