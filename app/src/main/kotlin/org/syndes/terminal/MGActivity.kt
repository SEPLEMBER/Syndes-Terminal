package org.syndes.terminal

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MGActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mg)

        val input = findViewById<EditText>(R.id.mg_input)
        val goButton = findViewById<Button>(R.id.mg_go_button)
        val instr = findViewById<TextView>(R.id.mg_instruction)

        // Небольшая локальная функция обработки ввода
        fun handleInput() {
            val value = input.text.toString().trim()
            when (value) {
                "1" -> {
                    // Переход в FormulhMainActivity
                    startActivity(Intent(this, FormulhMainActivity::class.java))
                }
                "2" -> {
                    // Переход в RexLedMainActivity
                    startActivity(Intent(this, RexLedMainActivity::class.java))
                }
                "" -> {
                    Toast.makeText(this, "Введите 1 или 2", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "Неверный ввод — введите 1 или 2", Toast.LENGTH_SHORT).show()
                }
            }
        }

        goButton.setOnClickListener { handleInput() }

        // Подтверждение через клавиатуру (Done / Go)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                handleInput()
                true
            } else {
                false
            }
        }
    }
}
