package com.islamic.adhkar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.GeolocationPermissions
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
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null
    private var latestDeviceLocation: Location? = null
    private var pendingCategoryFromNotification: String? = null

    private val nativeLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            latestDeviceLocation = location
            pushLocationToWebView(location.latitude, location.longitude)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

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
        extractTargetCategoryFromIntent(intent)
        requestNecessaryPermissionsOnStart()

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
                if (url != null && (url.startsWith("https://www.google.com/maps") || url.startsWith("https://maps.google.com") || url.startsWith("geo:") || url.startsWith("intent:"))) {
                    openGoogleMapsForNearestMosques()
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                startNativeHardwareLocationUpdates()
                pendingCategoryFromNotification?.let { cat ->
                    webView.evaluateJavascript(
                        "if(window.openCategoryFromNotification){window.openCategoryFromNotification('$cat');}",
                        null
                    )
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (hasLocationPermission()) {
                    callback?.invoke(origin, true, false)
                    startNativeHardwareLocationUpdates()
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        102
                    )
                }
            }
        }

        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")

        val startUrl = if (!pendingCategoryFromNotification.isNullOrEmpty()) {
            "file:///android_asset/adhkar_islamic_app.html?cat=${pendingCategoryFromNotification}"
        } else {
            "file:///android_asset/adhkar_islamic_app.html"
        }
        webView.loadUrl(startUrl)

        ensureBackgroundReminderScheduled()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractTargetCategoryFromIntent(intent)
        pendingCategoryFromNotification?.let { cat ->
            if (this::webView.isInitialized) {
                webView.post {
                    webView.evaluateJavascript(
                        "if(window.openCategoryFromNotification){window.openCategoryFromNotification('$cat');}",
                        null
                    )
                }
            }
        }
    }

    private fun extractTargetCategoryFromIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val rawCat = incomingIntent.getStringExtra("target_category") ?: ""
        val rawTitle = incomingIntent.getStringExtra("target_category_title") ?: ""
        val resolved = when {
            rawCat == "evening" || rawTitle.contains("المساء") -> "evening"
            rawCat == "morning" || rawTitle.contains("الصباح") -> "morning"
            rawCat == "prayer" || rawTitle.contains("الصلاة") -> "prayer"
            rawCat == "sleep" || rawTitle.contains("النوم") -> "sleep"
            rawCat == "ruqyah" || rawTitle.contains("الرقية") -> "ruqyah"
            rawCat == "jawami" || rawTitle.contains("جوامع") -> "jawami"
            rawCat.isNotEmpty() -> rawCat
            else -> null
        }
        if (!resolved.isNullOrEmpty()) {
            pendingCategoryFromNotification = resolved
        }
    }

    private fun openGoogleMapsForNearestMosques() {
        try {
            // يفتح تطبيق خرائط Google مباشرة على موقع الهاتف الفعلي ويعرض أقرب المساجد
            val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode("مسجد")}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
            }
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
                return
            }
        } catch (e: Exception) {}

        try {
            val fallbackUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode("مسجد قريب مني")}")
            startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
        } catch (e: Exception) {}
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun startNativeHardwareLocationUpdates() {
        if (!hasLocationPermission()) return
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var bestLoc: Location? = latestDeviceLocation
            for (p in providers) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestSingleUpdate(p, nativeLocationListener, Looper.getMainLooper())
                    }
                    val loc = lm.getLastKnownLocation(p) ?: continue
                    if (bestLoc == null || loc.time > bestLoc.time - 120000 || loc.accuracy < bestLoc.accuracy) {
                        bestLoc = loc
                    }
                } catch (e: Exception) {}
            }
            if (bestLoc != null) {
                latestDeviceLocation = bestLoc
                pushLocationToWebView(bestLoc.latitude, bestLoc.longitude)
            }
        } catch (e: Exception) {}
    }

    private fun pushLocationToWebView(lat: Double, lng: Double) {
        if (!this::webView.isInitialized) return
        webView.post {
            webView.evaluateJavascript(
                "if(window.onNativeGpsLocation){window.onNativeGpsLocation($lat,$lng);}",
                null
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102 || requestCode == 101) {
            val granted = hasLocationPermission()
            pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
            pendingGeoCallback = null
            if (granted) {
                startNativeHardwareLocationUpdates()
            }
        }
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
        fun openNearestMosquesInGoogleMaps() {
            runOnUiThread {
                openGoogleMapsForNearestMosques()
            }
        }

        @JavascriptInterface
        fun consumePendingCategory(): String {
            val cat = pendingCategoryFromNotification ?: ""
            pendingCategoryFromNotification = null
            return cat
        }

        @JavascriptInterface
        fun requestFreshLocationUpdate() {
            runOnUiThread {
                if (hasLocationPermission()) {
                    startNativeHardwareLocationUpdates()
                } else {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        102
                    )
                }
            }
        }

        @JavascriptInterface
        @SuppressLint("MissingPermission")
        fun getDeviceLocation(): String {
            try {
                if (!hasLocationPermission()) {
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
                latestDeviceLocation?.let {
                    return "${it.latitude},${it.longitude}"
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
                    latestDeviceLocation = bestLoc
                    return "${bestLoc.latitude},${bestLoc.longitude}"
                }
            } catch (e: Exception) {}
            return ""
        }

        @JavascriptInterface
        fun triggerNativeNotification(categoryTitle: String, text: String, reward: String) {
            val resolvedCat = when {
                categoryTitle.contains("المساء") -> "evening"
                categoryTitle.contains("الصباح") -> "morning"
                categoryTitle.contains("الصلاة") -> "prayer"
                categoryTitle.contains("النوم") -> "sleep"
                categoryTitle.contains("الرقية") -> "ruqyah"
                categoryTitle.contains("جوامع") -> "jawami"
                else -> "custom"
            }
            val item = DhikrItem("custom", resolvedCat, categoryTitle, text, 1, reward)
            NotificationHelper.showDhikrPopupNotification(context, item)
        }

        @JavascriptInterface
        fun triggerNativeNotificationWithCategory(category: String, categoryTitle: String, text: String, reward: String) {
            val item = DhikrItem("custom", category, categoryTitle, text, 1, reward)
            NotificationHelper.showDhikrPopupNotification(context, item)
        }

        @JavascriptInterface
        fun requestBackgroundPermission() {
            requestNecessaryPermissionsOnStart()
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

    private fun requestNecessaryPermissionsOnStart() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (!hasLocationPermission()) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
        }
    }
}
