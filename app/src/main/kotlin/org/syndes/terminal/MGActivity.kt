package org.syndes.terminal

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MGActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private val bootLines = listOf(
        "Initializing UEFI firmware interface..",
        "Locating EFI System Partition...",
        "Loading boot configuration...",
        "Checking Kotlin Libraries...",
        "Success",
        "Loading Mindbreaker UI...",
        "Applying neon shader...",
        "Executing kernel hand-off...",
        "Verifying integrity...",
        "Boot sequence continuing..."
    )

    private val charDelay = 2L
    private val linePause = 10L
    private val finalPause = 300L

    private var overlay: View? = null
    private var bootText: TextView? = null
    private var cursor: TextView? = null
    private var bootHeader: TextView? = null

    private var bootCompleted = false
    private var cursorRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mg)

        val input = findViewById<EditText>(R.id.mg_input)
        val goButton = findViewById<Button>(R.id.mg_go_button)

        overlay = findViewById(R.id.mg_boot_overlay)
        bootText = findViewById(R.id.mg_boot_text)
        cursor = findViewById(R.id.mg_boot_cursor)
        bootHeader = findViewById(R.id.mg_boot_header)

        bootHeader?.bringToFront()
        cursor?.bringToFront()
        bootHeader?.visibility = View.VISIBLE

        if (!bootCompleted) startLiveBoot()

        fun handleInput() {
            when (input.text.toString().trim()) {
                "1" -> startActivity(Intent(this, SettingsActivity::class.java))
                "2" -> startActivity(Intent(this, StatusRecActivity::class.java))

                "3" -> {
                    // App settings
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:org.syndes.terminal")
                    )
                    startActivity(intent)

                    Toast.makeText(
                        this,
                        "For reliable storage cleanup, use the standard data wipe function.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                "4" -> {
                    handler.postDelayed({
                        requestUninstallIfExists("org.syndes.rust")
                        requestUninstallIfExists("org.syndes.terminal")
                    }, 1500)
                }

                "" -> Toast.makeText(this, "Enter 1, 2, 3 or 4", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this, "Invalid input — enter 1, 2, 3 or 4", Toast.LENGTH_SHORT).show()
            }
        }

        goButton.setOnClickListener { handleInput() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                handleInput()
                true
            } else false
        }

        input.typeface = Typeface.MONOSPACE
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        stopCursorBlink()
    }

    // --- uninstall helpers ---

    private fun requestUninstallIfExists(pkg: String) {
        if (!isPackageInstalled(pkg)) return

        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$pkg")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    // --- LiveBoot ---

    private fun startLiveBoot() {
        bootText?.text = ""
        overlay?.visibility = View.VISIBLE
        overlay?.alpha = 1f
        bootHeader?.visibility = View.VISIBLE
        cursor?.visibility = View.VISIBLE
        startCursorBlink()
        printLinesSequentially(0)
    }

    private fun printLinesSequentially(index: Int) {
        if (index >= bootLines.size) {
            handler.postDelayed({
                typeLine("Finish") {
                    handler.postDelayed({ finishBootOverlay() }, finalPause)
                }
            }, linePause)
            return
        }

        val line = bootLines[index]
        typeLine(line) {
            appendToBoot("\n")
            handler.postDelayed({
                printLinesSequentially(index + 1)
            }, linePause)
        }
    }

    private fun typeLine(line: String, onComplete: () -> Unit) {
        var pos = 0
        val r = object : Runnable {
            override fun run() {
                if (pos < line.length) {
                    appendToBoot(line[pos].toString())
                    pos++
                    handler.postDelayed(this, charDelay)
                } else onComplete()
            }
        }
        handler.post(r)
    }

    private fun appendToBoot(s: String) {
        bootText?.let {
            it.text = (it.text?.toString() ?: "") + s
            findViewById<View>(R.id.mg_boot_scroll)?.post {
                if (it is android.widget.ScrollView) {
                    it.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    private fun finishBootOverlay() {
        stopCursorBlink()
        overlay?.animate()
            ?.alpha(0f)
            ?.setDuration(420)
            ?.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlay?.visibility = View.GONE
                    overlay?.alpha = 1f
                    bootHeader?.visibility = View.GONE
                    bootCompleted = true
                }
            })
    }

    private fun startCursorBlink() {
        stopCursorBlink()
        cursorRunnable = object : Runnable {
            var visible = true
            override fun run() {
                cursor?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
                visible = !visible
                handler.postDelayed(this, 480)
            }
        }
        handler.post(cursorRunnable!!)
    }

    private fun stopCursorBlink() {
        cursorRunnable?.let { handler.removeCallbacks(it) }
        cursor?.visibility = View.GONE
        cursorRunnable = null
    }
}
