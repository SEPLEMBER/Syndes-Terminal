package org.syndes.terminal

import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.hardware.SensorManager
import android.hardware.Sensor
import java.io.File
import java.lang.StringBuilder
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class StatusRecActivity : AppCompatActivity() {

    private val bgColor = 0xFF000000.toInt()
    private val accentColor = 0xFFFF00B8.toInt()

    private lateinit var container: LinearLayout
    private lateinit var batteryCircle: TextView

    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status_rec)

        window.decorView.setBackgroundColor(bgColor)

        container = findViewById(R.id.status_container)
        batteryCircle = findViewById(R.id.battery_circle)

        styleBatteryCircle()

        batteryCircle.text = "100"
        batteryCircle.setTextColor(accentColor)
        batteryCircle.typeface = Typeface.MONOSPACE

        showRow("Device", Build.MODEL ?: "unsupported")
        showRow("Manufacturer", Build.MANUFACTURER ?: "unsupported")
        showRow("Product", Build.PRODUCT ?: "unsupported")

        io.execute {
            val rows = collectDeviceInfo()
            runOnUiThread {
                container.removeAllViews()
                setupHeader()
                for ((label, value) in rows) {
                    showRow(label, value)
                }
            }
        }
    }

    private fun styleBatteryCircle() {
        val sizePx = dpToPx(56)
        val strokePx = dpToPx(2)

        val gd = GradientDrawable()
        gd.shape = GradientDrawable.OVAL
        gd.setSize(sizePx, sizePx)
        gd.setColor(Color.TRANSPARENT)
        gd.setStroke(strokePx, accentColor)

        batteryCircle.background = gd
        batteryCircle.gravity = Gravity.CENTER
        batteryCircle.textSize = 18f
        val pad = dpToPx(6)
        batteryCircle.setPadding(pad, pad, pad, pad)
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun setupHeader() {
        val title = findViewById<TextView>(R.id.status_title)
        title.setTextColor(accentColor)
        title.typeface = Typeface.MONOSPACE
        title.text = "Device Status"
    }

    private fun showRow(label: String, value: String) {
        val rowView = layoutInflater.inflate(R.layout.row_status_item, container, false)
        val tvLabel = rowView.findViewById<TextView>(R.id.item_label)
        val tvValue = rowView.findViewById<TextView>(R.id.item_value)

        tvLabel.text = label
        tvValue.text = value

        tvLabel.typeface = Typeface.MONOSPACE
        tvValue.typeface = Typeface.MONOSPACE

        tvLabel.setTextColor(accentColor)
        tvValue.setTextColor(accentColor)

        if (label == "TERMUX") {
            tvValue.setTextColor(if (value == "INSTALLED") Color.GREEN else Color.YELLOW)
        }

        if (label == "SECURITY") {
            tvValue.setTextColor(if (value == "PROTECTION ENABLED") Color.GREEN else Color.YELLOW)
        }

        container.addView(rowView)
    }

    private fun collectDeviceInfo(): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()

        list.add("Model" to safeStr(Build.MODEL))
        list.add("Manufacturer" to safeStr(Build.MANUFACTURER))
        list.add("Product" to safeStr(Build.PRODUCT))
        list.add("Board" to safeStr(Build.BOARD))
        list.add("Hardware" to safeStr(Build.HARDWARE))
        list.add("Bootloader" to safeStr(Build.BOOTLOADER))
        list.add("Android version" to "${safeStr(Build.VERSION.RELEASE)} (SDK ${Build.VERSION.SDK_INT})")

        try {
            list.add("Uptime" to formatElapsed(SystemClock.elapsedRealtime()))
        } catch (_: Exception) {
            list.add("Uptime" to "unsupported")
        }

        try {
            list.add("CPU cores" to Runtime.getRuntime().availableProcessors().toString())
        } catch (_: Exception) {
            list.add("CPU cores" to "unsupported")
        }

        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            list.add("RAM total" to bytesToHuman(mi.totalMem))
            list.add("RAM available" to bytesToHuman(mi.availMem))
        } catch (_: Exception) {
            list.add("RAM total" to "unsupported")
            list.add("RAM available" to "unsupported")
        }

        val batteryPct = getBatteryPercentSync()
        runOnUiThread { batteryCircle.text = batteryPct ?: "100" }

        list.add("Battery level" to (batteryPct?.plus("%") ?: "unsupported"))
        list.add("Battery status" to (getBatteryStatus() ?: "unsupported"))

        try {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            list.add("Display resolution" to "${metrics.widthPixels}x${metrics.heightPixels}")
            list.add("Display DPI" to metrics.densityDpi.toString())
        } catch (_: Exception) {
            list.add("Display resolution" to "unsupported")
            list.add("Display DPI" to "unsupported")
        }

        try {
            val sm = getSystemService(SENSOR_SERVICE) as SensorManager
            val sensors = sm.getSensorList(Sensor.TYPE_ALL)
            list.add("Sensors count" to sensors.size.toString())
        } catch (_: Exception) {
            list.add("Sensors count" to "unsupported")
        }

        try {
            list.add(
                "Android ID" to
                        (Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                            ?: "unsupported")
            )
        } catch (_: Exception) {
            list.add("Android ID" to "unsupported")
        }

        // === SECURITY CHECKS ===
        val termuxInstalled = isPackageInstalled("com.termux")
        list.add("TERMUX" to if (termuxInstalled) "INSTALLED" else "NOT INSTALLED")

        val nemesisInstalled = isPackageInstalled("com.nemesis.complex")
        list.add(
            "SECURITY" to
                    if (nemesisInstalled) "PROTECTION ENABLED"
                    else "SECURITY SYSTEM WEAKENED"
        )

        return list
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun safeStr(v: String?): String = v ?: "unsupported"

    private fun formatElapsed(ms: Long): String {
        var s = ms / 1000
        val d = s / 86400
        s %= 86400
        val h = s / 3600
        s %= 3600
        val m = s / 60
        val sec = s % 60

        val sb = StringBuilder()
        if (d > 0) sb.append("${d}d ")
        if (h > 0 || sb.isNotEmpty()) sb.append("${h}h ")
        if (m > 0 || sb.isNotEmpty()) sb.append("${m}m ")
        sb.append("${sec}s")
        return sb.toString()
    }

    private fun bytesToHuman(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var b = bytes.toDouble()
        var i = 0
        while (b >= 1024 && i < units.lastIndex) {
            b /= 1024
            i++
        }
        return "${(b * 10).roundToInt() / 10.0} ${units[i]}"
    }

    private fun getBatteryPercentSync(): String? {
        return try {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return null
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (scale > 0) ((level * 100) / scale).toString() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getBatteryStatus(): String? {
        return try {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            }
        } catch (_: Exception) {
            null
        }
    }
}
