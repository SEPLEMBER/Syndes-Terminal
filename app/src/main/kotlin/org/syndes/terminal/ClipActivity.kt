package es.zelliot.epubeditor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClipActivity : AppCompatActivity() {

    private lateinit var button: TextView
    private lateinit var infoText: TextView
    private lateinit var spinner: TextView

    private val buttonText = "REBOARD"
    private val infoTextValue =
        "1-30 MODE."

    private val frames = arrayOf(
        "(|)",
        "(/)",
        "(-)",
        "(\\)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_clip)

        button = findViewById(R.id.buttonClip)
        infoText = findViewById(R.id.infoText)
        spinner = findViewById(R.id.spinner)

        button.text = buttonText
        infoText.text = infoTextValue
        spinner.text = "( )"

        button.setOnClickListener {
            button.isEnabled = false

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            val spinJob: Job = lifecycleScope.launch {
                var i = 0
                while (true) {
                    spinner.text = frames[i % frames.size]
                    i++
                    delay(80)
                }
            }

            lifecycleScope.launch {
                for (i in 1..30) {
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("", i.toString())
                    )
                    delay(50)
                }

                clipboard.setPrimaryClip(
                    ClipData.newPlainText("", "")
                )

                spinJob.cancel()

                spinner.text = "(✓)"
                button.isEnabled = true
            }
        }
    }
}
