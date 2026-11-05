package com.islamic.adhkar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ضبط ألوان شريط الحالة (Status Bar) وشريط التنقل السفلي (Navigation Bar)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.parseColor("#064E3B") // أخضر إسلامي فاخر
            window.navigationBarColor = Color.parseColor("#FFFFFF") // متناسق مع شريط القائمة السفلي
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // تفعيل الأيقونات الداكنة لشريط التنقل ليكون واضحاً على الخلفية البيضاء
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
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")

        // تحميل التصميم مع دعم الهوامش الآمنة
        webView.loadUrl("file:///android_asset/adhkar_islamic_app.html")
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    inner class AndroidBridge(private val context: Context) {
        @JavascriptInterface
        fun scheduleReminder(intervalMinutes: Int, selectedCategoriesJson: String) {
            NotificationHelper.scheduleRepeatingReminder(
                context,
                intervalMinutes,
                selectedCategoriesJson
            )
        }

        @JavascriptInterface
        fun cancelReminder() {
            NotificationHelper.cancelReminder(context)
        }

        @JavascriptInterface
        fun triggerInstantReminder(category: String, dhikrText: String, rewardText: String) {
            NotificationHelper.showNotification(context, category, dhikrText, rewardText)
        }

        @JavascriptInterface
        fun setNavigationBarDark(isDark: Boolean) {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (isDark) {
                        window.navigationBarColor = Color.parseColor("#0F172A")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            window.decorView.systemUiVisibility = 0
                        }
                    } else {
                        window.navigationBarColor = Color.parseColor("#FFFFFF")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        }
                    }
                }
            }
        }
    }
}
