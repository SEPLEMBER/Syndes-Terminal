package org.syndes.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.syndes.terminal.databinding.ActivityResourceDistributeBinding
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResourceDistributeActivity : AppCompatActivity() {

    private var _binding: ActivityResourceDistributeBinding? = null
    private val binding get() = _binding!!

    // История расчётов (последние 5)
    private val calculationHistory = mutableListOf<String>()
    private val maxHistorySize = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        _binding = ActivityResourceDistributeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadHistory()
    }

    private fun setupUI() {
        // Переключение видимости полей весов
        binding.rgDistributionType.setOnCheckedChangeListener { _, checkedId ->
            val isWeighted = checkedId == R.id.rbWeighted
            binding.tvWeightsLabel.visibility = if (isWeighted) View.VISIBLE else View.GONE
            binding.etWeights.visibility = if (isWeighted) View.VISIBLE else View.GONE
        }

        binding.btnDistribute.setOnClickListener {
            calculateDistribution()
        }

        binding.btnClear.setOnClickListener {
            clearInputs()
        }

        binding.btnCopyResult.setOnClickListener {
            copyResultToClipboard()
        }

        binding.btnClearHistory.setOnClickListener {
            clearHistory()
        }
    }

    private fun calculateDistribution() {
        hideErrors()

        val totalStr = binding.etTotalAmount.text.toString().replace(",", ".")
        val participantsStr = binding.etParticipants.text.toString()
        val isWeighted = binding.rbWeighted.isChecked

        // 1. Валидация длины ввода (защита от слишком длинных строк)
        if (totalStr.length > 200 || participantsStr.length > 15) {
            showError(binding.errAmount, "Слишком длинный ввод. Сократите данные.")
            return
        }

        // 2. Валидация общего количества
        val totalAmount = totalStr.toBigDecimalOrNull()
        if (totalAmount == null || totalAmount < BigDecimal.ZERO) {
            showError(binding.errAmount, "Введите корректное число >= 0")
            return
        }

        // 3. Валидация участников с защитой от переполнения Int
        if (participantsStr.isEmpty()) {
            showError(binding.errParticipants, "Введите количество участников")
            return
        }
        
        val participants = participantsStr.toIntOrNull()
        if (participants == null) {
            showError(binding.errParticipants, "Число участников слишком велико или некорректно")
            return
        }
        if (participants <= 0) {
            showError(binding.errParticipants, "Количество участников должно быть > 0")
            return
        }

        // 4. Лимит участников (защита от зависания)
        if (participants > 1000) {
            showError(binding.errParticipants, "Максимум 1000 участников для весового расчёта")
            return
        }

        // Блокируем кнопку на время расчёта
        binding.btnDistribute.isEnabled = false
        binding.btnDistribute.text = "Расчёт..."

        // 5. Запускаем тяжёлые вычисления в фоновом потоке
        lifecycleScope.launch {
            val resultText = withContext(Dispatchers.Default) {
                if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
                    "📊 ИТОГО:\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n= Ресурсов нет. Всем по 0.\n= Остаток: 0\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                } else if (isWeighted) {
                    calculateWeightedDistribution(totalAmount, participants)
                } else {
                    calculateEqualDistribution(totalAmount, participants)
                }
            }

            // Возвращаемся в главный поток для обновления UI
            binding.btnDistribute.isEnabled = true
            binding.btnDistribute.text = "Распределить"

            if (resultText.startsWith("❌")) {
                showError(binding.errWeights, resultText.substring(2))
                return@launch
            }

            displayResult(resultText)
            addToHistory(resultText)
        }
    }

    private fun calculateEqualDistribution(totalAmount: BigDecimal, participants: Int): String {
        val pDec = BigDecimal.valueOf(participants.toLong())

        // Точное математическое распределение (20 знаков после запятой)
        val exactShare = totalAmount.divide(pDec, 20, RoundingMode.HALF_UP)

        // Целочисленное распределение
        val totalWhole = totalAmount.setScale(0, RoundingMode.DOWN)
        val baseWholeShare = totalWhole.divide(pDec, 0, RoundingMode.DOWN)
        val wholeRemainder = totalWhole.subtract(baseWholeShare.multiply(pDec))
        val fractionalRemainder = totalAmount.subtract(totalWhole)

        // Проверка суммы
        val checkSum = baseWholeShare.multiply(pDec).add(wholeRemainder).add(fractionalRemainder)

        // Процентное представление
        val percentage = BigDecimal.valueOf(100).divide(pDec, 4, RoundingMode.HALF_UP)

        return buildString {
            appendLine("📊 РАВНОМЕРНОЕ РАСПРЕДЕЛЕНИЕ")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("= На каждого (точно): ${formatBd(exactShare)}")
            appendLine("= Процент каждому: ${formatBd(percentage)}%")
            appendLine("")
            appendLine("= Целых на каждого: ${formatBd(baseWholeShare)}")
            appendLine("= Неразделенный остаток: ${formatBd(wholeRemainder)} (целых)")
            
            if (fractionalRemainder.compareTo(BigDecimal.ZERO) > 0) {
                appendLine("= Дробная часть: ${formatBd(fractionalRemainder)}")
            }
            
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("✅ Проверка:")
            appendLine("= (${formatBd(baseWholeShare)} × $participants) + ${formatBd(wholeRemainder)}")
            appendLine("= Сумма: ${formatBd(checkSum)} (исходно: ${formatBd(totalAmount)})")
            
            if (checkSum.compareTo(totalAmount) == 0) {
                appendLine("Статус: ✔️ Идеально!")
            } else {
                appendLine("Статус: ⚠️ Погрешность: ${formatBd(checkSum.subtract(totalAmount).abs())}")
            }
        }
    }

    private fun calculateWeightedDistribution(totalAmount: BigDecimal, participants: Int): String {
        val weightsStr = binding.etWeights.text.toString().trim()
        
        if (weightsStr.isEmpty()) {
            return "❌Введите веса для всех участников"
        }

        // Парсинг весов
        val weights = weightsStr.split(",").map { it.trim().replace(",", ".") }
        
        if (weights.size != participants) {
            return "❌Количество весов (${weights.size}) не совпадает с участниками ($participants)"
        }

        val weightValues = mutableListOf<BigDecimal>()
        for ((index, weightStr) in weights.withIndex()) {
            val weight = weightStr.toBigDecimalOrNull()
            if (weight == null || weight < BigDecimal.ZERO) {
                return "❌Некорректный вес #${index + 1}: '$weightStr'"
            }
            weightValues.add(weight)
        }

        // Сумма всех весов
        val totalWeight = weightValues.fold(BigDecimal.ZERO) { acc, w -> acc.add(w) }
        
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return "❌Сумма весов не может быть равна нулю"
        }

        // Распределение по весам
        val shares = weightValues.map { weight ->
            totalAmount.multiply(weight).divide(totalWeight, 20, RoundingMode.HALF_UP)
        }

        // Проверка суммы
        val checkSum = shares.fold(BigDecimal.ZERO) { acc, share -> acc.add(share) }

        return buildString {
            appendLine("📊 ВЕСОВОЕ РАСПРЕДЕЛЕНИЕ")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Сумма весов: ${formatBd(totalWeight)}")
            appendLine("")
            
            shares.forEachIndexed { index, share ->
                val weight = weightValues[index]
                val percent = weight.multiply(BigDecimal.valueOf(100)).divide(totalWeight, 4, RoundingMode.HALF_UP)
                appendLine("Участник ${index + 1} (вес ${formatBd(weight)}):")
                appendLine("  = ${formatBd(share)} (${formatBd(percent)}%)")
            }
            
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("✅ Проверка:")
            appendLine("= Сумма долей: ${formatBd(checkSum)}")
            appendLine("= Исходно: ${formatBd(totalAmount)}")
            
            val difference = checkSum.subtract(totalAmount).abs()
            if (difference.compareTo(BigDecimal.ZERO) == 0) {
                appendLine("Статус: ✔️ Идеально!")
            } else {
                appendLine("Статус: ⚠️ Погрешность: ${formatBd(difference)}")
            }
        }
    }

    // ИСПРАВЛЕНО: безопасная обрезка через take() вместо substring()
    private fun formatBd(bd: BigDecimal): String {
        if (bd.compareTo(BigDecimal.ZERO) == 0) return "0"
        // Убираем лишние нули и научную нотацию
        val plain = bd.stripTrailingZeros().toPlainString()
        // Ограничиваем длину для читаемости (take безопасен при любой длине)
        return if (plain.length > 20) {
            plain.take(20) + "..."
        } else {
            plain
        }
    }

    private fun displayResult(result: String) {
        binding.tvResult.text = result.trimEnd()
        binding.tvResult.visibility = View.VISIBLE
        binding.btnCopyResult.visibility = View.VISIBLE
    }

    // ИСПРАВЛЕНО: использование applicationContext для предотвращения утечек
    private fun copyResultToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Распределение ресурсов", binding.tvResult.text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(applicationContext, "✓ Результат скопирован", Toast.LENGTH_SHORT).show()
    }

    private fun addToHistory(result: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val shortResult = result.lines().take(3).joinToString(" | ")
        val historyEntry = "$timestamp | $shortResult..."
        
        calculationHistory.add(0, historyEntry)
        
        if (calculationHistory.size > maxHistorySize) {
            calculationHistory.removeAt(calculationHistory.size - 1)
        }
        
        updateHistoryDisplay()
    }

    private fun updateHistoryDisplay() {
        if (calculationHistory.isEmpty()) {
            binding.tvHistoryLabel.visibility = View.GONE
            binding.tvHistory.visibility = View.GONE
            binding.btnClearHistory.visibility = View.GONE
        } else {
            binding.tvHistoryLabel.visibility = View.VISIBLE
            binding.tvHistory.visibility = View.VISIBLE
            binding.btnClearHistory.visibility = View.VISIBLE
            
            binding.tvHistory.text = calculationHistory.joinToString("\n")
        }
    }

    private fun clearHistory() {
        calculationHistory.clear()
        updateHistoryDisplay()
    }

    private fun clearInputs() {
        binding.etTotalAmount.text.clear()
        binding.etParticipants.text.clear()
        binding.etWeights.text.clear()
        binding.tvResult.visibility = View.GONE
        binding.btnCopyResult.visibility = View.GONE
        hideErrors()
    }

    private fun showError(errorView: TextView, message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideErrors() {
        binding.errAmount.visibility = View.GONE
        binding.errParticipants.visibility = View.GONE
        binding.errWeights.visibility = View.GONE
    }

    private fun loadHistory() {
        updateHistoryDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
