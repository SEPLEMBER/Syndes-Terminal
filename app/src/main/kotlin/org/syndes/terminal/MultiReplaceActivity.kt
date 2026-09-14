package org.syndes.terminal

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class MultiReplaceActivity : AppCompatActivity() {

    private val REQUEST_TREE = 48
    private var pickedTreeUri: android.net.Uri? = null
    private var pickedRoot: DocumentFile? = null
    private var currentJob: Job? = null

    private lateinit var tvFolderStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnChooseFolder: Button
    private lateinit var etFileFilter: EditText
    private lateinit var cbRecursive: CheckBox
    private lateinit var cbIgnoreCase: CheckBox
    private lateinit var cbUseRegex: CheckBox
    private lateinit var btnStart: Button
    private lateinit var btnCancel: Button
    private lateinit var btnCopyReport: Button
    private lateinit var tvResultStatus: TextView
    private lateinit var llSlotsContainer: LinearLayout

    private data class ReplaceSlot(
        val id: Int,
        val etFind: EditText,
        val etReplace: EditText,
        val matchCount: AtomicInteger = AtomicInteger(0)
    )
    private val slots = mutableListOf<ReplaceSlot>()

    private const val MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024L 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_multi_replace)

        initViews()
        setupSlots()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        for (slot in slots) {
            outState.putString("find_${slot.id}", slot.etFind.text.toString())
            outState.putString("replace_${slot.id}", slot.etReplace.text.toString())
        }
        outState.putBoolean("recursive", cbRecursive.isChecked)
        outState.putBoolean("ignore_case", cbIgnoreCase.isChecked)
        outState.putBoolean("use_regex", cbUseRegex.isChecked)
        outState.putString("filter", etFileFilter.text.toString())
        pickedTreeUri?.let { outState.putString("tree_uri", it.toString()) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        for (slot in slots) {
            slot.etFind.setText(savedInstanceState.getString("find_${slot.id}", ""))
            slot.etReplace.setText(savedInstanceState.getString("replace_${slot.id}", ""))
        }
        cbRecursive.isChecked = savedInstanceState.getBoolean("recursive", false)
        cbIgnoreCase.isChecked = savedInstanceState.getBoolean("ignore_case", false)
        cbUseRegex.isChecked = savedInstanceState.getBoolean("use_regex", false)
        etFileFilter.setText(savedInstanceState.getString("filter", ""))
        
        savedInstanceState.getString("tree_uri")?.let { uriString ->
            val uri = android.net.Uri.parse(uriString)
            pickedTreeUri = uri
            pickedRoot = DocumentFile.fromTreeUri(this, uri)
            tvFolderStatus.text = "✅ Восстановлено: ${pickedRoot?.name ?: uri.path}"
        }
    }

    private fun initViews() {
        tvFolderStatus = findViewById(R.id.tvFolderStatus)
        progressBar = findViewById(R.id.progressBar)
        btnChooseFolder = findViewById(R.id.btnChooseFolder)
        etFileFilter = findViewById(R.id.etFileFilter)
        cbRecursive = findViewById(R.id.cbRecursive)
        cbIgnoreCase = findViewById(R.id.cbIgnoreCase)
        cbUseRegex = findViewById(R.id.cbUseRegex)
        btnStart = findViewById(R.id.btnStart)
        btnCancel = findViewById(R.id.btnCancel)
        btnCopyReport = findViewById(R.id.btnCopyReport)
        tvResultStatus = findViewById(R.id.tvResultStatus)
        llSlotsContainer = findViewById(R.id.llSlotsContainer)

        btnChooseFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, REQUEST_TREE)
        }

        btnStart.setOnClickListener { startOrCancelProcess() }
        btnCancel.setOnClickListener { currentJob?.cancel() }
        btnCopyReport.setOnClickListener { copyReportToClipboard() }
    }

    private fun setupSlots() {
        for (i in 1..10) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(0x1100FFFF.toInt())
            }

            val title = TextView(this).apply {
                text = "Слот $i:"
                setTextColor(0xFF00FFFF.toInt())
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val etFind = EditText(this).apply {
                hint = "Найти (текст или Regex)"
                setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFF666666.toInt())
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FFFF.toInt())
                gravity = Gravity.TOP or Gravity.START
                minLines = 2
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }

            val etReplace = EditText(this).apply {
                hint = "Заменить на..."
                setTextColor(0xFF00FF00.toInt())
                setHintTextColor(0xFF666666.toInt())
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF008B8B.toInt())
                gravity = Gravity.TOP or Gravity.START
                minLines = 2
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }

            card.addView(title)
            card.addView(etFind)
            card.addView(etReplace)
            llSlotsContainer.addView(card)

            slots.add(ReplaceSlot(i, etFind, etReplace))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TREE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                pickedTreeUri = uri
                pickedRoot = DocumentFile.fromTreeUri(this, uri)
                tvFolderStatus.text = "✅ Выбрано: ${pickedRoot?.name ?: uri.path}"
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, 
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    Toast.makeText(this, "⚠️ Нет прав на запись в эту папку", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startOrCancelProcess() {
        if (currentJob?.isActive == true) {
            currentJob?.cancel()
            return
        }

        if (pickedRoot == null) {
            Toast.makeText(this, "Сначала выберите папку!", Toast.LENGTH_SHORT).show()
            return
        }

        val activeSlots = slots.filter { it.etFind.text.toString().isNotEmpty() }
        if (activeSlots.isEmpty()) {
            Toast.makeText(this, "Заполните хотя бы одно поле 'Найти'", Toast.LENGTH_SHORT).show()
            return
        }

        activeSlots.forEach { it.matchCount.set(0) }

        btnStart.text = "⏸ Пауза"
        btnCancel.visibility = View.VISIBLE
        btnCopyReport.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvResultStatus.text = "Подготовка..."

        val isRecursive = cbRecursive.isChecked
        val isIgnoreCase = cbIgnoreCase.isChecked
        val isRegex = cbUseRegex.isChecked
        val fileFilterStr = etFileFilter.text.toString().trim()
        val allowedExts = if (fileFilterStr.isNotEmpty()) {
            fileFilterStr.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        currentJob = lifecycleScope.launch {
            var processedCount = 0
            var modifiedCount = 0
            var skippedCount = 0
            var errorCount = 0

            try {
                withContext(Dispatchers.IO) {
                    fun traverse(dir: DocumentFile) {
                        kotlinx.coroutines.ensureActive()
                        val children = dir.listFiles() ?: return
                        for (child in children) {
                            kotlinx.coroutines.ensureActive()

                            if (child.isDirectory && isRecursive) {
                                traverse(child)
                            } else if (child.isFile) {
                                val name = child.name?.lowercase() ?: ""
                                if (allowedExts.isNotEmpty() && allowedExts.none { name.endsWith(it) }) continue
                                if (!isTextFile(child.name, child.type)) continue
                                
                                if (child.length() > MAX_FILE_SIZE_BYTES) {
                                    skippedCount++
                                    continue
                                }

                                val result = processFileSafe(child, activeSlots, isIgnoreCase, isRegex)
                                if (result.modified) modifiedCount++
                                if (result.error) errorCount++
                                processedCount++

                                if (processedCount % 10 == 0) {
                                    withContext(Dispatchers.Main) {
                                        tvResultStatus.text = "Обработка... (проверено: $processedCount, изменено: $modifiedCount)"
                                    }
                                }
                            }
                        }
                    }
                    traverse(pickedRoot!!)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        tvResultStatus.text = "⚠️ Операция отменена пользователем."
                        Toast.makeText(this@MultiReplaceActivity, "Отменено", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    btnStart.text = "🚀 Старт"
                    btnCancel.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    btnCopyReport.isEnabled = true

                    val skipMsg = if (skippedCount > 0) " | Пропущено: $skippedCount (>15МБ)" else ""
                    val errMsg = if (errorCount > 0) " | Ошибок: $errorCount" else ""
                    tvResultStatus.text = "✅ Готово! Обработано: $processedCount | Изменено: $modifiedCount$skipMsg$errMsg"
                }
            }
        }
    }

    private data class ProcessResult(val modified: Boolean, val error: Boolean)

    private fun processFileSafe(
        doc: DocumentFile,
        activeSlots: List<ReplaceSlot>,
        isIgnoreCase: Boolean,
        isRegex: Boolean
    ): ProcessResult {
        // ЗАЩИТА #1: Ловим Throwable, чтобы предотвратить краш всего приложения 
        // из-за OutOfMemoryError, если SAF вернул неверный размер файла.
        return try {
            val input = contentResolver.openInputStream(doc.uri) ?: return ProcessResult(false, true)
            val data = input.use { it.readBytes() }
            
            val checkLimit = minOf(data.size, 1024)
            var isBinary = false
            for (i in 0 until checkLimit) {
                if (data[i] == 0.toByte()) {
                    isBinary = true
                    break
                }
            }
            if (isBinary) return ProcessResult(false, false)

            // ЗАЩИТА #2: Определяем кодировку, чтобы записать файл обратно в ней же.
            var usedCharset: Charset = StandardCharsets.UTF_8
            var text = try {
                String(data, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                usedCharset = Charset.defaultCharset()
                String(data, usedCharset)
            }

            var hasChanges = false

            for (slot in activeSlots) {
                val findText = slot.etFind.text.toString()
                val replaceText = slot.etReplace.text.toString()
                if (findText.isEmpty()) continue

                val regexOptions = mutableSetOf<RegexOption>()
                if (isIgnoreCase) regexOptions.add(RegexOption.IGNORE_CASE)
                if (!isRegex) regexOptions.add(RegexOption.LITERAL)

                val regex = try {
                    Regex(findText, regexOptions)
                } catch (e: Exception) {
                    continue 
                }

                val matches = regex.findAll(text).count()
                if (matches > 0) {
                    val safeReplacement = java.util.regex.Matcher.quoteReplacement(replaceText)
                    text = regex.replace(text, safeReplacement)
                    slot.matchCount.addAndGet(matches)
                    hasChanges = true
                }
            }

            if (hasChanges) {
                kotlinx.coroutines.ensureActive() 
                
                val output = contentResolver.openOutputStream(doc.uri, "w")
                output?.use {
                    // Записываем в той же кодировке, в которой читали
                    it.write(text.toByteArray(usedCharset))
                    it.flush() 
                } ?: return ProcessResult(false, true)
            }

            ProcessResult(hasChanges, false)
        } catch (e: Throwable) { // Ловим всё, включая OOM
            ProcessResult(false, true)
        }
    }

    private fun copyReportToClipboard() {
        val activeSlots = slots.filter { it.etFind.text.toString().isNotEmpty() }
        if (activeSlots.isEmpty()) return

        val report = buildString {
            appendLine("=== ОТЧЕТ МУЛЬТИ-ЗАМЕНЫ ===")
            appendLine("Дата: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("Папка: ${pickedRoot?.name ?: "N/A"}")
            appendLine("Рекурсивно: ${cbRecursive.isChecked} | Без регистра: ${cbIgnoreCase.isChecked} | Regex: ${cbUseRegex.isChecked}")
            appendLine("===========================")
            
            for (slot in activeSlots) {
                val findPreview = slot.etFind.text.toString().take(30).replace("\n", "\\n")
                val replacePreview = slot.etReplace.text.toString().take(30).replace("\n", "\\n")
                appendLine("Слот ${slot.id}:")
                appendLine("  Найти: «$findPreview${if (slot.etFind.text.length > 30) "..." else ""}»")
                appendLine("  Заменить: «$replacePreview${if (slot.etReplace.text.length > 30) "..." else ""}»")
                appendLine("  Срабатываний: ${slot.matchCount.get()}")
                appendLine()
            }
            appendLine("===========================")
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MultiReplace Report", report)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "✅ Отчет скопирован в буфер", Toast.LENGTH_SHORT).show()
    }

    private fun isTextFile(name: String?, mime: String?): Boolean {
        if (name == null) return false
        val lowerName = name.lowercase()
        val textExt = listOf(".txt", ".md", ".xml", ".xhtml", ".html", ".htm", ".csv", 
                             ".json", ".kt", ".java", ".syd", ".ft", ".fst", ".log", ".ini", ".cfg", ".yaml", ".yml")
        if (textExt.any { lowerName.endsWith(it) }) return true
        if (mime != null) {
            val lowerMime = mime.lowercase()
            if (lowerMime.startsWith("text/") || lowerMime.contains("xml") || lowerMime.contains("json")) return true
        }
        return false
    }
}
