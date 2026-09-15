package org.syndes.terminal

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Random

class BrPulseActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()

    private val bgColor = Color.parseColor("#0A0A0A")
    private val neonBrown = Color.parseColor("#B46A3C")
    private val neonBrownHint = Color.parseColor("#88B46A3C")

    private var isRunning = false

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etRooms: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val titleText = "BrPulse"
    private val defaultStatusText = "Ready"
    private val defaultHintText = "One site per line"
    private val startButtonText = "START TIMER"
    private val stopButtonText = "STOP TIMER"
    private val addAtLeastOneSiteText = "Add at least one site"
    private val noSitesInListText = "No sites in list"
    private val timerStartedText = "Timer started"
    private val timerStoppedText = "Timer stopped"
    private val nextOpenPrefix = "Next open in "
    private val nextOpenSuffix = " sec"
    private val openingPrefix = "Opening: "
    private val noBrowserFoundPrefix = "No browser found for:\n"

    private val defaultSitesText = """
        https://google.com
        https://microsoft.com
        https://minecraft.net
        https://vk.com
        https://ya.ru
    """.trimIndent()

    private var sites: List<String> = emptyList()

    private val pulseRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val currentSites = readSitesFromField()
            if (currentSites.isEmpty()) {
                tvStatus.text = noSitesInListText
                stopPulse()
                return
            }

            sites = currentSites
            openRandomSite()
            scheduleNext()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_br_pulse)

        tvTitle = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)
        etRooms = findViewById(R.id.etRooms)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        tvTitle.text = titleText
        tvTitle.setTextColor(neonBrown)
        tvTitle.setShadowLayer(14f, 0f, 0f, neonBrown)

        tvStatus.text = defaultStatusText
        tvStatus.setTextColor(neonBrown)
        tvStatus.setShadowLayer(12f, 0f, 0f, neonBrown)

        etRooms.setText(defaultSitesText)
        etRooms.setTextColor(neonBrown)
        etRooms.setHintTextColor(neonBrownHint)
        etRooms.setBackgroundColor(Color.parseColor("#141414"))
        etRooms.setShadowLayer(8f, 0f, 0f, neonBrown)
        etRooms.hint = defaultHintText
        etRooms.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

        btnStart.text = startButtonText
        btnStart.setBackgroundColor(Color.parseColor("#1A1A1A"))
        btnStart.setTextColor(neonBrown)
        btnStart.setOnClickListener { startPulse() }

        btnStop.text = stopButtonText
        btnStop.setBackgroundColor(Color.parseColor("#1A1A1A"))
        btnStop.setTextColor(neonBrown)
        btnStop.setOnClickListener { stopPulse() }
    }

    private fun startPulse() {
        val currentSites = readSitesFromField()
        if (currentSites.isEmpty()) {
            tvStatus.text = addAtLeastOneSiteText
            return
        }

        sites = currentSites
        isRunning = true
        tvStatus.text = timerStartedText
        scheduleNext()
    }

    private fun scheduleNext() {
        if (!isRunning) return

        val delaySeconds = random.nextInt(101) + 20
        val delayMillis = delaySeconds * 1000L

        tvStatus.text = nextOpenPrefix + delaySeconds + nextOpenSuffix
        handler.removeCallbacks(pulseRunnable)
        handler.postDelayed(pulseRunnable, delayMillis)
    }

    private fun openRandomSite() {
        if (sites.isEmpty()) return

        val url = sites[random.nextInt(sites.size)]
        tvStatus.text = openingPrefix + url

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            tvStatus.text = noBrowserFoundPrefix + url
        }
    }

    private fun stopPulse() {
        isRunning = false
        handler.removeCallbacks(pulseRunnable)
        tvStatus.text = timerStoppedText
    }

    private fun readSitesFromField(): List<String> {
        return etRooms.text
            .toString()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pulseRunnable)
    }
}
