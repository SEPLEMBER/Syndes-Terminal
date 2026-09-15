package es.zelliot.epubeditor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import es.zelliot.epubeditor.databinding.ActivityRetrMetrBinding

class RetrMetrActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRetrMetrBinding

    private val pureBlack = Color.parseColor("#000000")
    private val neonRed = Color.parseColor("#FF003C")

    private val promptText = "root@unsed-mst:~# "
    private val welcomeText = "SYSTEM INITIALIZED.\nAVANGARD MODULES READY.\nENTER COMMAND TO NAVIGATE.\n\n"
    private val errorText = "ERR: UNKNOWN COMMAND OR MODULE NOT FOUND."
    private val hintText = "type command..."

    private val outputHistory = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        binding = ActivityRetrMetrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rootLayout.setBackgroundColor(pureBlack)

        applyNeonStyle(binding.tvOutput)
        binding.tvOutput.text = welcomeText
        outputHistory.append(welcomeText)

        applyNeonStyle(binding.tvPrompt)
        binding.tvPrompt.text = promptText

        applyNeonStyle(binding.etCommand)
        binding.etCommand.hint = hintText
        binding.etCommand.setHintTextColor(Color.parseColor("#88FF003C"))

        binding.etCommand.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                processCommand()
                true
            } else {
                false
            }
        })

        binding.etCommand.requestFocus()
    }

    private fun applyNeonStyle(textView: TextView) {
        textView.setTextColor(neonRed)
        textView.setShadowLayer(12f, 0f, 0f, neonRed)
    }

    private fun processCommand() {
        val rawInput = binding.etCommand.text.toString()
        val command = rawInput.trim().uppercase()

        appendToOutput("${promptText}$rawInput\n")

        if (command.isEmpty()) {
            binding.etCommand.setText("")
            return
        }

        when (command) {
            "ELP", "ELPRIME", "ZLS" -> {
                appendToOutput("Launching ElPrime module...\n\n")
                startActivity(Intent(this, ElPrimeActivity::class.java))
            }

            "SAF", "NEXYD" -> {
                appendToOutput("Launching Nexyd module...\n\n")
                startActivity(Intent(this, NEXYDACTIVITY::class.java))
            }

            "BRPULSE", -> {
                appendToOutput("Launching Pulse module...\n\n")
                startActivity(Intent(this, BrPulseActivity::class.java))
            }

                        "CLIP", "CLP", "CLPS" -> {
                appendToOutput("Launching CLPS module...\n\n")
                startActivity(Intent(this, ClipActivity::class.java))
                        }

            "LUA INFO" -> {
                appendToOutput("Loading Lua Informatics...\n\n")
                startActivity(Intent(this, MainActivity::class.java))
            }

            else -> {
                appendToOutput("$errorText\n\n")
            }
        }

        binding.tvOutput.text = outputHistory.toString()
        binding.etCommand.setText("")
        binding.tvOutput.requestLayout()
    }

    private fun appendToOutput(text: String) {
        outputHistory.append(text)
    }
}
