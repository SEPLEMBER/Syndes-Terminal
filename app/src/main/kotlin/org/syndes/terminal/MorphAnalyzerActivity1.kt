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
import java.io.BufferedInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

class MorphAnalyzerActivity1 : AppCompatActivity() {

    private val REQUEST_TREE = 46
    private val REQUEST_EXPORT = 47
    private var pickedTreeUri: Uri? = null
    private var pickedRoot: DocumentFile? = null
    private var exportUri: Uri? = null

    private lateinit var tvFolderStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnChooseFolder: Button
    private lateinit var etFileFilter: EditText
    private lateinit var btnAnalyze: Button
    private lateinit var btnCopyAll: Button
    private lateinit var btnExportFile: Button
    private lateinit var tvResultStatus: TextView
    private lateinit var llSlotsContainer: LinearLayout
    private lateinit var llResultsContainer: LinearLayout

    private data class MorphSlot(
        val id: Int,
        val etWord: EditText,
        val etStem: EditText,
        val cbExact: CheckBox
    )
    private val slots = mutableListOf<MorphSlot>()

    private data class ContextParagraph(val text: String, val isMatchBlock: Boolean, val matchedWords: List<String> = emptyList())
    private data class MorphResult(
        val fileName: String,
        val blockIndex: Int,
        val originalWord: String,
        val stem: String,
        val contextBlock: List<ContextParagraph>
    )

    private val allResults = mutableListOf<MorphResult>()

    // Максимальное количество результатов для отрисовки в UI (защита от ANR)
    private val MAX_UI_RESULTS = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_morph_analyzer)

        initViews()
        setupSlots()
        
        if (pickedRoot == null) {
            Toast.makeText(this, "Выберите папку с документами", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        tvFolderStatus = findViewById(R.id.tvFolderStatus)
        progressBar = findViewById(R.id.progressBar)
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
                putExtra(Intent.EXTRA_TITLE, "morph_report.txt")
            }
            startActivityForResult(intent, REQUEST_EXPORT)
        }
    }

    private fun setupSlots() {
        val infoText = TextView(this).apply {
            text = "ℹ️ Введите слово. Система предложит корень, но вы можете исправить его вручную. 'Ё' = 'Е'."
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
                gravity = Gravity.CENTER_VERTICAL
            }

            val title = TextView(this).apply {
                text = "Слот $i:"
                setTextColor(0xFF00FFFF.toInt())
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val cbExact = CheckBox(this).apply {
                text = "Точное совпадение"
                setTextColor(0xFFFFBF00.toInt())
                textSize = 12f
                setOnCheckedChangeListener { _, isChecked ->
                    val slot = slots.find { it.id == i }
                    if (isChecked) {
                        slot?.etStem?.setText("строгий поиск")
                        slot?.etStem?.setTextColor(0xFFFFBF00.toInt())
                        slot?.etStem?.isEnabled = false
                    } else {
                        slot?.etStem?.isEnabled = true
                        slot?.let { updateStemDisplay(it.id, it.etWord.text.toString()) }
                    }
                }
            }

            titleRow.addView(title)
            titleRow.addView(cbExact)

            val etWord = EditText(this).apply {
                hint = "Слово (напр.: Существовать)"
                setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFF666666.toInt())
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FFFF.toInt())
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus && text.isNotEmpty()) {
                        val slot = slots.find { it.id == i }
                        if (slot?.cbExact?.isChecked == false) {
                            updateStemDisplay(i, text.toString())
                        }
                    }
                }
            }

            val etStem = EditText(this).apply {
                hint = "Корень (можно изменить вручную)"
                setTextColor(0xFF00FF00.toInt())
                setHintTextColor(0xFF666666.toInt())
                textSize = 13f
                typeface = android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.ITALIC)
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF008B8B.toInt())
                setPadding(0, 4, 0, 4)
            }

            card.addView(titleRow)
            card.addView(etWord)
            card.addView(etStem)
            llSlotsContainer.addView(card)

            slots.add(MorphSlot(i, etWord, etStem, cbExact))
        }
    }

    private fun updateStemDisplay(id: Int, word: String) {
        val stem = getRussianStem(word)
        val slot = slots.find { it.id == id }
        slot?.etStem?.setText(stem)
        slot?.etStem?.setTextColor(0xFF00FF00.toInt())
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

        val activeSlots = slots.filter { 
            it.etWord.text.toString().trim().length >= 2 && 
            it.etStem.text.toString().trim().length >= 2 
        }
        
        if (activeSlots.isEmpty()) {
            Toast.makeText(this, "Введите слово и убедитесь, что корень указан (мин. 2 символа)", Toast.LENGTH_SHORT).show()
            return
        }

        val fileFilterStr = etFileFilter.text.toString().trim()
        val allowedExts = if (fileFilterStr.isNotEmpty()) {
            fileFilterStr.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        btnAnalyze.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvResultStatus.text = "Подготовка к анализу..."
        llResultsContainer.removeAllViews()
        allResults.clear()

        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                analyzeFiles(pickedRoot!!, activeSlots, allowedExts)
            }
            
            withContext(Dispatchers.Main) {
                btnAnalyze.isEnabled = true
                progressBar.visibility = View.GONE
                allResults.addAll(results)
                
                val uniqueFiles = results.map { it.fileName }.toSet().size
                tvResultStatus.text = "✅ Найдено ${results.size} контекстов в $uniqueFiles файлах"
                
                btnCopyAll.isEnabled = results.isNotEmpty()
                btnExportFile.isEnabled = results.isNotEmpty()
                displayResults(results)
            }
        }
    }

    private suspend fun analyzeFiles(
        root: DocumentFile, 
        activeSlots: List<MorphSlot>, 
        allowedExts: List<String>
    ): List<MorphResult> {
        val results = mutableListOf<MorphResult>()
        var processedFiles = 0

        suspend fun traverse(dir: DocumentFile) {
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
                        processedFiles++
                        
                        if (processedFiles % 5 == 0) {
                            withContext(Dispatchers.Main) {
                                tvResultStatus.text = "Обработка файлов... (проверено: $processedFiles)"
                            }
                        }
                    }
                }
            }
        }
        traverse(root)
        return results
    }

    private fun processFileSafe(
        doc: DocumentFile,
        activeSlots: List<MorphSlot>,
        results: MutableList<MorphResult>
    ) {
        try {
            val input = contentResolver.openInputStream(doc.uri) ?: return
            val data = BufferedInputStream(input).use { it.readBytes() }
            
            if (data.size > 10 * 1024 * 1024) return 
            if (data.take(100).contains(0.toByte())) return

            val text = try {
                String(data, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                String(data, Charset.defaultCharset())
            }

            val paragraphs = text.split(Regex("\n\\s*\n|\\n---\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            val seenBlocks = mutableSetOf<String>()

            for ((index, para) in paragraphs.withIndex()) {
                val cleanPara = para.replace("\n", " ").trim()
                val cleanParaLower = normalize(cleanPara)

                if (cleanParaLower.length < 2) continue

                for (slot in activeSlots) {
                    val originalWord = slot.etWord.text.toString().trim()
                    
                    // ЗАЩИТА: Удаляем все пробелы из корня, чтобы избежать ошибок в Regex
                    val userStem = normalize(slot.etStem.text.toString().trim()).replace(Regex("\\s+"), "")
                    
                    // Формируем паттерн. Заменяем 'е' на '[её]' для более гибкого поиска, так как normalize() заменяет 'ё' на 'е'.
                    val searchPattern = if (slot.cbExact.isChecked) {
                        val escapedWord = Regex.escape(normalize(originalWord)).replace("е", "[её]")
                        "\\b$escapedWord\\b"
                    } else {
                        if (userStem.length < 2) continue
                        val escapedStem = Regex.escape(userStem).replace("е", "[её]")
                        "\\b[а-яё]*$escapedStem[а-яё]*\\b"
                    }

                    // ВАЖНО: Используем ТОЛЬКО IGNORE_CASE, точно как в рабочей версии MorphAnalyzerActivity.
                    // Флаги UNICODE_CHARACTER_CLASS или (?U) на Android ломают работу \b с кириллицей!
                    val regex = try { 
                        Regex(searchPattern, RegexOption.IGNORE_CASE) 
                    } catch (e: Exception) { 
                        continue 
                    }
                    
                    val matches = regex.findAll(cleanPara).map { it.value }.distinct().toList()

                    if (matches.isNotEmpty()) {
                        val normalizedText = cleanParaLower.replace(Regex("\\s+"), " ")
                        val uniqueKey = "$normalizedText|${matches.sorted().joinToString(",")}"
                        
                        if (seenBlocks.contains(uniqueKey)) continue
                        seenBlocks.add(uniqueKey)

                        val contextStart = max(0, index - 1)
                        val contextEnd = min(paragraphs.size - 1, index + 1)
                        val contextBlock = mutableListOf<ContextParagraph>()
                        
                        for (j in contextStart..contextEnd) {
                            val isMatchBlock = (j == index)
                            contextBlock.add(ContextParagraph(paragraphs[j], isMatchBlock, if (isMatchBlock) matches else emptyList()))
                        }

                        results.add(MorphResult(
                            fileName = doc.name ?: "Unknown",
                            blockIndex = index + 1,
                            originalWord = originalWord,
                            stem = userStem,
                            contextBlock = contextBlock
                        ))
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки чтения отдельных файлов
        }
    }

    private fun normalize(text: String): String {
        return text.lowercase().replace('ё', 'е').trim()
    }

    private fun getRussianStem(word: String): String {
        var w = normalize(word)
        if (w.length < 2) return w

        val suffixes = listOf(
            "овать", "евать", "ивать", "ывать",
            "ившись", "ывшись", "ующий", "яющий", "авший", "явший", "аемый", "имый",
            "ивш", "ывш", "ующ", "яющ", "авш", "явш", "вши", "ясь",
            "енный", "анный", "янный", "енн", "анн", "янн",
            "уйте", "емте", "ешь", "ите", "ет", "ат", "ят",
            "ем", "им", "ут", "ют", "ть", "чь", "ете",
            "ость", "ство", "ение", "ание", "овь", "евь", "ник", "чик",
            "ый", "ий", "ой", "ая", "яя", "ое", "ее", "ые", "ие",
            "ов", "ев", "ам", "ям", "ах", "ях", "ей", "ою", "ею",
            "ую", "юю", "ого", "его", "ому", "ему", "ами", "ями",
            "а", "я", "о", "е", "у", "ю", "ь"
        )

        for (suffix in suffixes) {
            if (w.endsWith(suffix) && w.length - suffix.length >= 2) {
                w = w.substring(0, w.length - suffix.length)
                break
            }
        }

        if (w.endsWith("ь") && w.length > 3) {
            w = w.substring(0, w.length - 1)
        }
        
        return w
    }

    private fun displayResults(results: List<MorphResult>) {
        llResultsContainer.removeAllViews()
        
        if (results.isEmpty()) {
            val tv = TextView(this).apply {
                text = "Совпадений не найдено. Попробуйте изменить корень вручную (сделать его короче или длиннее)."
                setTextColor(0xFFAAAAAA.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 32)
            }
            llResultsContainer.addView(tv)
            return
        }

        // ЗАЩИТА ОТ ANR: Отрисовываем только первые MAX_UI_RESULTS, чтобы интерфейс не завис
        val displayList = if (results.size > MAX_UI_RESULTS) {
            Toast.makeText(this, "Показаны первые $MAX_UI_RESULTS из ${results.size} совпадений. Полный список доступен в экспорте.", Toast.LENGTH_LONG).show()
            results.take(MAX_UI_RESULTS)
        } else {
            results
        }

        for (res in displayList) {
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

            val modeText = if (res.originalWord == res.stem) "(точное)" else "(по корню: ${res.stem})"
            val header = TextView(this).apply {
                text = "📄 ${res.fileName} | Абзац ~${res.blockIndex}\n🔎 Слово: «${res.originalWord}» $modeText"
                setTextColor(0xFF00FFFF.toInt())
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val spannableBuilder = SpannableStringBuilder()
            
            for (ctxPara in res.contextBlock) {
                if (ctxPara.isMatchBlock) {
                    spannableBuilder.append("▶ ")
                } else {
                    spannableBuilder.append("  ")
                }
                
                val paraStart = spannableBuilder.length
                spannableBuilder.append(ctxPara.text)
                spannableBuilder.append("\n\n")

                if (ctxPara.isMatchBlock && ctxPara.matchedWords.isNotEmpty()) {
                    val lowerText = normalize(ctxPara.text)
                    for (matchedWord in ctxPara.matchedWords) {
                        val lowerMatch = normalize(matchedWord)
                        var startIndex = lowerText.indexOf(lowerMatch)
                        while (startIndex != -1) {
                            val actualStart = paraStart + startIndex
                            val actualEnd = actualStart + matchedWord.length
                            
                            spannableBuilder.setSpan(
                                BackgroundColorSpan(Color.parseColor("#FFFF00")),
                                actualStart, actualEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            spannableBuilder.setSpan(
                                ForegroundColorSpan(Color.parseColor("#000000")),
                                actualStart, actualEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            
                            startIndex = lowerText.indexOf(lowerMatch, startIndex + 1)
                        }
                    }
                }
            }

            val tvContext = TextView(this).apply {
                text = spannableBuilder
                textSize = 14f
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
                        appendLine("=== ОТЧЕТ МОРФОЛОГИЧЕСКОГО АНАЛИЗА PRO ===")
                        appendLine("Дата: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                        appendLine("Всего уникальных контекстов: ${allResults.size}")
                        appendLine("========================================\n")
                        
                        for (res in allResults) { // Экспортируем ВСЕ результаты, без лимита
                            appendLine("ФАЙЛ: ${res.fileName} | Абзац: ${res.blockIndex}")
                            appendLine("СЛОВО: «${res.originalWord}» | ИСПОЛЬЗОВАН КОРЕНЬ: «${res.stem}»")
                            appendLine("---")
                            for (ctxPara in res.contextBlock) {
                                if (ctxPara.isMatchBlock) {
                                    appendLine("▶ ${ctxPara.text}")
                                } else {
                                    appendLine("  ${ctxPara.text}")
                                }
                            }
                            appendLine("\n")
                        }
                    }
                    
                    contentResolver.openOutputStream(exportUri!!, "wt")?.use { outputStream ->
                        outputStream.write(fullText.toByteArray(StandardCharsets.UTF_8))
                    }
                }
                Toast.makeText(this@MorphAnalyzerActivity1, "✅ Отчет успешно сохранен!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MorphAnalyzerActivity1, "❌ Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("МорфоАнализ", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Скопировано в буфер обмена", Toast.LENGTH_SHORT).show()
    }

    private fun copyAllResults() {
        if (allResults.isEmpty()) return
        val textToCopy = buildString {
            for (res in allResults) { // Копируем ВСЕ результаты
                appendLine("${res.fileName} (Абзац ${res.blockIndex}) | «${res.originalWord}» → корень «${res.stem}»")
                for (ctxPara in res.contextBlock) {
                    appendLine(if (ctxPara.isMatchBlock) "▶ ${ctxPara.text}" else "  ${ctxPara.text}")
                }
                appendLine()
            }
        }
        copyToClipboard(textToCopy.trimEnd())
        Toast.makeText(this, "Весь отчет скопирован!", Toast.LENGTH_LONG).show()
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
