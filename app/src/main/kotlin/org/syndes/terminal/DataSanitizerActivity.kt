package org.syndes.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class DataSanitizerActivity : AppCompatActivity() {

    companion object {
        private const val MAX_INPUT_SIZE_BYTES = 5 * 1024 * 1024 // 5 МБ
        private const val MAX_LINES = 100_000
        private const val MAX_LINE_LENGTH = 10_000
        private const val DISPLAY_LIMIT = 200

        private val COLOR_CYAN = Color.parseColor("#00FFFF")
        private val COLOR_GREEN = Color.parseColor("#00FF00")
        private val COLOR_RED = Color.parseColor("#FF5555")
        private val COLOR_GOLD = Color.parseColor("#FFD700")
        private val COLOR_GRAY = Color.parseColor("#AAAAAA")
        private val COLOR_BG_DARK = Color.parseColor("#121212")

        private val REGEX_INVISIBLE = Regex("[\\u200B-\\u200D\\uFEFF\\u00A0]")
        private val REGEX_SPACES = Regex("\\s+")
        private val REGEX_DIGITS = Regex("\\D")
        private val REGEX_INN = Regex("^\\d{10}$|^\\d{12}$")
        private val REGEX_OGRN = Regex("^\\d{13}$|^\\d{15}$")
        private val REGEX_EMAIL = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        private val REGEX_PHONE = Regex("(?:\\+7|8)?\\s*\\(?\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}")
    }

    private lateinit var etInput: EditText
    private lateinit var btnProcess: Button
    private lateinit var btnCopyValid: Button
    private lateinit var btnClear: Button
    private lateinit var llRulesContainer: LinearLayout
    private lateinit var llResultsContainer: LinearLayout
    private lateinit var tvStats: TextView
    private lateinit var spinnerFilter: Spinner

    private lateinit var cbTrimInvisible: CheckBox
    private lateinit var cbCollapseSpaces: CheckBox
    private lateinit var cbRemoveEmpty: CheckBox
    private lateinit var cbRemoveDuplicates: CheckBox
    private lateinit var cbValidateInn: CheckBox
    private lateinit var cbValidateOgrn: CheckBox
    private lateinit var cbNormalizePhones: CheckBox
    private lateinit var cbExtractEmails: CheckBox
    private lateinit var cbExtractPhones: CheckBox

    private var currentResults = listOf<ProcessResult>()
    private var processJob: Job? = null

    private data class ProcessResult(
        val original: String,
        val cleaned: String,
        val status: Status,
        val reason: String = ""
    )

    private enum class Status {
        UNCHANGED, FIXED, INVALID, REMOVED_EMPTY, REMOVED_DUPLICATE, EXTRACTED, ERROR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setupUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        processJob?.cancel()
    }

    private fun setupUI() {
        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(COLOR_BG_DARK)
            setPadding(16, 16, 16, 16)
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        TextView(this).apply {
            text = "🛡️ PRO: Детерминированный очиститель и Экстрактор"
            setTextColor(COLOR_CYAN)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 4)
            mainLayout.addView(this)
        }
        TextView(this).apply {
            text = "Математическая валидация (ИНН, ОГРН) и извлечение сущностей. Без ИИ."
            setTextColor(COLOR_GRAY)
            textSize = 12f
            setPadding(0, 0, 0, 16)
            mainLayout.addView(this)
        }

        llRulesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 16)
            mainLayout.addView(this)
        }

        addSectionTitle("🧹 Базовая очистка и форматирование")
        cbTrimInvisible = createCheckBox("Удалить невидимые символы (\\u200B, BOM, \\u00A0)")
        cbCollapseSpaces = createCheckBox("Сжать множественные пробелы в один")
        cbRemoveEmpty = createCheckBox("Удалить полностью пустые строки")
        cbRemoveDuplicates = createCheckBox("Удалить дубликаты строк (без учета регистра)")

        addSectionTitle("🏢 Строгие бизнес-валидаторы (Математика ФНС)")
        cbValidateInn = createCheckBox("Валидация ИНН (10/12 цифр, контрольная сумма)")
        cbValidateOgrn = createCheckBox("Валидация ОГРН/ОГРНИП (13/15 цифр, остаток от деления на 11)")
        cbNormalizePhones = createCheckBox("Нормализовать телефоны до +7 (XXX) XXX-XX-XX")

        addSectionTitle("🎯 Экстрактор данных (Извлечь из текста)")
        cbExtractEmails = createCheckBox("Извлечь ТОЛЬКО Email адреса (остальное отбросить)")
        cbExtractPhones = createCheckBox("Извлечь ТОЛЬКО Телефоны (остальное отбросить)")

        etInput = EditText(this).apply {
            hint = "Вставьте грязные данные или текст с мусором... (Лимит: 5 МБ, 100k строк)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#666666"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            backgroundTintList = android.content.res.ColorStateList.valueOf(COLOR_CYAN)
            mainLayout.addView(this)
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
            mainLayout.addView(this)
        }

        btnProcess = Button(this).apply {
            text = "⚙️ Обработать"
            setBackgroundColor(COLOR_CYAN)
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
            buttonsRow.addView(this)
        }

        btnClear = Button(this).apply {
            text = "🗑️ Очистить"
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            buttonsRow.addView(this)
        }

        tvStats = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
            mainLayout.addView(this)
        }

        spinnerFilter = Spinner(this).apply {
            adapter = ArrayAdapter(this@DataSanitizerActivity, android.R.layout.simple_spinner_dropdown_item, 
                listOf("Показать все", "Только исправленные/извлеченные", "Только ошибки", "Скрыть без изменений"))
            setBackgroundColor(Color.parseColor("#222222"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
            setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { displayResults(currentResults) }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            })
            mainLayout.addView(this)
        }

        llResultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
            mainLayout.addView(this)
        }

        btnCopyValid = Button(this).apply {
            text = "📋 Копировать валидные/извлеченные"
            setBackgroundColor(COLOR_GREEN)
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
            visibility = View.GONE
            mainLayout.addView(this)
        }

        scrollView.addView(mainLayout)
        setContentView(scrollView)

        btnProcess.setOnClickListener { runSanitization() }
        btnClear.setOnClickListener { clearAll() }
        btnCopyValid.setOnClickListener { copyValidResults() }
    }

    private fun addSectionTitle(text: String) {
        TextView(this).apply {
            this.text = text
            setTextColor(COLOR_GOLD)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 4)
            llRulesContainer.addView(this)
        }
    }

    private fun createCheckBox(text: String): CheckBox {
        return CheckBox(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 4, 0, 4)
            buttonTintList = android.content.res.ColorStateList.valueOf(COLOR_CYAN)
            llRulesContainer.addView(this)
        }
    }

    private fun runSanitization() {
        val rawText = etInput.text.toString()
        if (rawText.isBlank()) {
            Toast.makeText(this, "Введите данные", Toast.LENGTH_SHORT).show()
            return
        }

        if (rawText.length > MAX_INPUT_SIZE_BYTES) {
            Toast.makeText(this, "⚠️ Превышен лимит: ${MAX_INPUT_SIZE_BYTES / 1024 / 1024} МБ", Toast.LENGTH_LONG).show()
            return
        }

        val lines = rawText.split("\n")
        if (lines.size > MAX_LINES) {
            Toast.makeText(this, "⚠️ Превышен лимит: $MAX_LINES строк", Toast.LENGTH_LONG).show()
            return
        }

        processJob?.cancel()

        btnProcess.isEnabled = false
        btnProcess.text = "Обработка..."
        llResultsContainer.removeAllViews()
        btnCopyValid.visibility = View.GONE

        processJob = lifecycleScope.launch {
            val startTime = SystemClock.elapsedRealtime()
            val results = withContext(Dispatchers.Default) {
                processData(lines)
            }
            val elapsed = SystemClock.elapsedRealtime() - startTime

            withContext(Dispatchers.Main) {
                btnProcess.isEnabled = true
                btnProcess.text = "⚙️ Обработать"
                currentResults = results
                
                val total = results.size
                val fixed = results.count { it.status == Status.FIXED || it.status == Status.EXTRACTED }
                val invalid = results.count { it.status == Status.INVALID || it.status == Status.ERROR }
                val removed = results.count { it.status == Status.REMOVED_EMPTY || it.status == Status.REMOVED_DUPLICATE }
                
                tvStats.text = "⏱ ${elapsed}мс | Всего: $total | ✅ Исправлено/Извлечено: $fixed | ❌ Ошибки: $invalid | 🗑 Удалено: $removed"
                
                if (results.any { it.status != Status.INVALID && it.status != Status.ERROR && it.status != Status.REMOVED_EMPTY && it.status != Status.REMOVED_DUPLICATE }) {
                    btnCopyValid.visibility = View.VISIBLE
                }
                
                displayResults(results)
            }
        }
    }

    private fun processData(lines: List<String>): List<ProcessResult> {
        val results = mutableListOf<ProcessResult>()
        val seenLines = mutableSetOf<String>()

        for (line in lines) {
            try {
                if (line.length > MAX_LINE_LENGTH) {
                    results.add(ProcessResult(line, "", Status.ERROR, "Строка слишком длинная (>${MAX_LINE_LENGTH} символов)"))
                    continue
                }

                var current = line
                var status = Status.UNCHANGED
                var reason = ""

                if (cbTrimInvisible.isChecked) {
                    current = current.replace(REGEX_INVISIBLE, "")
                }
                if (cbCollapseSpaces.isChecked) {
                    current = current.replace(REGEX_SPACES, " ")
                }
                current = current.trim()

                if (cbRemoveEmpty.isChecked && current.isEmpty()) {
                    results.add(ProcessResult(line, "", Status.REMOVED_EMPTY, "Пустая строка"))
                    continue
                }

                if (cbRemoveDuplicates.isChecked) {
                    val lower = current.lowercase()
                    if (seenLines.contains(lower)) {
                        results.add(ProcessResult(line, "", Status.REMOVED_DUPLICATE, "Дубликат"))
                        continue
                    }
                    seenLines.add(lower)
                }

                if (cbExtractEmails.isChecked) {
                    val emails = REGEX_EMAIL.findAll(current).map { it.value }.toList()
                    if (emails.isNotEmpty()) {
                        current = emails.joinToString(", ")
                        status = Status.EXTRACTED
                        reason = "Извлечено ${emails.size} Email"
                    } else {
                        status = Status.INVALID
                        reason = "Email не найдены в строке"
                    }
                } else if (cbExtractPhones.isChecked) {
                    val phones = REGEX_PHONE.findAll(current).map { it.value }.toList()
                    if (phones.isNotEmpty()) {
                        val normalized = phones.mapNotNull { normalizePhone(it) }
                        if (normalized.isNotEmpty()) {
                            current = normalized.joinToString(", ")
                            status = Status.EXTRACTED
                            reason = "Извлечено ${normalized.size} Телефонов"
                        } else {
                            status = Status.INVALID
                            reason = "Телефоны не нормализуемы"
                        }
                    } else {
                        status = Status.INVALID
                        reason = "Телефоны не найдены в строке"
                    }
                } else {
                    if (cbValidateInn.isChecked && REGEX_INN.matches(current)) {
                        if (!isValidInn(current)) {
                            status = Status.INVALID
                            reason = "Ошибка контрольной суммы ИНН"
                        }
                    }
                    
                    if (cbValidateOgrn.isChecked && REGEX_OGRN.matches(current)) {
                        if (!isValidOgrn(current)) {
                            status = Status.INVALID
                            reason = "Ошибка контрольного числа ОГРН"
                        }
                    }

                    if (cbNormalizePhones.isChecked && status != Status.INVALID) {
                        val normalized = normalizePhone(current)
                        if (normalized != null && normalized != current) {
                            current = normalized
                            status = Status.FIXED
                            reason = "Телефон нормализован"
                        }
                    }
                }

                if (status == Status.UNCHANGED) reason = "Без изменений"
                results.add(ProcessResult(line, current, status, reason))
            } catch (e: Exception) {
                results.add(ProcessResult(line, "", Status.ERROR, "Ошибка обработки: ${e.javaClass.simpleName}"))
            }
        }
        return results
    }

    private fun normalizePhone(phone: String): String? {
        return try {
            val digits = phone.replace(REGEX_DIGITS, "")
            if (digits.length == 11 && (digits.startsWith("7") || digits.startsWith("8"))) {
                "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 9)}-${digits.substring(9)}"
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidInn(inn: String): Boolean {
        return try {
            if (inn.length == 10) {
                val w = intArrayOf(2, 4, 10, 3, 5, 9, 4, 6, 8)
                val sum = (0 until 9).sumOf { inn[it].digitToInt() * w[it] }
                (sum % 11) % 10 == inn[9].digitToInt()
            } else if (inn.length == 12) {
                val w11 = intArrayOf(7, 2, 4, 10, 3, 5, 9, 4, 6, 8)
                val w12 = intArrayOf(3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8)
                val sum11 = (0 until 10).sumOf { inn[it].digitToInt() * w11[it] }
                val sum12 = (0 until 11).sumOf { inn[it].digitToInt() * w12[it] }
                (sum11 % 11) % 10 == inn[10].digitToInt() && (sum12 % 11) % 10 == inn[11].digitToInt()
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidOgrn(ogrn: String): Boolean {
        return try {
            if (ogrn.length == 13) {
                val num = ogrn.substring(0, 12).toLongOrNull() ?: return false
                ((num % 11) % 10).toInt() == ogrn[12].digitToInt() // ИСПРАВЛЕНО: добавлено .toInt()
            } else if (ogrn.length == 15) {
                val num = ogrn.substring(0, 14).toLongOrNull() ?: return false
                ((num % 11) % 10).toInt() == ogrn[14].digitToInt() // ИСПРАВЛЕНО: добавлено .toInt()
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun displayResults(results: List<ProcessResult>) {
        llResultsContainer.removeAllViews()
        val filterMode = spinnerFilter.selectedItemPosition

        val filtered = when (filterMode) {
            1 -> results.filter { it.status == Status.FIXED || it.status == Status.EXTRACTED }
            2 -> results.filter { it.status == Status.INVALID || it.status == Status.ERROR }
            3 -> results.filter { it.status != Status.UNCHANGED }
            else -> results
        }

        if (filtered.isEmpty()) {
            TextView(this).apply {
                text = "Нет строк, соответствующих фильтру."
                setTextColor(COLOR_GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 32)
                llResultsContainer.addView(this)
            }
            return
        }

        val displayLimit = minOf(filtered.size, DISPLAY_LIMIT)
        for (i in 0 until displayLimit) {
            val res = filtered[i]
            if (res.status == Status.REMOVED_EMPTY || res.status == Status.REMOVED_DUPLICATE) continue

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(when (res.status) {
                    Status.FIXED -> Color.parseColor("#1A00FF00")
                    Status.EXTRACTED -> Color.parseColor("#1A00FFFF")
                    Status.INVALID, Status.ERROR -> Color.parseColor("#1AFF0000")
                    else -> Color.parseColor("#11FFFFFF")
                })
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 }
            }

            val header = TextView(this).apply {
                text = "${when (res.status) {
                    Status.FIXED -> "✅ ИСПРАВЛЕНО"
                    Status.EXTRACTED -> "🎯 ИЗВЛЕЧЕНО"
                    Status.INVALID -> "❌ ОШИБКА ВАЛИДАЦИИ"
                    Status.ERROR -> "⚠️ ОШИБКА ОБРАБОТКИ"
                    else -> "ℹ️ БЕЗ ИЗМЕНЕНИЙ"
                }} | ${res.reason}"
                setTextColor(when (res.status) {
                    Status.FIXED -> COLOR_GREEN
                    Status.EXTRACTED -> COLOR_CYAN
                    Status.INVALID, Status.ERROR -> COLOR_RED
                    else -> COLOR_GRAY
                })
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val content = TextView(this).apply {
                text = if (res.status == Status.EXTRACTED || res.status == Status.FIXED) {
                    "Исходник: ${res.original}\nРезультат: ${res.cleaned}"
                } else {
                    "Текст: ${res.original}"
                }
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextIsSelectable(true)
            }

            card.addView(header)
            card.addView(content)
            llResultsContainer.addView(card)
        }

        if (filtered.size > DISPLAY_LIMIT) {
            TextView(this).apply {
                text = "... и еще ${filtered.size - DISPLAY_LIMIT} строк (используйте кнопку копирования ниже)"
                setTextColor(COLOR_GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 16)
                llResultsContainer.addView(this)
            }
        }
    }

    private fun copyValidResults() {
        val validText = currentResults.filter { 
            it.status != Status.REMOVED_EMPTY && 
            it.status != Status.REMOVED_DUPLICATE && 
            it.status != Status.INVALID &&
            it.status != Status.ERROR
        }.joinToString("\n") { it.cleaned }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SanitizedData", validText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "✅ Скопировано (${validText.lines().size} строк)", Toast.LENGTH_SHORT).show()
    }

    private fun clearAll() {
        processJob?.cancel()
        etInput.text.clear()
        llResultsContainer.removeAllViews()
        tvStats.text = ""
        btnCopyValid.visibility = View.GONE
        currentResults = emptyList()
        
        cbTrimInvisible.isChecked = true
        cbCollapseSpaces.isChecked = true
        cbRemoveEmpty.isChecked = true
        cbRemoveDuplicates.isChecked = true
        cbValidateInn.isChecked = false
        cbValidateOgrn.isChecked = false
        cbNormalizePhones.isChecked = false
        cbExtractEmails.isChecked = false
        cbExtractPhones.isChecked = false
    }
}
