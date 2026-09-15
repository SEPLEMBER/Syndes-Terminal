package org.syndes.terminal

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.Nullable
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity

class ElPrimeActivity : AppCompatActivity() {

    companion object {
        private const val DOT_INTERVAL_MS = 500L
        private const val TOTAL_DELAY_MS = 5000L
        private const val TAP_RESET_MS = 1500L
    }

    private val baseText = "EE running"
    private var statusView: TextView? = null
    private var rootView: android.view.View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dotIndex = 0
    private val dots = arrayOf(".", "..", "...")
    private var tapCount = 0

    private val dotRunnable = object : Runnable {
        override fun run() {
            statusView?.text = baseText + dots[dotIndex]
            dotIndex = (dotIndex + 1) % dots.size
            handler.postDelayed(this, DOT_INTERVAL_MS)
        }
    }

    private val navigateRunnable = object : Runnable {
        override fun run() {
            cancelAllPending()
            startActivity(Intent(this@ElPrimeActivity, MainActivity::class.java))
            finish()
        }
    }

    private val resetTapRunnable = object : Runnable {
        override fun run() {
            tapCount = 0
        }
    }

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_el_prime)

        val ab: ActionBar? = supportActionBar
        ab?.let {
            it.title = "EER"
            it.setBackgroundDrawable(ColorDrawable(Color.parseColor("#9C27B0")))
        }

        statusView = findViewById(R.id.zls_status)
        rootView = findViewById(R.id.zls_root)

        handler.post(dotRunnable)
        handler.postDelayed(navigateRunnable, TOTAL_DELAY_MS)

        rootView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                tapCount++
                handler.removeCallbacks(resetTapRunnable)
                handler.postDelayed(resetTapRunnable, TAP_RESET_MS)

                if (tapCount >= 6) {
                    handler.removeCallbacks(navigateRunnable)
                    handler.removeCallbacks(dotRunnable)
                    handler.removeCallbacks(resetTapRunnable)
                    startActivity(Intent(this@ElPrimeActivity, EleganceThroneActivity::class.java))
                    finish()
                }
            }
            true
        }
    }

    private fun cancelAllPending() {
        handler.removeCallbacks(dotRunnable)
        handler.removeCallbacks(navigateRunnable)
        handler.removeCallbacks(resetTapRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAllPending()
    }
}
