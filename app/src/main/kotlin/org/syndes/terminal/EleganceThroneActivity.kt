package org.syndes.terminal

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class EleganceThroneActivity : AppCompatActivity() {

    private lateinit var smaliConv: EditText
    private lateinit var dexI: EditText
    private lateinit var saveButton: Button
    private lateinit var unsaveButton: Button
    private lateinit var clearButton: Button
    private lateinit var exitButton: Button

private val passwordHint = "Contraseña"
private val textHint = "Texto"
private val codingText = "Codificar"
private val uncodingText = "Decodificar"
private val clearText = "Limpiar"
private val exitText = "Salir"
private val emptyPasswordMessage = "La contraseña está vacía."
private val emptyTextMessage = "El texto está vacío."
private val codingFailedMessage = "La codificación falló."
private val uncodingFailedMessage = "La decodificación falló."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_elegance_throne)

        smaliConv = findViewById(R.id.smaliConv)
        dexI = findViewById(R.id.dexI)
        saveButton = findViewById(R.id.saveButton)
        unsaveButton = findViewById(R.id.unsaveButton)
        clearButton = findViewById(R.id.clearButton)
        exitButton = findViewById(R.id.exitButton)

        smaliConv.hint = passwordHint
        dexI.hint = textHint
        saveButton.text = codingText
        unsaveButton.text = uncodingText
        clearButton.text = clearText
        exitButton.text = exitText

        saveButton.setOnClickListener { codingFlow() }
        unsaveButton.setOnClickListener { uncodingFlow() }
        clearButton.setOnClickListener { clearFields() }
        exitButton.setOnClickListener { finishAffinity() }
    }

    private fun codingFlow() {
        val password = smaliConv.text?.toString().orEmpty()
        val plaintext = dexI.text?.toString().orEmpty()

        if (password.isBlank()) {
            toast(emptyPasswordMessage)
            return
        }

        if (plaintext.isBlank()) {
            toast(emptyTextMessage)
            return
        }

        val passwordChars = password.toCharArray()
        try {
            val aes = Secure.Coding(passwordChars, plaintext)
            val elegant = Secure2.Coding(aes).orEmpty()

            dexI.setText(elegant)
            if (elegant.isNotEmpty()) dexI.setSelection(elegant.length)
        } catch (e: Exception) {
            toast(e.message ?: codingFailedMessage)
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    private fun uncodingFlow() {
        val password = smaliConv.text?.toString().orEmpty()
        val input = dexI.text?.toString().orEmpty()

        if (password.isBlank()) {
            toast(emptyPasswordMessage)
            return
        }

        if (input.isBlank()) {
            toast(emptyTextMessage)
            return
        }

        val passwordChars = password.toCharArray()
        try {
            val decoded = Secure2.Uncoding(input).orEmpty()
            val plaintext = Secure.Uncoding(passwordChars, decoded).orEmpty()

            dexI.setText(plaintext)
            if (plaintext.isNotEmpty()) dexI.setSelection(plaintext.length)
        } catch (e: Exception) {
            toast(e.message ?: uncodingFailedMessage)
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    private fun clearFields() {
        smaliConv.text?.clear()
        dexI.text?.clear()
        smaliConv.requestFocus()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
