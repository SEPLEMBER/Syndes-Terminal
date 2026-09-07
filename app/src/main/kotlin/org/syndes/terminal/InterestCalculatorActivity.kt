package org.syndes.terminal

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.syndes.terminal.databinding.ActivityInterestCalculatorBinding
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.pow

class InterestCalculatorActivity : AppCompatActivity() {

    // Паттерн для предотвращения утечек памяти (Memory Leak) с ViewBinding
    private var _binding: ActivityInterestCalculatorBinding? = null
    private val binding get() = _binding!!

    private val periodUnits = arrayOf("Годы", "Месяцы")
    private val capitalizationOptions = arrayOf("В конце срока (простые)", "Ежемесячно", "Ежеквартально", "Ежегодно")
    
    // Профессиональное форматирование чисел для РФ (пробелы между разрядами, запятая для копеек)
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }
    private val percentFormat = NumberFormat.getNumberInstance(Locale("ru", "RU")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // FLAG_SECURE: Запрет скриншотов, записи экрана и скрытие из меню "Недавние приложения"
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        _binding = ActivityInterestCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        
        binding.btnCalculate.setOnClickListener {
            calculateInterest()
        }
    }

    private fun setupSpinners() {
        val periodAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, periodUnits)
        binding.spinnerPeriodUnit.adapter = periodAdapter

        val capAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, capitalizationOptions)
        binding.spinnerCapitalization.adapter = capAdapter
        binding.spinnerCapitalization.setSelection(1) // По умолчанию: Ежемесячно
    }

    private fun calculateInterest() {
        hideErrors()

        val amountStr = binding.etAmount.text.toString()
        val rateStr = binding.etRate.text.toString()
        val periodStr = binding.etPeriod.text.toString()
        val monthlyDepositStr = binding.etMonthlyDeposit.text.toString()
        val inflationStr = binding.etInflation.text.toString()

        // 1. Строгая валидация с ранним возвратом (гарантирует, что дальше переменные НЕ null)
        val principal = amountStr.toDoubleOrNull()
        if (principal == null || principal <= 0) {
            showError(binding.errAmount, "Введите корректную сумму > 0")
            return
        }

        val rate = rateStr.toDoubleOrNull()
        if (rate == null || rate < 0) {
            showError(binding.errRate, "Введите корректную ставку >= 0")
            return
        }

        val periodValue = periodStr.toIntOrNull()
        if (periodValue == null || periodValue <= 0) {
            showError(binding.errPeriod, "Введите срок > 0")
            return
        }

        // Эти значения могут быть пустыми, по умолчанию 0.0 (уже не null)
        val monthlyDeposit = monthlyDepositStr.toDoubleOrNull() ?: 0.0
        val inflation = inflationStr.toDoubleOrNull() ?: 0.0

        val selectedUnit = binding.spinnerPeriodUnit.selectedItem.toString()
        val capitalization = binding.spinnerCapitalization.selectedItem.toString()

        // 2. Профессиональный банковский расчёт (помесячный цикл)
        val totalMonths = if (selectedUnit == "Годы") periodValue * 12 else periodValue
        val years = totalMonths / 12.0

        var balance: Double = principal
        var totalInvested: Double = principal

        for (month in 1..totalMonths) {
            // Пополнение в начале месяца
            balance += monthlyDeposit
            totalInvested += monthlyDeposit

            // Начисление процентов согласно выбранной капитализации
            when (capitalization) {
                "Ежемесячно" -> {
                    balance += balance * (rate / 100.0 / 12.0)
                }
                "Ежеквартально" -> {
                    if (month % 3 == 0) {
                        balance += balance * (rate / 100.0 / 4.0)
                    }
                }
                "Ежегодно" -> {
                    if (month % 12 == 0) {
                        balance += balance * (rate / 100.0)
                    }
                }
                "В конце срока (простые)" -> {
                    // Проценты не добавляются к телу вклада до самого конца цикла
                }
            }
        }

        // Если капитализация в конце срока, считаем простые проценты одним разом
        if (capitalization == "В конце срока (простые)") {
            val interestOnPrincipal = principal * (rate / 100.0) * years
            
            var interestOnDeposits = 0.0
            for (month in 1..totalMonths) {
                val remainingMonths = totalMonths - month + 1
                interestOnDeposits += monthlyDeposit * (rate / 100.0) * (remainingMonths / 12.0)
            }
            balance += (interestOnPrincipal + interestOnDeposits)
        }

        val totalProfit = balance - totalInvested

        // 3. Расчёт инфляции (реальная покупательная способность)
        val realValue = balance / (1.0 + inflation / 100.0).pow(years)
        val realProfit = realValue - totalInvested

        // 4. Формирование вывода
        val resultText = buildString {
            appendLine("= ${if (capitalization == "В конце срока (простые)") "Простые" else "Сложные"} проценты:")
            appendLine("= Начальная сумма: ${currencyFormat.format(principal)}")
            appendLine("= Ставка: ${percentFormat.format(rate)}% годовых")
            appendLine("= Срок: $periodValue ${getPluralizedUnit(periodValue, selectedUnit)}")
            if (monthlyDeposit > 0) {
                appendLine("= Пополнения: ${currencyFormat.format(monthlyDeposit)} / мес.")
            }
            appendLine("= Всего вложено: ${currencyFormat.format(totalInvested)}")
            appendLine("= Итоговая сумма: ${currencyFormat.format(balance)}")
            appendLine("= Начислено процентов: ${currencyFormat.format(totalProfit)}")
            
            if (inflation > 0) {
                appendLine("----------------------------------------")
                appendLine("= Учёт инфляции (${percentFormat.format(inflation)}%):")
                appendLine("= Реальная ценность: ${currencyFormat.format(realValue)}")
                appendLine("= Реальная прибыль: ${currencyFormat.format(realProfit)}")
            }
            
            appendLine("----------------------------------------")
            appendLine("= Средний доход:")
            appendLine("= В год: ${currencyFormat.format(totalProfit / years)}")
            appendLine("= В месяц: ${currencyFormat.format(totalProfit / totalMonths)}")
            appendLine("= В день: ${currencyFormat.format(totalProfit / (totalMonths * 30.4375))}")
        }

        binding.tvResult.text = resultText.trimEnd()
    }

    private fun showError(errorView: TextView, message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideErrors() {
        binding.errAmount.visibility = View.GONE
        binding.errRate.visibility = View.GONE
        binding.errPeriod.visibility = View.GONE
    }

    private fun getPluralizedUnit(value: Int, unit: String): String {
        val mod100 = value % 100
        val mod10 = value % 10
        return when (unit) {
            "Годы" -> when {
                mod100 in 11..14 -> "лет"
                mod10 == 1 -> "год"
                mod10 in 2..4 -> "года"
                else -> "лет"
            }
            "Месяцы" -> when {
                mod100 in 11..14 -> "месяцев"
                mod10 == 1 -> "месяц"
                mod10 in 2..4 -> "месяца"
                else -> "месяцев"
            }
            else -> unit.lowercase()
        }
    }

    // Очистка binding для предотвращения утечек памяти (Memory Leak)
    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}               appendLine("= Реальная прибыль: ${currencyFormat.format(realProfit)}")
