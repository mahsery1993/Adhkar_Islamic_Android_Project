package com.islamic.adhkar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.regex.Pattern
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null
    private var latestDeviceLocation: Location? = null

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
                    try {
                        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(mapIntent)
                        return true
                    } catch (e: Exception) {}
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                startNativeHardwareLocationUpdates()
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
        webView.loadUrl("file:///android_asset/adhkar_islamic_app.html")

        ensureBackgroundReminderScheduled()
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

    // Searches Google Maps (tbm=map) + Android Google Geocoder for the TRUE closest mosques
    private fun searchGoogleMapsAndGeocoderMosques(userLat: Double, userLng: Double) {
        thread {
            val results = mutableListOf<Triple<String, Double, Double>>()

            // 1. Android Built-in Google Geocoder (queries Google Play Services Places/Geocoding backend directly)
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(this@MainActivity, Locale("ar"))
                    val radii = listOf(0.008, 0.018, 0.03) // ~800m, ~1.8km, ~3km boxes
                    val terms = listOf("مسجد", "جامع", "مصلى")
                    for (r in radii) {
                        for (term in terms) {
                            try {
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocationName(
                                    term,
                                    15,
                                    userLat - r,
                                    userLng - r,
                                    userLat + r,
                                    userLng + r
                                )
                                if (addresses != null) {
                                    for (addr in addresses) {
                                        if (!addr.hasLatitude() || !addr.hasLongitude()) continue
                                        val mLat = addr.latitude
                                        val mLng = addr.longitude
                                        val distArr = FloatArray(1)
                                        Location.distanceBetween(userLat, userLng, mLat, mLng, distArr)
                                        if (distArr[0] < 5f || distArr[0] > 8000f) continue

                                        var name = addr.featureName ?: ""
                                        val line0 = addr.getAddressLine(0) ?: ""
                                        if (!name.contains("مسجد") && !name.contains("جامع") && !name.contains("مصلى")) {
                                            val firstSeg = line0.split("،", ",")[0].trim()
                                            if (firstSeg.isNotEmpty()) {
                                                name = firstSeg
                                            }
                                        }
                                        if (name.isNotEmpty() && !name.contains("مسجد") && !name.contains("جامع") && !name.contains("مصلى")) {
                                            name = "مسجد $name"
                                        }
                                        if (name.isNotEmpty()) {
                                            results.add(Triple(name, mLat, mLng))
                                        }
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                        if (results.size >= 6) break
                    }
                }
            } catch (e: Exception) {}

            // 2. Direct Google Maps tbm=map JSON Search for "مسجد" and "جامع" around (userLat, userLng)
            val searchQueries = listOf("مسجد", "جامع")
            for (q in searchQueries) {
                try {
                    val encodedQ = URLEncoder.encode(q, "UTF-8")
                    val urlStr = "https://www.google.com/search?tbm=map&authuser=0&hl=ar&gl=jo&q=$encodedQ&pb=!4m8!1m3!1d2200!2d$userLng!3d$userLat!3m2!1i1024!2i768!4f13.1"
                    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 6000
                        readTimeout = 6000
                        setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        )
                        setRequestProperty("Accept-Language", "ar-JO,ar;q=0.9,en;q=0.8")
                    }
                    if (conn.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                        val body = reader.readText()
                        reader.close()

                        // Pattern 1: "Name" ... [null,null,lat,lng]
                        val p1 = Pattern.compile("\"([^\"]*(?:مسجد|جامع|مصلى|Mosque|Masjid)[^\"]*)\"[\\s\\S]{1,350}?\\[null,null,(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)\\]")
                        val m1 = p1.matcher(body)
                        while (m1.find()) {
                            val rawName = m1.group(1)?.trim() ?: continue
                            val mLat = m1.group(2)?.toDoubleOrNull() ?: continue
                            val mLng = m1.group(3)?.toDoubleOrNull() ?: continue
                            val distArr = FloatArray(1)
                            Location.distanceBetween(userLat, userLng, mLat, mLng, distArr)
                            if (distArr[0] in 5f..8000f && rawName.length in 3..80) {
                                results.add(Triple(rawName, mLat, mLng))
                            }
                        }

                        // Pattern 2: [null,null,lat,lng] ... "Name"
                        val p2 = Pattern.compile("\\[null,null,(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)\\][\\s\\S]{1,350}?\"([^\"]*(?:مسجد|جامع|مصلى|Mosque|Masjid)[^\"]*)\"")
                        val m2 = p2.matcher(body)
                        while (m2.find()) {
                            val mLat = m2.group(1)?.toDoubleOrNull() ?: continue
                            val mLng = m2.group(2)?.toDoubleOrNull() ?: continue
                            val rawName = m2.group(3)?.trim() ?: continue
                            val distArr = FloatArray(1)
                            Location.distanceBetween(userLat, userLng, mLat, mLng, distArr)
                            if (distArr[0] in 5f..8000f && rawName.length in 3..80) {
                                results.add(Triple(rawName, mLat, mLng))
                            }
                        }
                    }
                } catch (e: Exception) {}
            }

            if (results.isNotEmpty() && this@MainActivity::webView.isInitialized) {
                val jsonArray = JSONArray()
                for (item in results) {
                    val obj = JSONObject()
                    obj.put("name", item.first)
                    obj.put("lat", item.second)
                    obj.put("lng", item.third)
                    jsonArray.put(obj)
                }
                val jsonStr = jsonArray.toString()
                webView.post {
                    webView.evaluateJavascript(
                        "if(window.onNativeMosquesFound){window.onNativeMosquesFound($jsonStr, $userLat, $userLng);}",
                        null
                    )
                }
            }
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
        fun requestNativeGoogleMapsMosques(lat: Double, lng: Double) {
            searchGoogleMapsAndGeocoderMosques(lat, lng)
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
            val item = DhikrItem("custom", "custom", categoryTitle, text, 1, reward)
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
