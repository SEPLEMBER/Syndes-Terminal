package org.syndes.terminal

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.syndes.terminal.databinding.ActivityFinBudgetBinding
import java.text.NumberFormat
import java.util.Locale

class FinBudgetActivity : AppCompatActivity() {

    // Паттерн для предотвращения утечек памяти (Memory Leak)
    private var _binding: ActivityFinBudgetBinding? = null
    private val binding get() = _binding!!

    // Профессиональное форматирование валюты для РФ
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE: Запрет скриншотов и скрытие из меню "Недавние приложения"
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        _binding = ActivityFinBudgetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCalculate.setOnClickListener {
            calculateBudget()
        }
    }

    private fun calculateBudget() {
        hideErrors()

        val amountStr = binding.etBudgetAmount.text.toString()
        val daysStr = binding.etPeriodDays.text.toString()

        // Строгая валидация с ранним возвратом (Early Return)
        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            showError(binding.errAmount, "Введите корректную сумму > 0")
            return
        }

        val days = daysStr.toIntOrNull()
        if (days == null || days <= 0) {
            showError(binding.errDays, "Введите количество дней > 0")
            return
        }

        // --- МАТЕМАТИКА БЮДЖЕТА ---
        val dailyLimit = amount / days
        val weeklyLimit = dailyLimit * 7
        val monthlyLimit = dailyLimit * 30 // ≈30 дней, как в запросе
        val hourlyLimit = dailyLimit / 24
        val workHourlyLimit = dailyLimit / 8

        // --- ФОРМИРОВАНИЕ ВЫВОДА ---
        val resultText = buildString {
            appendLine("= Бюджет: ${currencyFormat.format(amount)} (лимит на $days ${getPluralizedDays(days)})")
            appendLine("= → Это лимиты — сколько можно тратить, чтобы запас хватил на указанный период:")
            appendLine("= В день: ${currencyFormat.format(dailyLimit)}")
            appendLine("= В неделю (7д): ${currencyFormat.format(weeklyLimit)}")
            appendLine("= В месяц (≈30д): ${currencyFormat.format(monthlyLimit)}")
            appendLine("= В час (24ч): ${currencyFormat.format(hourlyLimit)}")
            appendLine("= За рабочий час (8ч): ${currencyFormat.format(workHourlyLimit)}")
        }

        binding.tvResult.text = resultText.trimEnd()
    }

    private fun showError(errorView: TextView, message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideErrors() {
        binding.errAmount.visibility = View.GONE
        binding.errDays.visibility = View.GONE
    }

    /**
     * Правильное склонение слова "день" (1 день, 2 дня, 5 дней, 21 день и т.д.)
     */
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

    // Очистка binding для предотвращения утечек памяти
    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
