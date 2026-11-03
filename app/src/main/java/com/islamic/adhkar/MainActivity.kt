package com.islamic.adhkar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()

        val prefs = getSharedPreferences("adhkar_prefs", Context.MODE_PRIVATE)

        val switchEnable = findViewById<Switch>(R.id.switchEnableReminders)
        val spinnerInterval = findViewById<Spinner>(R.id.spinnerInterval)
        val chkMorning = findViewById<CheckBox>(R.id.chkMorning)
        val chkEvening = findViewById<CheckBox>(R.id.chkEvening)
        val chkJawami = findViewById<CheckBox>(R.id.chkJawami)
        val chkPrayer = findViewById<CheckBox>(R.id.chkPrayer)
        val chkSleep = findViewById<CheckBox>(R.id.chkSleep)
        val chkRuqyah = findViewById<CheckBox>(R.id.chkRuqyah)
        val btnSaveSettings = findViewById<Button>(R.id.btnSaveSettings)
        val btnTestNotification = findViewById<Button>(R.id.btnTestNotification)
        val spinnerCategoryFilter = findViewById<Spinner>(R.id.spinnerCategoryFilter)
        val tvAdhkarList = findViewById<TextView>(R.id.tvAdhkarList)

        val intervalOptions = listOf(
            "كل 5 دقائق" to 5,
            "كل 15 دقيقة" to 15,
            "كل نصف ساعة (30 دقيقة)" to 30,
            "كل ساعة (60 دقيقة)" to 60,
            "كل ساعتين (120 دقيقة)" to 120
        )

        spinnerInterval.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            intervalOptions.map { it.first }
        )

        val categoryFilters = listOf(
            "جميع الأقسام" to "all",
            "☀️ أذكار الصباح" to "morning",
            "🌙 أذكار المساء" to "evening",
            "✨ جوامع الأذكار" to "jawami",
            "🕌 أذكار بعد الصلاة" to "prayer",
            "🛌 أذكار النوم" to "sleep",
            "🛡️ الرقية الشرعية" to "ruqyah"
        )

        spinnerCategoryFilter.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categoryFilters.map { it.first }
        )

        // استرجاع الإعدادات المحفوظة
        switchEnable.isChecked = prefs.getBoolean("reminders_enabled", true)
        chkMorning.isChecked = prefs.getBoolean("cat_morning", true)
        chkEvening.isChecked = prefs.getBoolean("cat_evening", true)
        chkJawami.isChecked = prefs.getBoolean("cat_jawami", true)
        chkPrayer.isChecked = prefs.getBoolean("cat_prayer", true)
        chkSleep.isChecked = prefs.getBoolean("cat_sleep", true)
        chkRuqyah.isChecked = prefs.getBoolean("cat_ruqyah", true)

        val savedMin = prefs.getInt("interval_minutes", 30)
        val selectedIndex = intervalOptions.indexOfFirst { it.second == savedMin }.coerceAtLeast(0)
        spinnerInterval.setSelection(selectedIndex)

        fun renderFilteredAdhkar(catKey: String) {
            val list = if (catKey == "all") {
                DhikrRepository.allAdhkar
            } else {
                DhikrRepository.allAdhkar.filter { it.category == catKey }
            }
            tvAdhkarList.text = list.joinToString("\n\n────────────────\n\n") {
                "【 ${it.categoryTitle} 】 (التكرار: ${it.targetCount})\n${it.text}\n💡 ${it.reward}"
            }
        }

        spinnerCategoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                renderFilteredAdhkar(categoryFilters[position].second)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        renderFilteredAdhkar("all")

        btnSaveSettings.setOnClickListener {
            val chosenMinutes = intervalOptions[spinnerInterval.selectedItemPosition].second
            val isEnabled = switchEnable.isChecked

            prefs.edit()
                .putBoolean("reminders_enabled", isEnabled)
                .putInt("interval_minutes", chosenMinutes)
                .putBoolean("cat_morning", chkMorning.isChecked)
                .putBoolean("cat_evening", chkEvening.isChecked)
                .putBoolean("cat_jawami", chkJawami.isChecked)
                .putBoolean("cat_prayer", chkPrayer.isChecked)
                .putBoolean("cat_sleep", chkSleep.isChecked)
                .putBoolean("cat_ruqyah", chkRuqyah.isChecked)
                .apply()

            if (isEnabled) {
                DhikrAlarmReceiver.scheduleNextReminder(this, chosenMinutes)
                Toast.makeText(this, "تم تفعيل الإشعارات كل $chosenMinutes دقيقة", Toast.LENGTH_SHORT).show()
            } else {
                DhikrAlarmReceiver.cancelReminder(this)
                Toast.makeText(this, "تم إيقاف الإشعارات التذكيرية", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestNotification.setOnClickListener {
            val sample = DhikrRepository.getRandomDhikr(
                chkMorning.isChecked,
                chkEvening.isChecked,
                chkJawami.isChecked,
                chkPrayer.isChecked,
                chkSleep.isChecked,
                chkRuqyah.isChecked
            )
            NotificationHelper.showDhikrPopupNotification(this, sample)
        }

        if (switchEnable.isChecked) {
            DhikrAlarmReceiver.scheduleNextReminder(this, savedMin)
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
