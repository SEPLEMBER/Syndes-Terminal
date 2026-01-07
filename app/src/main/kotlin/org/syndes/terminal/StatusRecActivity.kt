package org.syndes.terminal

import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
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

    // colors hardcoded per your theme
    private val bgColor = 0xFF000000.toInt() // #000000
    private val accentColor = 0xFFFF00B8.toInt() // #FF00B8

    private lateinit var container: LinearLayout
    private lateinit var batteryCircle: TextView
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status_rec)

        window.decorView.setBackgroundColor(bgColor)

        container = findViewById(R.id.status_container)
        batteryCircle = findViewById(R.id.battery_circle)

        // apply colors / fonts
        batteryCircle.setTextColor(accentColor)
        batteryCircle.typeface = Typeface.MONOSPACE

        // initial placeholder
        showRow("Device", Build.MODEL ?: "unsupported")
        showRow("Manufacturer", Build.MANUFACTURER ?: "unsupported")
        showRow("Product", Build.PRODUCT ?: "unsupported")

        // gather heavier info off the main thread
        io.execute {
            val rows = collectDeviceInfo()
            runOnUiThread {
                // clear any placeholders (we kept a couple) and add real rows
                container.removeAllViews()
                // top line with battery on left and title
                setupHeader()
                // add rows from collected info
                for ((label, value) in rows) {
                    showRow(label, value)
                }
            }
        }
    }

    private fun setupHeader() {
        // The layout already includes batteryCircle view at left in XML.
        // Update battery now (synchronously) and add a header label
        val title = findViewById<TextView>(R.id.status_title)
        title.setTextColor(accentColor)
        title.typeface = Typeface.MONOSPACE
        title.text = "Device Status"
    }

    private fun showRow(label: String, value: String) {
        // create a horizontal row with label (left) and value (right)
        val row = layoutInflater.inflate(R.layout.row_status_item, container, false)
        val tvLabel = row.findViewById<TextView>(R.id.item_label)
        val tvValue = row.findViewById<TextView>(R.id.item_value)
        tvLabel.text = label
        tvValue.text = value
        tvLabel.setTextColor(accentColor)
        tvValue.setTextColor(accentColor)
        tvLabel.typeface = Typeface.MONOSPACE
        tvValue.typeface = Typeface.MONOSPACE

        container.addView(row)
    }

    private fun collectDeviceInfo(): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()

        // Basic build info
        list.add("Model" to safeStr(Build.MODEL))
        list.add("Manufacturer" to safeStr(Build.MANUFACTURER))
        list.add("Product" to safeStr(Build.PRODUCT))
        list.add("Board" to safeStr(Build.BOARD))
        list.add("Hardware" to safeStr(Build.HARDWARE))
        list.add("Bootloader" to safeStr(Build.BOOTLOADER))
        list.add("Android version" to ("${safeStr(Build.VERSION.RELEASE)} (SDK ${Build.VERSION.SDK_INT})"))

        // Uptime
        try {
            val upMs = SystemClock.elapsedRealtime()
            list.add("Uptime" to formatElapsed(upMs))
        } catch (e: Exception) {
            list.add("Uptime" to "unsupported")
        }

        // CPU cores & info
        try {
            val cores = Runtime.getRuntime().availableProcessors()
            list.add("CPU cores" to cores.toString())

            val cpuShort = readFirstMatch("/proc/cpuinfo", listOf("Hardware", "model name", "Processor"))
            list.add("CPU model" to (cpuShort ?: "unsupported"))

            // try read cpu current freq for cpu0
            val freq = readFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
                ?: readFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq")
            list.add("CPU freq (kHz)" to (freq ?: "unsupported"))
        } catch (e: Exception) {
            list.add("CPU cores" to "unsupported")
            list.add("CPU model" to "unsupported")
            list.add("CPU freq (kHz)" to "unsupported")
        }

        // Memory
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val avail = bytesToHuman(mi.availMem)
            val total = if (Build.VERSION.SDK_INT >= 16) bytesToHuman(mi.totalMem) else readMemFromProc()
            list.add("RAM total" to (total ?: "unsupported"))
            list.add("RAM available" to avail)
        } catch (e: Exception) {
            list.add("RAM total" to "unsupported")
            list.add("RAM available" to "unsupported")
        }

        // Battery - also update circle on main thread via sticky intent
        val batteryPct = getBatteryPercentSync()
        runOnUiThread {
            batteryCircle.text = batteryPct ?: "N/A"
            // color already set in onCreate
        }
        list.add("Battery level" to (batteryPct?.plus("%") ?: "unsupported"))
        list.add("Battery status" to (getBatteryStatus() ?: "unsupported"))

        // Display
        try {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val dpi = metrics.densityDpi
            list.add("Display resolution" to "${w}x$h")
            list.add("Display DPI" to dpi.toString())
        } catch (e: Exception) {
            list.add("Display resolution" to "unsupported")
            list.add("Display DPI" to "unsupported")
        }

        // Sensors (names)
        try {
            val sm = getSystemService(SENSOR_SERVICE) as SensorManager
            val sensors = sm.getSensorList(Sensor.TYPE_ALL)
            if (sensors.isNotEmpty()) {
                val names = sensors.take(6).map { it.name }.joinToString(", ")
                val more = if (sensors.size > 6) " (+${sensors.size - 6} more)" else ""
                list.add("Sensors (sample)" to "$names$more")
                list.add("Sensors count" to sensors.size.toString())
            } else {
                list.add("Sensors (sample)" to "unsupported")
                list.add("Sensors count" to "0")
            }
        } catch (e: Exception) {
            list.add("Sensors (sample)" to "unsupported")
            list.add("Sensors count" to "unsupported")
        }

        // Android ID (available without special permission)
        try {
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            list.add("Android ID" to (androidId ?: "unsupported"))
        } catch (e: Exception) {
            list.add("Android ID" to "unsupported")
        }

        // GPU / GL info: not trivial without GL context -> mark unsupported
        list.add("GPU vendor" to "unsupported")
        list.add("GPU renderer" to "unsupported")
        list.add("GPU version" to "unsupported")

        // Network mac/address - restricted on modern Android -> unsupported to avoid permissions.
        list.add("WiFi MAC" to "unsupported")
        list.add("IP address" to "unsupported")

        // /proc/meminfo raw hint (if available)
        val meminfo = readFirstMatch("/proc/meminfo", listOf("MemTotal"))
        list.add("proc/meminfo (MemTotal)" to (meminfo ?: "unsupported"))

        return list
    }

    private fun safeStr(v: String?): String = v ?: "unsupported"

    private fun formatElapsed(ms: Long): String {
        var s = ms / 1000
        val days = s / 86400
        s %= 86400
        val hours = s / 3600
        s %= 3600
        val minutes = s / 60
        val seconds = s % 60
        val sb = StringBuilder()
        if (days > 0) sb.append("${days}d ")
        if (hours > 0 || sb.isNotEmpty()) sb.append("${hours}h ")
        if (minutes > 0 || sb.isNotEmpty()) sb.append("${minutes}m ")
        sb.append("${seconds}s")
        return sb.toString()
    }

    private fun readFirstLine(path: String): String? {
        return try {
            val f = File(path)
            if (!f.exists()) return null
            f.bufferedReader().use { it.readLine()?.trim() }
        } catch (e: Exception) {
            null
        }
    }

    private fun readFirstMatch(path: String, keys: List<String>): String? {
        return try {
            val f = File(path)
            if (!f.exists()) return null
            f.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    for (k in keys) {
                        if (line.contains(k, ignoreCase = true)) {
                            val parts = line.split(":", limit = 2)
                            if (parts.size >= 2) return parts[1].trim()
                            else return line.trim()
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun readMemFromProc(): String? {
        val v = readFirstMatch("/proc/meminfo", listOf("MemTotal"))
        return v?.let {
            // MemTotal: 123456 kB
            val parts = it.split(Regex("\\s+"))
            if (parts.isNotEmpty()) try {
                val kb = parts[0].toLong()
                bytesToHuman(kb * 1024)
            } catch (e: Exception) { null } else null
        }
    }

    private fun bytesToHuman(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var b = bytes.toDouble()
        var i = 0
        while (b >= 1024 && i < units.size - 1) {
            b /= 1024
            i++
        }
        return "${(b * 10.0).roundToInt() / 10.0} ${units[i]}"
    }

    private fun getBatteryPercentSync(): String? {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = registerReceiver(null, ifilter)
            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val pct = (level * 100) / scale
                    pct.toString()
                } else null
            } else {
                // fallback: BatteryManager property
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                val prop = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (prop >= 0) prop.toString() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getBatteryStatus(): String? {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = registerReceiver(null, ifilter) ?: return null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            null
        }
    }
}
