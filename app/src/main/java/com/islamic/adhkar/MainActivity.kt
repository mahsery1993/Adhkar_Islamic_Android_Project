package com.islamic.adhkar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.parseColor("#064E3B")
            window.navigationBarColor = Color.parseColor("#FFFFFF")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }

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
            setGeolocationEnabled(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && (url.startsWith("https://www.google.com/maps") || url.startsWith("geo:"))) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        return true
                    } catch (e: Exception) {}
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    callback?.invoke(origin, true, false)
                } else {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        102
                    )
                    callback?.invoke(origin, true, false)
                }
            }
        }

        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")
        webView.loadUrl("file:///android_asset/adhkar_islamic_app.html")

        ensureBackgroundReminderScheduled()
    }

    override fun onStop() {
        super.onStop()
        ensureBackgroundReminderScheduled()
    }

    private fun ensureBackgroundReminderScheduled() {
        val prefs = getSharedPreferences("adhkar_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("reminders_enabled", true)) {
            val savedMin = prefs.getInt("interval_minutes", 30)
            DhikrAlarmReceiver.scheduleNextReminder(this, savedMin)
        }
        DhikrAlarmReceiver.scheduleAllDailyAlarmsFromPrefs(this)
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
        fun saveDailyAlarms(
            morningEnabled: Boolean,
            morningTime: String,
            eveningEnabled: Boolean,
            eveningTime: String
        ) {
            val prefs = context.getSharedPreferences("adhkar_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("morning_alarm_enabled", morningEnabled)
                .putString("morning_alarm_time", morningTime)
                .putBoolean("evening_alarm_enabled", eveningEnabled)
                .putString("evening_alarm_time", eveningTime)
                .apply()

            DhikrAlarmReceiver.scheduleAllDailyAlarmsFromPrefs(context)
        }

        @JavascriptInterface
        fun getDeviceLocation(): String {
            try {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        102
                    )
                    return ""
                }
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                var bestLoc: Location? = null
                for (p in providers) {
                    val loc = lm.getLastKnownLocation(p) ?: continue
                    if (bestLoc == null || loc.accuracy < bestLoc.accuracy) {
                        bestLoc = loc
                    }
                }
                if (bestLoc != null) {
                    return "${bestLoc.latitude},${bestLoc.longitude}"
                }
            } catch (e: Exception) {}
            return ""
        }

        @JavascriptInterface
        fun triggerNativeNotification(categoryTitle: String, text: String, reward: String) {
            val item = DhikrItem("custom", "custom", categoryTitle, text, 1, reward)
            NotificationHelper.showDhikrPopupNotification(context, item)
        }

        @JavascriptInterface
        fun requestBackgroundPermission() {
            requestNotificationPermissionIfNeeded()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                } catch (e: Exception) {}
            }
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
