package org.syndes.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.syndes.terminal.databinding.ActivityFairDistributionBinding

class FairDistributionActivity : AppCompatActivity() {

    private val MAX_PARTICIPANTS_FOR_UI = 500
    private lateinit var binding: ActivityFairDistributionBinding
    private var lastResultText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFairDistributionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Применяем стили
        val inputBackground = createInputBackground()
        val buttonBackground = createButtonBackground()

        binding.etTotalAmount.background = inputBackground
        binding.etParticipantsCount.background = inputBackground
        binding.etItemName.background = inputBackground
        binding.etParticipantName.background = inputBackground
        
        binding.btnCalculate.background = buttonBackground
        binding.btnCopy.background = buttonBackground

        binding.btnCalculate.setOnClickListener {
            calculateDistribution()
        }

        binding.btnCopy.setOnClickListener {
            copyToClipboard()
        }
    }

    private fun createInputBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0xFF121212.toInt()) // Тёмный фон
            setStroke(4, 0xFF00E5FF.toInt()) // Неоновая рамка
            cornerRadius = 16f
        }
    }

    private fun createButtonBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0xFF00E5FF.toInt()) // Сплошной неоновый циан
            cornerRadius = 16f
        }
    }

    private fun calculateDistribution() {
        val totalStr = binding.etTotalAmount.text.toString().trim()
        val participantsStr = binding.etParticipantsCount.text.toString().trim()

        if (totalStr.isEmpty() || participantsStr.isEmpty()) {
            showToast("Заполните числовые поля")
            return
        }

        val total = totalStr.toLongOrNull()
        val participants = participantsStr.toIntOrNull()

        if (total == null || total < 0) {
            showToast("Некорректное общее количество")
            return
        }

        if (participants == null || participants <= 0) {
            showToast("Количество участников должно быть > 0")
            return
        }

        if (participants > MAX_PARTICIPANTS_FOR_UI) {
            showToast("Максимум $MAX_PARTICIPANTS_FOR_UI участников для отображения")
            return
        }

        hideKeyboard()

        val itemName = binding.etItemName.text.toString().trim().takeIf { it.isNotEmpty() } ?: "единиц"
        val participantName = binding.etParticipantName.text.toString().trim().takeIf { it.isNotEmpty() } ?: "участников"
        val capParticipant = participantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val baseAmount = total / participants
        val remainder = (total % participants).toInt()

        val participantsWithExtra = remainder
        val participantsWithBase = participants - remainder

        val fmtTotal = formatNumber(total)
        val fmtBase = formatNumber(baseAmount)
        val fmtBasePlus = formatNumber(baseAmount + 1)

        binding.resultsContainer.removeAllViews()
        
        // Безопасное отключение кнопки (избегаем ошибок ViewBinding с val)
        binding.btnCalculate.isClickable = false
        binding.btnCalculate.alpha = 0.5f

        val copyBuilder = StringBuilder()
        copyBuilder.append("РАСПРЕДЕЛЕНИЕ РЕСУРСОВ\n")
        copyBuilder.append("Всего: $fmtTotal $itemName\n")
        copyBuilder.append("Участников: $participants\n\n")

        val spannable = SpannableStringBuilder()
        
        if (remainder == 0) {
            spannable.append("Итого: $fmtTotal $itemName делятся абсолютно поровну между $participants $participantName.")
            copyBuilder.append("Результат: Абсолютно поровну по $fmtBase $itemName.\n")
        } else {
            spannable.append("Поскольку $fmtTotal не делится нацело на $participants, образуется остаток $remainder.\n\n")
            spannable.append("Для справедливого распределения:\n")
            
            appendColoredText(spannable, "• $participantsWithExtra $participantName получат по ", "#4DD0E1")
            appendColoredText(spannable, "$fmtBasePlus $itemName\n", "#00FFFF", true)
            
            appendColoredText(spannable, "• $participantsWithBase $participantName получат по ", "#4DD0E1")
            appendColoredText(spannable, "$fmtBase $itemName", "#00FFFF", true)

            copyBuilder.append("Результат:\n")
            copyBuilder.append("- $participantsWithExtra $participantName получат по $fmtBasePlus $itemName\n")
            copyBuilder.append("- $participantsWithBase $participantName получат по $fmtBase $itemName\n")
        }

        addResultText(spannable)
        addSpacer(32)

        val detailsTitle = TextView(this).apply {
            text = "ДЕТАЛИЗАЦИЯ:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF4DD0E1.toInt())
            setLetterSpacing(0.1f)
            setPadding(0, 0, 0, 16)
            isTextSelectable = true
        }
        binding.resultsContainer.addView(detailsTitle)
        copyBuilder.append("\nДЕТАЛИЗАЦИЯ:\n")

        for (i in 1..participants) {
            val amount = if (i <= remainder) baseAmount + 1 else baseAmount
            val isExtra = i <= remainder
            val fmtAmount = formatNumber(amount)

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 32, 32, 32)
                setBackgroundColor(if (i % 2 == 0) 0xFF121212.toInt() else 0xFF0A0A0A.toInt())
            }

            val participantText = TextView(this).apply {
                text = "$capParticipant №$i:"
                textSize = 15f
                setTextColor(0xFF80DEEA.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                isTextSelectable = true
            }

            val amountText = TextView(this).apply {
                text = "$fmtAmount $itemName"
                textSize = 16f
                setTypeface(null, if (isExtra) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isExtra) 0xFF00FFFF.toInt() else 0xFF00E5FF.toInt())
                gravity = Gravity.END
                isTextSelectable = true
            }

            rowLayout.addView(participantText)
            rowLayout.addView(amountText)
            binding.resultsContainer.addView(rowLayout)

            copyBuilder.append("$capParticipant №$i: $fmtAmount $itemName\n")
        }

        lastResultText = copyBuilder.toString()

        // Безопасное включение кнопки и показ второй кнопки
        binding.btnCalculate.isClickable = true
        binding.btnCalculate.alpha = 1.0f
        binding.btnCopy.setVisibility(View.VISIBLE) // Метод вместо присваивания
        
        binding.resultsContainer.post {
            binding.resultsContainer.requestFocus()
        }
    }

    private fun copyToClipboard() {
        if (lastResultText.isEmpty()) return
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Распределение ресурсов", lastResultText)
        clipboard.setPrimaryClip(clip)
        
        showToast("Результат скопирован в буфер обмена")
    }

    private fun appendColoredText(builder: SpannableStringBuilder, text: String, colorHex: String, isBold: Boolean = false) {
        val start = builder.length
        builder.append(text)
        val end = builder.length
        
        builder.setSpan(ForegroundColorSpan(android.graphics.Color.parseColor(colorHex)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (isBold) {
            builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun addResultText(spannable: SpannableStringBuilder) {
        val textView = TextView(this).apply {
            textSize = 16f
            setLineSpacing(0f, 1.4f)
            text = spannable
            isTextSelectable = true
            setTextIsSelectable(true)
        }
        binding.resultsContainer.addView(textView)
    }

    private fun addSpacer(heightPx: Int) {
        val view = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx)
        }
        binding.resultsContainer.addView(view)
    }

    private fun formatNumber(number: Number): String {
        return String.format("%,d", number.toLong()).replace(',', ' ')
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
