package com.islamic.adhkar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()

        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")

        // تحميل التصميم الاحترافي الكامل (الأقسام الـ 6 + المسبحة + المنبه) من مجلد assets
        webView.loadUrl("file:///android_asset/adhkar_islamic_app.html")

        // تفعيل الجدولة الافتراضية في الخلفية عند أول تشغيل
        val prefs = getSharedPreferences("adhkar_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("reminders_enabled", true)) {
            val savedMin = prefs.getInt("interval_minutes", 30)
            DhikrAlarmReceiver.scheduleNextReminder(this, savedMin)
        }
    }

    inner class AndroidBridge(private val context: Context) {

        @JavascriptInterface
        fun saveReminderSettings(
            enabled: Boolean,
            intervalMinutes: Int,
            catMorning: Boolean,
            catEvening: Boolean,
            catJawami: Boolean,
            catPrayer: Boolean,
            catSleep: Boolean,
            catRuqyah: Boolean
        ) {
            val prefs = context.getSharedPreferences("adhkar_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("reminders_enabled", enabled)
                .putInt("interval_minutes", intervalMinutes)
                .putBoolean("cat_morning", catMorning)
                .putBoolean("cat_evening", catEvening)
                .putBoolean("cat_jawami", catJawami)
                .putBoolean("cat_prayer", catPrayer)
                .putBoolean("cat_sleep", catSleep)
                .putBoolean("cat_ruqyah", catRuqyah)
                .apply()

            if (enabled) {
                DhikrAlarmReceiver.scheduleNextReminder(context, intervalMinutes)
            } else {
                DhikrAlarmReceiver.cancelReminder(context)
            }
        }

        @JavascriptInterface
        fun triggerNativeNotification(categoryTitle: String, text: String, reward: String) {
            val item = DhikrItem("custom", "custom", categoryTitle, text, 1, reward)
            NotificationHelper.showDhikrPopupNotification(context, item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
