package org.syndes.terminal

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.syndes.terminal.databinding.ActivityNtrBinding

class NTRActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNtrBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNtrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInputListener()
    }

    private fun setupInputListener() {
        binding.etCommandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val input = binding.etCommandInput.text.toString().trim()
                if (input.isNotEmpty()) {
                    executeCommand(input)
                    binding.etCommandInput.text.clear()
                }
                true
            } else {
                false
            }
        }
    }

    private fun executeCommand(command: String) {
        // Убираем ведущие нули, если пользователь ввел "01" вместо "1"
        val cmd = command.trimStart('0')
        
        appendToTerminal("Выполнение: $cmd...")

        when (cmd) {
            // --- ВНУТРЕННИЕ МОДУЛИ (org.syndes.terminal) ---
            "1" -> launchInternal(InterestCalculatorActivity::class.java, "Финансовые проценты")
            "2" -> launchInternal(FinBudgetActivity::class.java, "Бюджет")
            "3" -> launchInternal(TaxCalculatorActivity::class.java, "Налоги")
            "4" -> launchInternal(IpTaxCalculatorActivity::class.java, "УСН, Патент, Страхование ИП")
            "5" -> launchInternal(NetTrafficActivity::class.java, "Бюджет сетевого трафика")
            "6" -> launchInternal(FinIncomeActivity::class.java, "Доход")
            "7" -> launchInternal(TravelTimeActivity::class.java, "Скорость x Расстояние")

            // --- ВНЕШНИЕ МОДУЛИ (es.zelliot.epubeditor) ---
            "8" -> launchExternal("es.zelliot.epubeditor.EfficiencyActivity", "RSI, DUI")
            "9" -> launchExternal("es.zelliot.epubeditor.RushActivity", "Rush")
            "10" -> launchExternal("es.zelliot.epubeditor.PurMoneyActivity", "PurMoney")
            "11" -> launchExternal("es.zelliot.epubeditor.StockCalcActivity", "Запасы и потребление")
            "12" -> launchExternal("es.zelliot.epubeditor.ResourceCalcActivity", "Распределение ресурсов")
            "13" -> launchExternal("es.zelliot.epubeditor.HumanitarianCalcActivity", "Управление ресурсами")
            "14" -> launchExternal("es.zelliot.epubeditor.DynamicEffectActivity", "Динамический эффект")
            "15" -> launchExternal("es.zelliot.epubeditor.WorkCalcActivity", "Накопительный эффект")
            "16" -> launchExternal("es.zelliot.epubeditor.CalmCalcActivity", "Спокойствие при обретении ресурса")
            "17" -> launchExternal("es.zelliot.epubeditor.GeoCalcActivity", "Гео")
            "18" -> launchExternal("es.zelliot.epubeditor.DateCalculatorActivity", "Даты")
            "19" -> launchExternal("es.zelliot.epubeditor.CalcTwoActivity", "CPI + ROI")
            "20" -> launchExternal("es.zelliot.epubeditor.PurchaseActivity", "Покупка товара")
            "21" -> launchExternal("es.zelliot.epubeditor.TimeUtilityActivity", "Затраты времени")
            "22" -> launchExternal("es.zelliot.epubeditor.SpActivity", "Скорость шага")
            "23" -> launchExternal("es.zelliot.epubeditor.CalculatorActivity", "Прочее 1")
            "24" -> launchExternal("es.zelliot.epubeditor.UniversalCalcActivity", "Прочее 2")

            else -> {
                appendToTerminal("ОШИБКА: Модуль '$cmd' не найден.")
                showToast("Неверная команда")
            }
        }
    }

    private fun launchInternal(activityClass: Class<*>, moduleName: String) {
        try {
            startActivity(Intent(this, activityClass))
            appendToTerminal("УСПЕХ: Запущен внутренний модуль '$moduleName'.")
        } catch (e: Exception) {
            appendToTerminal("ОШИБКА: Внутренний модуль '$moduleName' не реализован или упал.")
            showToast("Ошибка запуска модуля")
        }
    }

    private fun launchExternal(fullClassName: String, moduleName: String) {
        val packageName = "es.zelliot.epubeditor"
        val intent = Intent().setClassName(packageName, fullClassName)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        try {
            startActivity(intent)
            appendToTerminal("УСПЕХ: Передан контроль внешнему модулю '$moduleName'.")
        } catch (e: ActivityNotFoundException) {
            appendToTerminal("ОШИБКА: Внешнее приложение '$packageName' не найдено.")
            showToast("Приложение не установлено")
        }
    }

    private fun appendToTerminal(text: String) {
        val currentText = binding.tvTerminalOutput.text.toString()
        // Заменяем последний курсор на текст и добавляем новый
        val cleanText = currentText.removeSuffix("> ОЖИДАНИЕ ВВОДА_")
        binding.tvTerminalOutput.text = "$cleanText$text\n> ОЖИДАНИЕ ВВОДА_"
        
        // Автопрокрутка вниз
        binding.tvTerminalOutput.post {
            binding.tvTerminalOutput.parent.requestChildFocus(binding.tvTerminalOutput, binding.tvTerminalOutput)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
