package org.syndes.terminal

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.syndes.terminal.databinding.ActivityNetTrafficBinding
import java.util.Locale

class NetTrafficActivity : AppCompatActivity() {

    private var _binding: ActivityNetTrafficBinding? = null
    private val binding get() = _binding!!

    private val trafficUnits = arrayOf("GB", "MB", "KB")
    
    // Для сетевых расчётов используем Locale.US, чтобы десятичный разделитель был точкой
    private val techFormat = Locale.US

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        _binding = ActivityNetTrafficBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка Spinner для единиц измерения трафика
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, trafficUnits)
        binding.spinnerTrafficUnit.adapter = unitAdapter
        binding.spinnerTrafficUnit.setSelection(0) // По умолчанию GB

        binding.btnCalculate.setOnClickListener {
            calculateTraffic()
        }
    }

    private fun calculateTraffic() {
        hideErrors()

        val trafficStr = binding.etTotalTraffic.text.toString()
        val daysStr = binding.etPeriodDays.text.toString()
        val selectedUnit = binding.spinnerTrafficUnit.selectedItem.toString()

        // Валидация
        val inputValue = trafficStr.toDoubleOrNull()
        if (inputValue == null || inputValue <= 0) {
            showError(binding.errTraffic, "Введите корректный объём > 0")
            return
        }

        val days = daysStr.toIntOrNull()
        if (days == null || days <= 0) {
            showError(binding.errDays, "Введите количество дней > 0")
            return
        }

        // --- КОНВЕРТАЦИЯ В БАЗОВЫЕ GB (десятичная система: 1000) ---
        val totalGB = when (selectedUnit) {
            "GB" -> inputValue
            "MB" -> inputValue / 1000.0
            "KB" -> inputValue / 1_000_000.0
            else -> inputValue
        }

        // --- МАТЕМАТИКА СЕТЕВОГО ТРАФИКА ---
        val perDayGB = totalGB / days
        val perHourMB = (perDayGB * 1000.0) / 24.0
        val perMinMB = perHourMB / 60.0
        
        // Расчёт бит в секунду (1 GB = 8 000 000 000 бит)
        val totalBits = totalGB * 8_000_000_000.0
        val totalSeconds = days * 24.0 * 60.0 * 60.0
        val bitsPerSec = totalBits / totalSeconds
        
        val kbitPerSec = bitsPerSec / 1000.0
        val kbytePerSec = bitsPerSec / 8.0 / 1000.0

        // --- ФОРМИРОВАНИЕ ВЫВОДА ---
        val resultText = buildString {
            // Показываем исходный ввод и нормализованное значение в GB
            appendLine("= Ввод: $inputValue $selectedUnit → ${String.format(techFormat, "%.2f", totalGB)} GB")
            appendLine("= Период: $days ${getPluralizedDays(days)}")
            appendLine("= В день: ${String.format(techFormat, "%.3f", perDayGB)} GB")
            appendLine("= В час: ${String.format(techFormat, "%.3f", perHourMB)} MB")
            appendLine("= В мин: ${String.format(techFormat, "%.3f", perMinMB)} MB")
            appendLine("= В сек (бит/с): ${String.format(techFormat, "%.2f", kbitPerSec)} Kbit/s (~${String.format(techFormat, "%.2f", kbytePerSec)} KB/s)")
        }

        binding.tvResult.text = resultText.trimEnd()
    }

    private fun showError(errorView: TextView, message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideErrors() {
        binding.errTraffic.visibility = View.GONE
        binding.errDays.visibility = View.GONE
    }

    private fun getPluralizedDays(value: Int): String {
        val mod100 = value % 100
        val mod10 = value % 10
        return when {
            mod100 in 11..14 -> "дней"
            mod10 == 1 -> "день"
            mod10 in 2..4 -> "дня"
            else -> "дней"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
