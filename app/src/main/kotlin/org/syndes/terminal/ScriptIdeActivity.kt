package org.syndes.terminal

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile

/**
 * NeonPad (ScriptIdeActivity) — легковесный IDE-редактор для скриптов оболочки.
 * Темная неоновая тема, поиск, автодополнение.
 */
class ScriptIdeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_NAME = "FILE_NAME"
        const val EXTRA_FILE_URI = "FILE_URI"

        // Цветовая схема: Dark Neon
        val BG_DARK = Color.parseColor("#0A0A0A")
        val BG_PANEL = Color.parseColor("#111111")
        val NEON_PURPLE = Color.parseColor("#B026FF")
        val NEON_PURPLE_DIM = Color.parseColor("#33B026FF") // Полупрозрачный для фона неактивных совпадений
        val TEXT_LIGHT = Color.parseColor("#E0E0E0")
        val TEXT_DIM = Color.parseColor("#888888")

        // Список команд для автодополнения (можно расширять)
        val COMMANDS = listOf(
            "echo", "sleep", "cat", "ls", "cd", "mkdir", "rm", "cp", "mv", "grep", "find", 
            "pm", "edit", "tree", "seq", "base64", "xxd", "strings", "printf", "alias", 
            "runsyd", "wifi", "bts", "backup", "checksum", "diff", "head", "tail", "wc"
        )
    }

    private lateinit var editor: EditText
    private lateinit var titleView: TextView
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    
    private var fileUri: Uri? = null
    private var fileName: String = "untitled.txt"
    private var isModified = false
    private var originalText: String = ""

    // Переменные для Поиска
    private var searchMatches = listOf<Int>()
    private var currentMatchIndex = -1
    private var searchQuery = ""

    // Переменные для Автодополнения
    private lateinit var autocompletePopup: PopupWindow
    private lateinit var suggestionsLayout: LinearLayout
    private var isUpdatingText = false // Флаг для предотвращения рекурсии в TextWatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "untitled.txt"
        fileUri = intent.getParcelableExtra(EXTRA_FILE_URI)

        buildUI()
        initAutocomplete()

        if (fileUri != null) loadFileContent()
        else editor.setText(getTemplateForExtension(getExtension(fileName)))

        setupTextWatcher()
        setupBackPress()
    }

    // =====================================================================
    // ПОСТРОЕНИЕ UI
    // =====================================================================

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_DARK)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        // 1. Заголовок
        titleView = TextView(this).apply {
            text = "⚡ NeonPad: $fileName"
            setTextColor(NEON_PURPLE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(32, 24, 32, 24)
            setBackgroundColor(BG_PANEL)
        }
        root.addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 2. Панель поиска (Скрыта по умолчанию)
        searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(BG_PANEL)
            setPadding(16, 8, 16, 8)
            visibility = View.GONE
        }
        
        searchInput = EditText(this).apply {
            hint = "Поиск..."
            setHintTextColor(TEXT_DIM)
            setTextColor(TEXT_LIGHT)
            setBackgroundColor(BG_DARK)
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { performSearch(s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        searchBar.addView(searchInput)
        searchBar.addView(createSmallNeonButton("◀", { navigateMatch(-1) }))
        searchBar.addView(createSmallNeonButton("▶", { navigateMatch(1) }))
        searchBar.addView(createSmallNeonButton("✕", { toggleSearch() }))
        
        root.addView(searchBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 3. Редактор
        editor = EditText(this).apply {
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            setBackgroundColor(BG_DARK)
            setTextColor(TEXT_LIGHT)
            setHintTextColor(TEXT_DIM)
            highlightColor = NEON_PURPLE
            hint = "Начните писать код..."
            setPadding(32, 32, 32, 32)
            background = null
            inputType = inputType or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS // Отключаем системные подсказки
        }
        root.addView(editor, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // 4. Нижняя панель
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(BG_PANEL)
            setPadding(16, 16, 16, 16)
        }

        bottomBar.addView(createNeonButton("🔍 FIND", ::toggleSearch))
        bottomBar.addView(createSpacer())
        bottomBar.addView(createNeonButton("💾 SAVE", ::saveFile))
        bottomBar.addView(createSpacer())
        bottomBar.addView(createNeonButton("❌ EXIT", ::attemptExit))

        root.addView(bottomBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }

    private fun createNeonButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.BLACK)
            setBackgroundColor(NEON_PURPLE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(32, 16, 32, 16)
            setOnClickListener { onClick() }
            stateListAnimator = null
        }
    }

    private fun createSmallNeonButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(NEON_PURPLE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 16f
            setPadding(16, 8, 16, 8)
            setOnClickListener { onClick() }
            stateListAnimator = null
        }
    }

    private fun createSpacer(): Space {
        return Space(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 0.5f) }
    }

    // =====================================================================
    // ЛОГИКА ПОИСКА
    // =====================================================================

    private fun toggleSearch() {
        if (searchBar.visibility == View.GONE) {
            searchBar.visibility = View.VISIBLE
            searchInput.requestFocus()
        } else {
            searchBar.visibility = View.GONE
            clearSearchHighlights()
            searchInput.text.clear()
        }
    }

    private fun performSearch(query: String) {
        clearSearchHighlights()
        searchQuery = query
        if (query.isEmpty()) return

        searchMatches = findAllOccurrences(editor.text.toString(), query)
        currentMatchIndex = if (searchMatches.isNotEmpty()) 0 else -1
        applySearchHighlights()
    }

    private fun navigateMatch(direction: Int) {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + direction + searchMatches.size) % searchMatches.size
        applySearchHighlights()
    }

    private fun applySearchHighlights() {
        if (searchQuery.isEmpty() || searchMatches.isEmpty()) return
        
        // Подсвечиваем все найденные варианты бледным фиолетовым
        for (idx in searchMatches) {
            editor.text.setSpan(BackgroundColorSpan(NEON_PURPLE_DIM), idx, idx + searchQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Текущий вариант выделяем ярко и инвертируем текст
        if (currentMatchIndex >= 0 && currentMatchIndex < searchMatches.size) {
            val currentIdx = searchMatches[currentMatchIndex]
            editor.text.setSpan(BackgroundColorSpan(NEON_PURPLE), currentIdx, currentIdx + searchQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            editor.text.setSpan(ForegroundColorSpan(Color.BLACK), currentIdx, currentIdx + searchQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Скроллим к текущему совпадению
            val layout = editor.layout ?: return
            val line = layout.getLineForOffset(currentIdx)
            val y = layout.getLineTop(line)
            editor.scrollTo(0, y - editor.height / 3)
        }
    }

    private fun clearSearchHighlights() {
        val bgSpans = editor.text.getSpans(0, editor.text.length, BackgroundColorSpan::class.java)
        for (span in bgSpans) editor.text.removeSpan(span)
        val fgSpans = editor.text.getSpans(0, editor.text.length, ForegroundColorSpan::class.java)
        for (span in fgSpans) editor.text.removeSpan(span)
    }

    private fun findAllOccurrences(text: String, query: String): List<Int> {
        val indices = mutableListOf<Int>()
        var idx = text.indexOf(query, ignoreCase = true)
        while (idx >= 0) {
            indices.add(idx)
            idx = text.indexOf(query, idx + 1, ignoreCase = true)
        }
        return indices
    }

    // =====================================================================
    // ЛОГИКА АВТОДОПОЛНЕНИЯ (Темный Popup)
    // =====================================================================

    private fun initAutocomplete() {
        suggestionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_PANEL)
        }
        
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(BG_PANEL)
            addView(suggestionsLayout)
        }

        autocompletePopup = PopupWindow(scrollView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ColorDrawable(BG_PANEL))
            isTouchable = true
            isFocusable = false // Важно! Чтобы не отбирать фокус у EditText и не скрывать клавиатуру
        }
    }

    private fun updateAutocomplete() {
        if (isUpdatingText) return

        val text = editor.text.toString()
        val cursor = editor.selectionStart
        if (cursor <= 0) { hideAutocomplete(); return }

        // Ищем начало текущего слова
        var start = cursor - 1
        while (start >= 0 && !text[start].isWhitespace() && text[start] != '\n') start--
        start++
        
        val currentWord = text.substring(start, cursor)
        
        if (currentWord.length >= 2) {
            val suggestions = COMMANDS.filter { it.startsWith(currentWord, ignoreCase = true) && it != currentWord }
            if (suggestions.isNotEmpty()) {
                showAutocompletePopup(suggestions, start, cursor)
            } else {
                hideAutocomplete()
            }
        } else {
            hideAutocomplete()
        }
    }

    private fun showAutocompletePopup(suggestions: List<String>, wordStart: Int, wordEnd: Int) {
        suggestionsLayout.removeAllViews()
        
        for (suggestion in suggestions) {
            val tv = TextView(this).apply {
                text = suggestion
                setTextColor(NEON_PURPLE)
                setBackgroundColor(BG_PANEL)
                setPadding(32, 20, 32, 20)
                typeface = Typeface.MONOSPACE
                textSize = 14f
                // Обработка нажатия через Touch, так как Popup не в фокусе
                setOnTouchListener { _, _ ->
                    applyAutocomplete(suggestion, wordStart, wordEnd)
                    true
                }
            }
            suggestionsLayout.addView(tv)
        }

        // Позиционируем Popup рядом с курсором
        val layout = editor.layout ?: return
        val line = layout.getLineForOffset(wordEnd)
        val x = layout.getPrimaryHorizontal(wordEnd)
        val y = layout.getLineTop(line)
        
        val location = IntArray(2)
        editor.getLocationOnScreen(location)

        try {
            autocompletePopup.showAtLocation(editor, Gravity.NO_GRAVITY, location[0] + x.toInt(), location[1] + y + editor.lineHeight)
        } catch (e: Exception) {
            // Игнорируем ошибки позиционирования
        }
    }

    private fun hideAutocomplete() {
        if (autocompletePopup.isShowing) autocompletePopup.dismiss()
    }

    private fun applyAutocomplete(word: String, start: Int, end: Int) {
        isUpdatingText = true
        editor.text.replace(start, end, "$word ")
        editor.setSelection(start + word.length + 1)
        isUpdatingText = false
        hideAutocomplete()
    }

    // =====================================================================
    // TEXT WATCHER & BACK PRESS
    // =====================================================================

    private fun setupTextWatcher() {
        editor.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingText) {
                    if (editor.text.toString() != originalText) isModified = true
                    updateAutocomplete()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                hideAutocomplete() // Скрываем при любом изменении, чтобы пересчитать позицию
            }
        })
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isModified) showUnsavedDialog()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // =====================================================================
    // ФАЙЛОВЫЕ ОПЕРАЦИИ (SAF)
    // =====================================================================

    private fun saveFile() {
        val workDirUri = getWorkDirUri()
        if (workDirUri == null) {
            Toast.makeText(this, "Ошибка: Рабочая папка не выбрана!", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val treeDoc = DocumentFile.fromTreeUri(this, workDirUri) ?: throw Exception("Cannot open work dir")
            val targetFolderName = getTargetFolder(getExtension(fileName))
            var targetDir = treeDoc.findFile(targetFolderName)
            if (targetDir == null || !targetDir.isDirectory) targetDir = treeDoc.createDirectory(targetFolderName)
            if (targetDir == null) throw Exception("Cannot create folder $targetFolderName")

            var fileDoc = targetDir.findFile(fileName)
            if (fileDoc == null) fileDoc = targetDir.createFile("text/plain", fileName)
            if (fileDoc == null) throw Exception("Cannot create file $fileName")

            val content = editor.text.toString()
            contentResolver.openOutputStream(fileDoc.uri, "wt")?.use { out ->
                out.write(content.toByteArray())
            } ?: throw Exception("Cannot open output stream")

            fileUri = fileDoc.uri
            isModified = false
            originalText = content
            Toast.makeText(this, "✅ Сохранено в $targetFolderName/$fileName", Toast.LENGTH_SHORT).show()
            titleView.text = "⚡ NeonPad: $fileName (Saved)"

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadFileContent() {
        try {
            contentResolver.openInputStream(fileUri!!)?.bufferedReader()?.use {
                val text = it.readText()
                editor.setText(text)
                originalText = text
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка чтения файла", Toast.LENGTH_SHORT).show()
        }
    }

    // =====================================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // =====================================================================

    private fun getWorkDirUri(): Uri? {
        val prefs = getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
        return prefs.getString("work_dir_uri", null)?.let { Uri.parse(it) }
    }

    private fun getExtension(name: String): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(dotIndex).lowercase() else ""
    }

    private fun getTargetFolder(extension: String): String {
        return when (extension) {
            ".syd" -> "Scripts"
            ".lua" -> "MoonlightScripts"
            ".ft" -> "ForthScripts"
            else -> "Scripts"
        }
    }

    private fun getTemplateForExtension(ext: String): String {
        return when (ext) {
            ".syd" -> "# SydShell Script\n# Author: Syndes\n\necho \"Hello from Syd!\"\n"
            ".lua" -> "-- Moonlight Lua Script\n-- Powered by Lua\n\nprint(\"Hello from Moonlight!\")\n"
            ".ft" -> "\\ Forth Script\n\\ Compiled in Forth\n\n.\" Hello from Forth!\" CR\n"
            else -> ""
        }
    }

    private fun attemptExit() {
        if (isModified) showUnsavedDialog() else finish()
    }

    private fun showUnsavedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Несохраненные изменения")
            .setMessage("Сохранить изменения в NeonPad?")
            .setPositiveButton("Сохранить") { _, _ -> saveFile(); finish() }
            .setNegativeButton("Не сохранять") { _, _ -> finish() }
            .setNeutralButton("Отмена", null)
            .show()
    }
}
