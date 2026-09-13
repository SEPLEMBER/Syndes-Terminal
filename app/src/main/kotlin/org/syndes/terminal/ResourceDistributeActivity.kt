package org.syndes.terminal

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.syndes.terminal.databinding.ActivityResourceDistributeBinding
import java.math.BigDecimal
import java.math.RoundingMode

class ResourceDistributeActivity : AppCompatActivity() {

    // Защита от утечек памяти
    private var _binding: ActivityResourceDistributeBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Защита от скриншотов и скрытие из меню недавних приложений
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        _binding = ActivityResourceDistributeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnDistribute.setOnClickListener {
            calculateDistribution()
        }
    }

    private fun calculateDistribution() {
        hideErrors()

        // Поддержка запятой (на случай, если пользователь скопировал число из Excel)
        val totalStr = binding.etTotalAmount.text.toString().replace(",", ".")
        val participantsStr = binding.etParticipants.text.toString()

        // Валидация общего количества (BigDecimal для поддержки огромных чисел без потери точности)
        val totalAmount = totalStr.toBigDecimalOrNull()
        if (totalAmount == null || totalAmount < BigDecimal.ZERO) {
            showError(binding.errAmount, "Введите корректное число >= 0")
            return
        }

        // Валидация участников
        val participants = participantsStr.toLongOrNull()
        if (participants == null || participants <= 0L) {
            showError(binding.errParticipants, "Количество участников должно быть > 0")
            return
        }

        // Крайний случай: 0 ресурсов
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            val resultText = buildString {
                appendLine("📊 ИТОГО РАСПРЕДЕЛЕНИЯ:")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("= Ресурсов нет. Всем по 0.")
                appendLine("= Остаток: 0")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }
            binding.tvResult.text = resultText.trimEnd()
            return
        }

        val pDec = BigDecimal.valueOf(participants)

        // 1. Точное математическое распределение (до 10 знаков после запятой)
        val exactShare = totalAmount.divide(pDec, 10, RoundingMode.HALF_UP)

        // 2. Целочисленное распределение (если ресурсы неделимы, как серверы/файлы)
        val totalWhole = totalAmount.setScale(0, RoundingMode.DOWN) // Отбрасываем дробную часть
        val baseWholeShare = totalWhole.divide(pDec, 0, RoundingMode.DOWN)
        val wholeRemainder = totalWhole.subtract(baseWholeShare.multiply(pDec))

        // 3. Дробная часть исходного числа (если вводили 10.5)
        val fractionalRemainder = totalAmount.subtract(totalWhole)

        // Математическая проверка (сумма частей должна быть равна исходному числу)
        val checkSum = baseWholeShare.multiply(pDec).add(wholeRemainder).add(fractionalRemainder)

        // --- ФОРМИРОВАНИЕ ВЫВОДА ---
        val resultText = buildString {
            appendLine("📊 ИТОГО РАСПРЕДЕЛЕНИЯ:")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            
            appendLine("= На каждого (точно): ${formatBd(exactShare)}")
            appendLine("= Целых на каждого: ${formatBd(baseWholeShare)}")
            
            if (wholeRemainder.compareTo(BigDecimal.ZERO) > 0) {
                appendLine("= Неразделенный остаток: ${formatBd(wholeRemainder)} (целых)")
            } else {
                appendLine("= Неразделенный остаток: 0")
            }
            
            if (fractionalRemainder.compareTo(BigDecimal.ZERO) > 0) {
                appendLine("= Дробная часть в остатке: ${formatBd(fractionalRemainder)}")
            }
            
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            
            appendLine("\n✅ Математическая проверка:")
            appendLine("= (${formatBd(baseWholeShare)} × $participants) + ${formatBd(wholeRemainder)} + ${formatBd(fractionalRemainder)}")
            appendLine("= Сумма: ${formatBd(checkSum)} (исходно: ${formatBd(totalAmount)})")
            
            if (checkSum.compareTo(totalAmount) == 0) {
                appendLine("Статус: ✔️ Сходится идеально!")
            } else {
                appendLine("Статус: ⚠️ Есть погрешность округления.")
            }
        }

        binding.tvResult.text = resultText.trimEnd()
    }

    // Безопасное форматирование BigDecimal (избегаем 0E-10 и научной нотации)
    private fun formatBd(bd: BigDecimal): String {
        if (bd.compareTo(BigDecimal.ZERO) == 0) return "0"
        return bd.stripTrailingZeros().toPlainString()
    }

    private fun showError(errorView: TextView, message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideErrors() {
        binding.errAmount.visibility = View.GONE
        binding.errParticipants.visibility = View.GONE
    }

    // Очистка binding
    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
