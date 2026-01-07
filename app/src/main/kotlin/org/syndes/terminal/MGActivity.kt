package org.syndes.terminal

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MGActivity : AppCompatActivity() {

    // Handler для отложенных вызовов
    private val handler = Handler(Looper.getMainLooper())

    // Хардкодированные строки для "LiveBoot"
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

    // Параметры скорости
    private val charDelay = 9L       // мс между символами (типинг)
    private val linePause = 150L     // пауза после полной строки
    private val finalPause = 300L    // пауза перед Finish

    // Views для сплэша
    private var overlay: View? = null
    private var bootText: TextView? = null
    private var cursor: TextView? = null
    private var bootHeader: TextView? = null

    // Режим: если сплэш уже завершён, не запускать повторно
    private var bootCompleted = false
    private var cursorRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mg)

        // основное UI
        val input = findViewById<EditText>(R.id.mg_input)
        val goButton = findViewById<Button>(R.id.mg_go_button)

        // Инициализация overlay views
        overlay = findViewById(R.id.mg_boot_overlay)
        bootText = findViewById(R.id.mg_boot_text)
        cursor = findViewById(R.id.mg_boot_cursor)
        bootHeader = findViewById(R.id.mg_boot_header)

        // Убедимся, что header и cursor на переднем плане
        bootHeader?.bringToFront()
        cursor?.bringToFront()

        // header виден сразу (текст уже задан в xml) — можно переопределить при желании:
        // bootHeader?.text = "тут текст..."
        bootHeader?.visibility = View.VISIBLE

        // сразу запускаем LiveBoot, если ещё не запускали
        if (!bootCompleted) startLiveBoot()

        // логика перехода по вводу
        fun handleInput() {
            val value = input.text.toString().trim()
            when (value) {
                "1" -> startActivity(Intent(this, FormulhMainActivity::class.java))
                "2" -> startActivity(Intent(this, ArrivalActivity::class.java))
                ""  -> Toast.makeText(this, "Введите 1 или 2", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this, "Неверный ввод — введите 1 или 2", Toast.LENGTH_SHORT).show()
            }
        }

        goButton.setOnClickListener { handleInput() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                handleInput()
                true
            } else false
        }

        // optional: упростим поле ввода визуально
        input.typeface = Typeface.MONOSPACE
    }

    override fun onDestroy() {
        super.onDestroy()
        // отменяем все отложенные задачи
        handler.removeCallbacksAndMessages(null)
        stopCursorBlink()
    }

    // --- LiveBoot implementation ---
    private fun startLiveBoot() {
        bootText?.text = ""  // очистим
        overlay?.visibility = View.VISIBLE
        overlay?.alpha = 1f
        // header уже установлен в xml и видим
        bootHeader?.visibility = View.VISIBLE
        cursor?.visibility = View.VISIBLE
        startCursorBlink()
        // Start printing lines sequentially
        printLinesSequentially(0)
    }

    private fun printLinesSequentially(index: Int) {
        if (index >= bootLines.size) {
            // все строки выведены, ждем и печатаем Finish
            handler.postDelayed({
                typeLine("Finish") {
                    // маленькая пауза и скрываем overlay
                    handler.postDelayed({
                        finishBootOverlay()
                    }, finalPause)
                }
            }, linePause)
            return
        }
        val line = bootLines[index]
        typeLine(line) {
            // после печати строки добавляем перевод строки и паузу, затем следующая
            appendToBoot("\n")
            handler.postDelayed({
                printLinesSequentially(index + 1)
            }, linePause)
        }
    }

    // печатает строку посимвольно в bootText, затем вызывает onComplete
    private fun typeLine(line: String, onComplete: () -> Unit) {
        var pos = 0
        val runnable = object : Runnable {
            override fun run() {
                if (pos <= line.length - 1) {
                    appendToBoot(line[pos].toString())
                    pos++
                    handler.postDelayed(this, charDelay)
                } else {
                    onComplete()
                }
            }
        }
        handler.post(runnable)
    }

    // безопасно добавляет текст в bootText, сохраняя курсор справа
    private fun appendToBoot(s: String) {
        bootText?.let { tv ->
            val current = tv.text?.toString() ?: ""
            tv.text = current + s
            // скроллим вниз, если нужно (bootText в ScrollView делается в xml)
            val scroll = findViewById<View>(R.id.mg_boot_scroll)
            scroll?.post {
                if (scroll is android.widget.ScrollView) {
                    scroll.fullScroll(android.view.View.FOCUS_DOWN)
                }
            }
        }
    }

    private fun finishBootOverlay() {
        // остановим курсор
        stopCursorBlink()

        // Плавно скрываем overlay (header и логи — дочерние элементы overlay)
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

    // --- курсор (мигающий) ---
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
