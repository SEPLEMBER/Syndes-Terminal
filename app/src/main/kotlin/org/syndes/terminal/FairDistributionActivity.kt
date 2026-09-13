package org.syndes.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FairDistributionActivity : AppCompatActivity() {

    private val MAX_PARTICIPANTS = 500
    private var isProcessing = false
    private var lastResultText = ""

    private lateinit var etTotal: EditText
    private lateinit var etParticipants: EditText
    private lateinit var etItem: EditText
    private lateinit var etParticipantName: EditText
    private lateinit var btnCalc: TextView
    private lateinit var btnCopy: TextView
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fair_distribution)

        // 1. Получаем ссылки напрямую через findViewById (без ViewBinding)
        etTotal = findViewById(R.id.etTotalAmount)
        etParticipants = findViewById(R.id.etParticipantsCount)
        etItem = findViewById(R.id.etItemName)
        etParticipantName = findViewById(R.id.etParticipantName)
        btnCalc = findViewById(R.id.btnCalculate)
        btnCopy = findViewById(R.id.btnCopy)
        container = findViewById(R.id.resultsContainer)

        applyStyles()

        btnCalc.setOnClickListener { calculate() }
        btnCopy.setOnClickListener { copyResult() }
    }

    private fun applyStyles() {
        val inputBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#121212"))
            setStroke(4, Color.parseColor("#00E5FF"))
            cornerRadius = 16f
        }
        
        val btnBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#00E5FF"))
            cornerRadius = 16f
        }

        etTotal.background = inputBg
        etParticipants.background = inputBg
        etItem.background = inputBg
        etParticipantName.background = inputBg
        
        btnCalc.background = btnBg
        btnCopy.background = btnBg
    }

    private fun calculate() {
        if (isProcessing) return
        isProcessing = true

        val totalStr = etTotal.text.toString().trim()
        val partStr = etParticipants.text.toString().trim()

        if (totalStr.isEmpty() || partStr.isEmpty()) {
            showToast("Заполните числовые поля")
            isProcessing = false
            return
        }

        val total = totalStr.toLongOrNull()
        val parts = partStr.toIntOrNull()

        if (total == null || total < 0) {
            showToast("Некорректное общее количество")
            isProcessing = false
            return
        }

        if (parts == null || parts <= 0) {
            showToast("Количество участников должно быть > 0")
            isProcessing = false
            return
        }

        if (parts > MAX_PARTICIPANTS) {
            showToast("Максимум $MAX_PARTICIPANTS участников")
            isProcessing = false
            return
        }

        hideKeyboard()

        val itemName = etItem.text.toString().trim().ifEmpty { "единиц" }
        val pName = etParticipantName.text.toString().trim().ifEmpty { "участников" }
        val capPName = pName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val base = total / parts
        val rem = (total % parts).toInt()

        val fmtTotal = formatNum(total)
        val fmtBase = formatNum(base)
        val fmtBasePlus = formatNum(base + 1)

        container.removeAllViews()
        
        // 2. Блокировка кнопки через явные методы setClickable() и setAlpha()
        // Это обходит любые баги компилятора с val/var
        btnCalc.setClickable(false)
        btnCalc.setAlpha(0.5f)

        val copyText = StringBuilder()
        copyText.append("РАСПРЕДЕЛЕНИЕ РЕСУРСОВ\n")
        copyText.append("Всего: $fmtTotal $itemName\n")
        copyText.append("Участников: $parts\n\n")

        val spannable = SpannableStringBuilder()
        
        if (rem == 0) {
            spannable.append("Итого: $fmtTotal $itemName делятся абсолютно поровну между $parts $pName.")
            copyText.append("Результат: Абсолютно поровну по $fmtBase $itemName.\n")
        } else {
            spannable.append("Поскольку $fmtTotal не делится нацело на $parts, образуется остаток $rem.\n\n")
            spannable.append("Для справедливого распределения:\n")
            
            addSpan(spannable, "• $rem $pName получат по ", "#4DD0E1")
            addSpan(spannable, "$fmtBasePlus $itemName\n", "#00FFFF", true)
            
            addSpan(spannable, "• ${parts - rem} $pName получат по ", "#4DD0E1")
            addSpan(spannable, "$fmtBase $itemName", "#00FFFF", true)

            copyText.append("Результат:\n")
            copyText.append("- $rem $pName получат по $fmtBasePlus $itemName\n")
            copyText.append("- ${parts - rem} $pName получат по $fmtBase $itemName\n")
        }

        val resultTv = TextView(this).apply {
            textSize = 16f
            setLineSpacing(0f, 1.4f)
            text = spannable
            isTextSelectable = true
        }
        container.addView(resultTv)
        
        addSpacer(32)

        val titleTv = TextView(this).apply {
            text = "ДЕТАЛИЗАЦИЯ:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#4DD0E1"))
            setLetterSpacing(0.1f)
            setPadding(0, 0, 0, 16)
            isTextSelectable = true
        }
        container.addView(titleTv)
        copyText.append("\nДЕТАЛИЗАЦИЯ:\n")

        for (i in 1..parts) {
            val amount = if (i <= rem) base + 1 else base
            val isExtra = i <= rem
            val fmtAmt = formatNum(amount)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 32, 32, 32)
                setBackgroundColor(if (i % 2 == 0) Color.parseColor("#121212") else Color.parseColor("#0A0A0A"))
            }

            val leftTv = TextView(this).apply {
                text = "$capPName №$i:"
                textSize = 15f
                setTextColor(Color.parseColor("#80DEEA"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                isTextSelectable = true
            }

            val rightTv = TextView(this).apply {
                text = "$fmtAmt $itemName"
                textSize = 16f
                setTypeface(null, if (isExtra) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isExtra) Color.parseColor("#00FFFF") else Color.parseColor("#00E5FF"))
                gravity = Gravity.END
                isTextSelectable = true
            }

            row.addView(leftTv)
            row.addView(rightTv)
            container.addView(row)

            copyText.append("$capPName №$i: $fmtAmt $itemName\n")
        }

        lastResultText = copyText.toString()

        // 3. Разблокировка кнопки и показ второй кнопки через setVisibility()
        btnCalc.setClickable(true)
        btnCalc.setAlpha(1.0f)
        btnCopy.setVisibility(View.VISIBLE)
        
        container.post { container.requestFocus() }
        isProcessing = false
    }

    private fun copyResult() {
        if (lastResultText.isEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Распределение", lastResultText))
        showToast("Скопировано в буфер обмена")
    }

    private fun addSpan(builder: SpannableStringBuilder, text: String, colorHex: String, bold: Boolean = false) {
        val start = builder.length
        builder.append(text)
        builder.setSpan(ForegroundColorSpan(Color.parseColor(colorHex)), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bold) builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun addSpacer(heightPx: Int) {
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx)
        })
    }

    private fun formatNum(number: Number): String {
        return String.format("%,d", number.toLong()).replace(',', ' ')
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
