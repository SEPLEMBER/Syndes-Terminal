package org.syndes.terminal

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.syndes.terminal.databinding.ActivityFinIncomeBinding
import java.util.Locale

class FinIncomeActivity : AppCompatActivity() {

    // Защита от утечек памяти
    private var _binding: ActivityFinIncomeBinding? = null
    private val binding get() = _binding!!

    // Единицы времени для ввода
    private val incomeUnits = arrayOf(
        "Месяц", 
        "Год", 
        "Неделя", 
        "День", 
        "Час (24ч)", 
        "Рабочий час (8ч)"
    )

    // Используем Locale.US, чтобы десятичный разделитель был точкой (как в вашем примере: 120000.00)
    private val techFormat = Locale.US

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Защита от скриншотов и скрытие из меню недавних приложений
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        _binding = ActivityFinIncomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка Spinner
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, incomeUnits)
        binding.spinnerIncomeUnit.adapter = unitAdapter
        binding.spinnerIncomeUnit.setSelection(0) // По умолчанию "Месяц"

        binding.btnCalculate.setOnClickListener {
            calculateIncome()
        }
    }

    private fun calculateIncome() {
        hideErrors()

        val amountStr = binding.etIncomeAmount.text.toString()
        val selectedUnit = binding.spinnerIncomeUnit.selectedItem.toString()

        // Валидация
        val inputValue = amountStr.toDoubleOrNull()
        if (inputValue == null || inputValue <= 0) {
            showError(binding.errAmount, "Введите корректную сумму > 0")
            return
        }

        // --- ПЕРЕВОД В БАЗОВУЮ ЕДИНИЦУ (Доход в 1 день) ---
        // Используем стандарт: 1 месяц = 30 дней, 1 год = 360 дней
        val dailyIncome = when (selectedUnit) {
            "Месяц" -> inputValue / 30.0
            "Год" -> inputValue / 360.0
            "Неделя" -> inputValue / 7.0
            "День" -> inputValue
            "Час (24ч)" -> inputValue * 24.0
            "Рабочий час (8ч)" -> inputValue * 8.0
            else -> 0.0
        }

        // --- РАСЧЁТ ВСЕХ ОСТАЛЬНЫХ ЗНАЧЕНИЙ ОТ БАЗОВОЙ ---
        val monthlyIncome = dailyIncome * 30.0
        val yearlyIncome = dailyIncome * 360.0
        val weeklyIncome = dailyIncome * 7.0
        val hourlyIncome24 = dailyIncome / 24.0
        val hourlyIncome8 = dailyIncome / 8.0

        // --- ФОРМИРОВАНИЕ ВЫВОДА ---
        val resultText = buildString {
            appendLine("= Месячный доход: ${String.format(techFormat, "%.2f", monthlyIncome)}")
            appendLine("= В год: ${String.format(techFormat, "%.2f", yearlyIncome)}")
            appendLine("= В неделю (≈): ${String.format(techFormat, "%.2f", weeklyIncome)}")
            appendLine("= В день (30д/мес): ${String.format(techFormat, "%.2f", dailyIncome)}")
            appendLine("= В час (24ч): ${String.format(techFormat, "%.4f", hourlyIncome24)}")
            appendLine("= За рабочий час (8ч): ${String.format(techFormat, "%.2f", hourlyIncome8)}")
        }

        binding.tvResult.text = resultText.trimEnd()
    }

    private fun showError(errorView: TextView, message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideErrors() {
        binding.errAmount.visibility = View.GONE
    }

    // Очистка binding
    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
