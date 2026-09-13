package org.syndes.terminal

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.nio.charset.Charset
import kotlin.math.max
import kotlin.math.min

class AnalyzerToolActivity : AppCompatActivity() {

    private val REQUEST_TREE = 44
    private val REQUEST_EXPORT = 45
    private var pickedTreeUri: Uri? = null
    private var pickedRoot: DocumentFile? = null
    private var exportUri: Uri? = null

    private lateinit var tvFolderStatus: TextView
    private lateinit var btnChooseFolder: Button
    private lateinit var etFileFilter: EditText
    private lateinit var btnAnalyze: Button
    private lateinit var btnCopyAll: Button
    private lateinit var btnExportFile: Button
    private lateinit var tvResultStatus: TextView
    private lateinit var llSlotsContainer: LinearLayout
    private lateinit var llResultsContainer: LinearLayout

    private data class AnalyticSlot(
        val id: Int,
        val etName: EditText,
        val etInclude: EditText,
        val etExclude: EditText,
        val cbAllWords: CheckBox,
        val cbRegex: CheckBox
    )
    private val slots = mutableListOf<AnalyticSlot>()

    // ИСПРАВЛЕНО: isMatchLine вместо isMatch, чтобы подсвечивать несколько строк блока
    private data class ContextLine(val text: String, val isMatchLine: Boolean, val matchedTerms: List<String> = emptyList())
    private data class AnalysisResult(
        val fileName: String,
        val lineNumber: Int,
        val slotName: String,
        val confidence: Int,
        val contextBlock: List<ContextLine>
    )

    private val allResults = mutableListOf<AnalysisResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_analyzer_tool)

        initViews()
        setupSlots()
        
        if (pickedRoot == null) {
            Toast.makeText(this, "Выберите папку с документами для анализа", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        tvFolderStatus = findViewById(R.id.tvFolderStatus)
        btnChooseFolder = findViewById(R.id.btnChooseFolder)
        etFileFilter = findViewById(R.id.etFileFilter)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        btnCopyAll = findViewById(R.id.btnCopyAll)
        btnExportFile = findViewById(R.id.btnExportFile)
        tvResultStatus = findViewById(R.id.tvResultStatus)
        llSlotsContainer = findViewById(R.id.llSlotsContainer)
        llResultsContainer = findViewById(R.id.llResultsContainer)

        btnChooseFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, REQUEST_TREE)
        }

        btnAnalyze.setOnClickListener { runAnalysis() }
        btnCopyAll.setOnClickListener { copyAllResults() }
        btnExportFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "analysis_report.txt")
            }
            startActivityForResult(intent, REQUEST_EXPORT)
        }
    }

    private fun setupSlots() {
        // ОБНОВЛЕНО: Информационное сообщение теперь объясняет работу с переносами и дефисами
        val infoText = TextView(this).apply {
            text = "ℹ️ Поиск нечувствителен к регистру. `Dell-15` и `Dell 15` считаются одинаковыми. " +
                   "Скрипт автоматически проверяет блоки по 3 строки, чтобы находить совпадения с переносами строк."
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 16)
        }
        llSlotsContainer.addView(infoText)

        for (i in 1..10) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(0x1100FFFF.toInt())
            }

            val titleRow = LinearLayout(this).apply { 
                orientation = LinearLayout.HORIZONTAL 
            }
            
            val title = TextView(this).apply {
                text = "Правило $i:"
                setTextColor(0xFF00FFFF.toInt())
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            title.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            
            val cbRegex = CheckBox(this).apply {
                text = "Точный Regex"
                setTextColor(0xFFFF5555.toInt())
                textSize = 12f
                setOnLongClickListener {
                    Toast.makeText(context, "Использовать регулярные выражения вместо нечеткого поиска", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            val cbAll = CheckBox(this).apply {
                text = "Искать ВСЕ слова (AND)"
                setTextColor(0xFF00FF00.toInt())
                textSize = 12f
                isChecked = true
                setOnLongClickListener {
                    Toast.makeText(context, "Если включено: блок из 3-х строк должен содержать ВСЕ слова. Если выключено: достаточно ОДНОГО.", Toast.LENGTH_LONG).show()
                    true
                }
            }
            
            titleRow.addView(title)
            titleRow.addView(cbRegex)
            titleRow.addView(cbAll)

            val etName = EditText(this).apply {
                hint = "Название правила (напр.: Dell Ноутбуки)"
                setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFF666666.toInt())
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FFFF.toInt())
            }

            val etInclude = EditText(this).apply {
                hint = "ВКЛЮЧИТЬ: слова для поиска (напр.: Ноутбук, Dell, 15)"
                setTextColor(0xFF00FF00.toInt())
                setHintTextColor(0xFF666666.toInt())
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FF00.toInt())
            }

            val etExclude = EditText(this).apply {
                hint = "ИСКЛЮЧИТЬ: слова-фильтры (напр.: б/у, ремонт)"
                setTextColor(0xFFFF5555.toInt())
                setHintTextColor(0xFF666666.toInt())
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF5555.toInt())
            }

            card.addView(titleRow)
            card.addView(etName)
            card.addView(etInclude)
            card.addView(etExclude)
            llSlotsContainer.addView(card)

            slots.add(AnalyticSlot(i, etName, etInclude, etExclude, cbAll, cbRegex))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TREE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                pickedTreeUri = uri
                pickedRoot = DocumentFile.fromTreeUri(this, uri)
                tvFolderStatus.text = "✅ Выбрано: ${pickedRoot?.name ?: uri.path}"
            }
        } else if (requestCode == REQUEST_EXPORT && resultCode == Activity.RESULT_OK) {
            exportUri = data?.data
            if (exportUri != null) {
                performFileExport()
            }
        }
    }

    private fun runAnalysis() {
        if (pickedRoot == null) {
            Toast.makeText(this, "Сначала выберите папку!", Toast.LENGTH_SHORT).show()
            return
        }

        val activeSlots = slots.filter { it.etInclude.text.toString().isNotBlank() }
        if (activeSlots.isEmpty()) {
            Toast.makeText(this, "Заполните 'ВКЛЮЧИТЬ' хотя бы в одном правиле", Toast.LENGTH_SHORT).show()
            return
        }

        val fileFilterStr = etFileFilter.text.toString().trim()
        val allowedExts = if (fileFilterStr.isNotEmpty()) {
            fileFilterStr.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        btnAnalyze.isEnabled = false
        tvResultStatus.text = "Анализ файлов... (проверка блоков по 3 строки)"
        llResultsContainer.removeAllViews()
        allResults.clear()

        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                analyzeFiles(pickedRoot!!, activeSlots, allowedExts)
            }
            
            withContext(Dispatchers.Main) {
                btnAnalyze.isEnabled = true
                allResults.addAll(results)
                tvResultStatus.text = "Найдено совпадений: ${results.size}"
                btnCopyAll.isEnabled = results.isNotEmpty()
                btnExportFile.isEnabled = results.isNotEmpty()
                displayResults(results)
            }
        }
    }

    private suspend fun analyzeFiles(
        root: DocumentFile, 
        activeSlots: List<AnalyticSlot>, 
        allowedExts: List<String>
    ): List<AnalysisResult> {
        val results = mutableListOf<AnalysisResult>()

        fun traverse(dir: DocumentFile) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    traverse(child)
                } else if (child.isFile) {
                    val name = child.name?.lowercase() ?: ""
                    if (allowedExts.isNotEmpty() && allowedExts.none { name.endsWith(it) }) {
                        continue
                    }
                    if (isTextFile(child.name, child.type)) {
                        processFileSafe(child, activeSlots, results)
                    }
                }
            }
        }
        traverse(root)
        return results
    }

    private fun processFileSafe(
        doc: DocumentFile,
        activeSlots: List<AnalyticSlot>,
        results: MutableList<AnalysisResult>
    ) {
        try {
            val input = contentResolver.openInputStream(doc.uri) ?: return
            val data = BufferedInputStream(input).use { it.readBytes() }
            
            if (data.size > 10 * 1024 * 1024) return 
            if (data.take(100).contains(0.toByte())) return

            val text = String(data, Charset.forName("UTF-8"))
            val lines = text.split("\n")

            // НОВАЯ ЛОГИКА: Отслеживаем последний найденный индекс, чтобы не дублировать совпадения в блоке
            var lastMatchedIndex = -1

            for (i in lines.indices) {
                // Если эта строка уже была частью успешно найденного блока, пропускаем её
                if (i <= lastMatchedIndex) continue

                // БЕРЕМ БЛОК ИЗ 3-Х СТРОК (предыдущая, текущая, следующая)
                val startIdx = max(0, i - 1)
                val endIdx = min(lines.size - 1, i + 1)
                val blockLines = lines.subList(startIdx, endIdx + 1)
                
                // ОБЪЕДИНЯЕМ ИХ ЧЕРЕЗ ПРОБЕЛ. Это превращает перенос строки в обычный пробел!
                val blockText = blockLines.joinToString(" ")
                val blockTextLower = blockText.lowercase()

                if (blockTextLower.trim().length < 2) continue

                for (slot in activeSlots) {
                    val includeTerms = slot.etInclude.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val excludeTerms = slot.etExclude.text.toString().split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                    
                    // 1. ПРОВЕРКА ИСКЛЮЧЕНИЙ (теперь работает и для слов на разных строках внутри блока)
                    var isExcluded = false
                    for (exTerm in excludeTerms) {
                        if (blockTextLower.contains(exTerm)) {
                            isExcluded = true
                            break
                        }
                    }
                    if (isExcluded) continue

                    // 2. ПРОВЕРКА ВКЛЮЧЕНИЙ
                    var isMatch = false
                    var confidence = 0
                    val matchedTerms = mutableListOf<String>()

                    if (slot.cbRegex.isChecked) {
                        try {
                            val combinedRegex = includeTerms.joinToString("|") { "($it)" }
                            val regex = Regex(combinedRegex, RegexOption.IGNORE_CASE)
                            
                            val matchResult = kotlinx.coroutines.runBlocking {
                                withTimeoutOrNull(100) { regex.find(blockText) }
                            }
                            
                            if (matchResult != null) {
                                isMatch = true
                                confidence = 100
                                for (group in matchResult.groupValues) {
                                    if (group.isNotEmpty() && includeTerms.any { it.equals(group, ignoreCase = true) }) {
                                        matchedTerms.add(group)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            continue
                        }
                    } else {
                        val requireAll = slot.cbAllWords.isChecked 
                        
                        if (requireAll) {
                            var allFound = true
                            for (term in includeTerms) {
                                if (!blockTextLower.contains(term.lowercase())) {
                                    allFound = false
                                    break
                                }
                            }
                            if (allFound) {
                                isMatch = true
                                confidence = 95
                                matchedTerms.addAll(includeTerms)
                            }
                        } else {
                            var maxSim = 0.0
                            for (term in includeTerms) {
                                val jaccard = calculateJaccard(blockTextLower, term.lowercase())
                                val lev = calculateLevenshteinSimilarity(blockTextLower, term.lowercase())
                                val sim = (jaccard * 0.7) + (lev * 0.3)
                                if (sim > maxSim) {
                                    maxSim = sim
                                    if (sim > 0.6) matchedTerms.add(term)
                                }
                            }
                            if (maxSim > 0.65) {
                                isMatch = true
                                confidence = (maxSim * 100).toInt()
                            }
                        }
                    }

                    if (isMatch) {
                        // Запоминаем, что до этого индекса мы уже нашли совпадение, чтобы не спамить отчет
                        lastMatchedIndex = endIdx 

                        // Формируем красивый контекст: 3 строки до блока и 3 после
                        val contextStart = max(0, startIdx - 3)
                        val contextEnd = min(lines.size - 1, endIdx + 3)
                        val contextBlock = mutableListOf<ContextLine>()
                        
                        for (j in contextStart..contextEnd) {
                            // Подсвечиваем все строки, которые вошли в совпавший блок
                            val isPartOfMatch = j in startIdx..endIdx
                            contextBlock.add(ContextLine(lines[j], isPartOfMatch, if (isPartOfMatch) matchedTerms else emptyList()))
                        }

                        results.add(AnalysisResult(
                            fileName = doc.name ?: "Unknown",
                            lineNumber = startIdx + 1, // Показываем номер первой строки блока
                            slotName = slot.etName.text.toString().ifBlank { "Правило ${slot.id}" },
                            confidence = min(confidence, 100),
                            contextBlock = contextBlock
                        ))
                        break // Переходим к следующей строке после блока
                    }
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки чтения
        }
    }

    private fun displayResults(results: List<AnalysisResult>) {
        llResultsContainer.removeAllViews()
        
        if (results.isEmpty()) {
            val tv = TextView(this).apply {
                text = "Совпадений не найдено. Попробуйте изменить фильтры или снять галочку 'Искать ВСЕ слова'."
                setTextColor(0xFFAAAAAA.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 32)
            }
            llResultsContainer.addView(tv)
            return
        }

        for (res in results) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(0x1500FFFF.toInt())
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 8, 0, 8)
                layoutParams = params
            }

            val header = TextView(this).apply {
                text = "📄 ${res.fileName} | Строки: ${res.lineNumber}+ | ${res.slotName} | Точность: ${res.confidence}%"
                setTextColor(0xFF00FFFF.toInt())
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val spannableBuilder = SpannableStringBuilder()
            
            for (ctxLine in res.contextBlock) {
                // Если строка является частью совпавшего блока, ставим маркер
                if (ctxLine.isMatchLine) {
                    spannableBuilder.append("▶ ")
                } else {
                    spannableBuilder.append("  ")
                }
                
                val lineStart = spannableBuilder.length
                spannableBuilder.append(ctxLine.text)
                spannableBuilder.append("\n")

                // Подсветка терминов только внутри строк, которые являются частью совпадения
                if (ctxLine.isMatchLine && ctxLine.matchedTerms.isNotEmpty()) {
                    val lowerText = ctxLine.text.lowercase()
                    for (term in ctxLine.matchedTerms) {
                        val lowerTerm = term.lowercase()
                        var startIndex = lowerText.indexOf(lowerTerm)
                        while (startIndex != -1) {
                            val actualStart = lineStart + startIndex
                            val actualEnd = actualStart + term.length
                            
                            spannableBuilder.setSpan(
                                BackgroundColorSpan(Color.parseColor("#FFFF00")),
                                actualStart, actualEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            spannableBuilder.setSpan(
                                ForegroundColorSpan(Color.parseColor("#000000")),
                                actualStart, actualEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            
                            startIndex = lowerText.indexOf(lowerTerm, startIndex + 1)
                        }
                    }
                }
            }

            val tvContext = TextView(this).apply {
                text = spannableBuilder
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextIsSelectable(true)
                setPadding(12, 12, 12, 12)
                setBackgroundColor(0x00000000)
            }

            val btnCopyBlock = Button(this).apply {
                text = "📋 Копировать блок"
                textSize = 12f
                setTextColor(0xFF0A0A0A.toInt())
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FFFF.toInt())
                setPadding(0, 8, 0, 8)
                setOnClickListener {
                    copyToClipboard(spannableBuilder.toString().replace("▶ ", "").trim())
                }
            }

            card.addView(header)
            card.addView(tvContext)
            card.addView(btnCopyBlock)
            llResultsContainer.addView(card)
        }
    }

    private fun performFileExport() {
        if (allResults.isEmpty() || exportUri == null) return
        
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val fullText = buildString {
                        appendLine("=== ОТЧЕТ АНАЛИТИЧЕСКОГО СКАНЕРА PRO ===")
                        appendLine("Дата: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                        appendLine("Всего совпадений: ${allResults.size}")
                        appendLine("========================================\n")
                        
                        for (res in allResults) {
                            appendLine("ФАЙЛ: ${res.fileName} | Строки: ${res.lineNumber}+ | Правило: ${res.slotName} (${res.confidence}%)")
                            appendLine("---")
                            for (ctxLine in res.contextBlock) {
                                if (ctxLine.isMatchLine) {
                                    appendLine("▶ ${ctxLine.text}")
                                } else {
                                    appendLine("  ${ctxLine.text}")
                                }
                            }
                            appendLine("\n")
                        }
                    }
                    
                    contentResolver.openOutputStream(exportUri!!, "wt")?.use { outputStream ->
                        outputStream.write(fullText.toByteArray(Charsets.UTF_8))
                    }
                }
                
                Toast.makeText(this@AnalyzerToolActivity, "✅ Отчет успешно сохранен!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@AnalyzerToolActivity, "❌ Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Анализ", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Скопировано в буфер обмена", Toast.LENGTH_SHORT).show()
    }

    private fun copyAllResults() {
        if (allResults.isEmpty()) return
        val textToCopy = buildString {
            for (res in allResults) {
                appendLine("${res.fileName}:${res.lineNumber}+ | ${res.slotName} | ${res.confidence}%")
                for (ctxLine in res.contextBlock) {
                    appendLine(if (ctxLine.isMatchLine) "▶ ${ctxLine.text}" else "  ${ctxLine.text}")
                }
                appendLine()
            }
        }
        copyToClipboard(textToCopy.trimEnd())
        Toast.makeText(this, "Весь отчет скопирован!", Toast.LENGTH_LONG).show()
    }

    private fun calculateJaccard(s1: String, s2: String): Double {
        val maxWords = 100
        val set1 = s1.split(Regex("\\W+")).filter { it.isNotEmpty() }.take(maxWords).toSet()
        val set2 = s2.split(Regex("\\W+")).filter { it.isNotEmpty() }.take(maxWords).toSet()
        if (set1.isEmpty() && set2.isEmpty()) return 1.0
        if (set1.isEmpty() || set2.isEmpty()) return 0.0
        return set1.intersect(set2).size.toDouble() / set1.union(set2).size.toDouble()
    }

    private fun calculateLevenshteinSimilarity(s1: String, s2: String): Double {
        val maxAllowedLength = 500
        val s1Truncated = if (s1.length > maxAllowedLength) s1.take(maxAllowedLength) else s1
        val s2Truncated = if (s2.length > maxAllowedLength) s2.take(maxAllowedLength) else s2
        
        val distance = levenshteinDistance(s1Truncated, s2Truncated)
        val maxLen = max(s1Truncated.length, s2Truncated.length).toDouble()
        if (maxLen == 0.0) return 1.0
        return 1.0 - (distance / maxLen)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(dp[i - 1][j] + 1, min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost))
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun isTextFile(name: String?, mime: String?): Boolean {
        if (name == null) return false
        val lowerName = name.lowercase()
        val textExt = listOf(".txt", ".md", ".xml", ".xhtml", ".html", ".htm", ".csv", 
                             ".json", ".kt", ".java", ".syd", ".ft", ".fst", ".log", ".ini", ".cfg")
        if (textExt.any { lowerName.endsWith(it) }) return true
        if (mime != null) {
            val lowerMime = mime.lowercase()
            if (lowerMime.startsWith("text/") || lowerMime.contains("xml") || lowerMime.contains("json")) return true
        }
        return false
    }
}
