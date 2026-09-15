package org.syndes.terminal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Locale

class NEXYDACTIVITY : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    private lateinit var tvPath: TextView
    private lateinit var tvSelected: TextView
    private lateinit var tvStatus: TextView
    private lateinit var fileListContainer: LinearLayout
    private lateinit var editorPanel: LinearLayout
    private lateinit var tvEditorName: TextView
    private lateinit var etEditor: EditText
    private lateinit var btnOpen: Button
    private lateinit var btnExport: Button
    private lateinit var btnDelete: Button
    private lateinit var btnImport: Button
    private lateinit var btnSaf: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private val storageRoot by lazy { File(filesDir, ROOT_FOLDER_NAME) }

    private var currentDir: File = File("")
    private var selectedItem: File? = null
    private var editingFile: File? = null
    private var exportTreeUri: Uri? = null
    private var requestExportFolderOnStart = false

    private val pickExportTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                setStatus("Export folder not selected.")
                return@registerForActivityResult
            }
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                exportTreeUri = uri
                prefs.edit().putString(KEY_EXPORT_TREE_URI, uri.toString()).apply()
                setStatus("Export folder saved.")
            } catch (t: Throwable) {
                setStatus("Export folder error: ${t.message ?: "unknown"}")
            }
        }

    private val pickImportFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                setStatus("Import cancelled.")
                return@registerForActivityResult
            }
            importFromUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_nexydactivity)

        bindViews()
        bindActions()

        ensureStorageRoot()
        loadSavedExportTree()

        currentDir = storageRoot
        renderDirectory()

        requestExportFolderOnStart = exportTreeUri == null
        if (requestExportFolderOnStart) {
            tvStatus.post {
                if (exportTreeUri == null) {
                    pickExportTreeLauncher.launch(null)
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    editorPanel.visibility == View.VISIBLE -> closeEditor()
                    currentDir != storageRoot -> navigateUp()
                    else -> finish()
                }
            }
        })
    }

    private fun bindViews() {
        tvPath = findViewById(R.id.tvPath)
        tvSelected = findViewById(R.id.tvSelected)
        tvStatus = findViewById(R.id.tvStatus)
        fileListContainer = findViewById(R.id.fileListContainer)
        editorPanel = findViewById(R.id.editorPanel)
        tvEditorName = findViewById(R.id.tvEditorName)
        etEditor = findViewById(R.id.etEditor)

        btnOpen = findViewById(R.id.btnOpen)
        btnExport = findViewById(R.id.btnExport)
        btnDelete = findViewById(R.id.btnDelete)
        btnImport = findViewById(R.id.btnImport)
        btnSaf = findViewById(R.id.btnSaf)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
    }

    private fun bindActions() {
        btnOpen.setOnClickListener { openSelected() }
        btnExport.setOnClickListener { exportSelected() }
        btnDelete.setOnClickListener { deleteSelected() }
        btnImport.setOnClickListener { pickImportFileLauncher.launch(arrayOf("*/*")) }
        btnSaf.setOnClickListener { pickExportTreeLauncher.launch(exportTreeUri) }
        btnSave.setOnClickListener { saveEditingFile() }
        btnCancel.setOnClickListener { closeEditor() }
    }

    private fun ensureStorageRoot() {
        if (!storageRoot.exists()) {
            storageRoot.mkdirs()
        }
        currentDir = storageRoot
    }

    private fun loadSavedExportTree() {
        val saved = prefs.getString(KEY_EXPORT_TREE_URI, null) ?: return
        exportTreeUri = runCatching { Uri.parse(saved) }.getOrNull()
    }

    private fun renderDirectory() {
        if (!currentDir.exists()) {
            currentDir.mkdirs()
        }

        tvPath.text = "Current dir: ${currentDir.absolutePath}"
        tvSelected.text = "Selected: ${selectedItem?.name ?: "none"}"

        fileListContainer.removeAllViews()

        val files = currentDir.listFiles()
            ?.sortedWith(
                compareBy<File> { !it.isDirectory }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )
            .orEmpty()

        if (files.isEmpty()) {
            addListRow("(empty)")
            return
        }

        for (file in files) {
            addFileRow(file)
        }
    }

    private fun addListRow(text: String) {
        val row = TextView(this)
        row.text = text
        row.textSize = 16f
        row.setPadding(16, 14, 16, 14)
        row.setTextColor(0xFFA0A0A0.toInt())
        fileListContainer.addView(row)
    }

    private fun addFileRow(file: File) {
        val row = TextView(this)
        row.text = if (file.isDirectory) "■ ${file.name}" else "● ${file.name}"
        row.textSize = 16f
        row.setPadding(16, 14, 16, 14)
        row.setTextColor(0xFFA0A0A0.toInt())
        row.isClickable = true
        row.isFocusable = true
        row.setBackgroundColor(if (selectedItem == file) 0xFF1A1A1A.toInt() else 0x00000000)

        row.setOnClickListener {
            selectedItem = file
            tvSelected.text = "Selected: ${file.name}"
            renderDirectory()
        }

        row.setOnLongClickListener {
            selectedItem = file
            tvSelected.text = "Selected: ${file.name}"
            renderDirectory()
            openSelected()
            true
        }

        fileListContainer.addView(row)
    }

    private fun openSelected() {
        val file = selectedItem ?: run {
            setStatus("Nothing selected.")
            return
        }

        when {
            file.isDirectory -> {
                currentDir = file
                selectedItem = null
                closeEditor()
                renderDirectory()
                setStatus("Opened folder: ${file.name}")
            }

            file.isFile && file.name.endsWith(".txt", ignoreCase = true) -> {
                openTextFile(file)
            }

            else -> {
                setStatus("Preview is only enabled for .txt right now.")
            }
        }
    }

    private fun openTextFile(file: File) {
        try {
            val text = file.readText(Charsets.UTF_8)
            editingFile = file
            tvEditorName.text = file.name
            etEditor.setText(text)
            editorPanel.visibility = View.VISIBLE
            setStatus("Editing ${file.name}")
        } catch (t: Throwable) {
            setStatus("Open error: ${t.message ?: "unknown"}")
        }
    }

    private fun saveEditingFile() {
        val file = editingFile ?: run {
            setStatus("No text file open.")
            return
        }

        try {
            file.parentFile?.mkdirs()
            file.writeText(etEditor.text.toString(), Charsets.UTF_8)
            setStatus("Saved ${file.name}")
            renderDirectory()
        } catch (t: Throwable) {
            setStatus("Save error: ${t.message ?: "unknown"}")
        }
    }

    private fun closeEditor() {
        editingFile = null
        editorPanel.visibility = View.GONE
    }

    private fun deleteSelected() {
        val file = selectedItem ?: run {
            setStatus("Nothing selected.")
            return
        }

        try {
            val deleted = file.deleteRecursively()
            if (deleted) {
                if (editingFile == file) closeEditor()
                selectedItem = null
                renderDirectory()
                setStatus("Deleted ${file.name}")
            } else {
                setStatus("Delete failed: ${file.name}")
            }
        } catch (t: Throwable) {
            setStatus("Delete error: ${t.message ?: "unknown"}")
        }
    }

    private fun exportSelected() {
        val file = selectedItem ?: run {
            setStatus("Nothing selected.")
            return
        }

        val treeUri = exportTreeUri ?: run {
            setStatus("Choose an export folder first.")
            pickExportTreeLauncher.launch(null)
            return
        }

        Thread {
            val result = runCatching {
                val tree = DocumentFile.fromTreeUri(this, treeUri)
                    ?: error("Unable to open export folder.")

                if (file.isDirectory) {
                    exportDirectory(file, tree)
                } else {
                    exportSingleFile(file, tree)
                }
            }

            runOnUiThread {
                result.fold(
                    onSuccess = { setStatus("Exported ${file.name}") },
                    onFailure = { setStatus("Export error: ${it.message ?: "unknown"}") }
                )
            }
        }.start()
    }

    private fun importFromUri(uri: Uri) {
        Thread {
            val result = runCatching {
                val pickedName = resolveDisplayName(uri) ?: "imported_file"
                val target = uniqueFile(currentDir, pickedName)
                val input = contentResolver.openInputStream(uri) ?: error("Unable to read imported file.")
                input.use { inp ->
                    FileOutputStream(target).use { output ->
                        inp.copyTo(output)
                    }
                }
                target
            }

            runOnUiThread {
                result.fold(
                    onSuccess = {
                        renderDirectory()
                        setStatus("Imported ${it.name}")
                    },
                    onFailure = {
                        setStatus("Import error: ${it.message ?: "unknown"}")
                    }
                )
            }
        }.start()
    }

    private fun exportSingleFile(source: File, parent: DocumentFile) {
        val name = uniqueDocumentName(parent, source.name, false)
        val mime = guessMimeType(source.name)
        val outDoc = parent.createFile(mime, name) ?: error("Unable to create export file.")
        val output = contentResolver.openOutputStream(outDoc.uri, "w")
            ?: error("Unable to open export output.")
        output.use { out ->
            FileInputStream(source).use { input ->
                input.copyTo(out)
            }
        }
    }

    private fun exportDirectory(sourceDir: File, parent: DocumentFile) {
        val name = uniqueDocumentName(parent, sourceDir.name, true)
        val outDir = parent.createDirectory(name) ?: error("Unable to create export folder.")
        sourceDir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                exportDirectory(child, outDir)
            } else if (child.isFile) {
                exportSingleFile(child, outDir)
            }
        }
    }

    private fun uniqueDocumentName(parent: DocumentFile, baseName: String, directory: Boolean): String {
        var candidate = baseName
        var index = 1
        while (parent.findFile(candidate) != null) {
            candidate = if (directory) {
                "$baseName ($index)"
            } else {
                val dot = baseName.lastIndexOf('.')
                if (dot > 0) {
                    val stem = baseName.substring(0, dot)
                    val ext = baseName.substring(dot)
                    "$stem ($index)$ext"
                } else {
                    "$baseName ($index)"
                }
            }
            index++
        }
        return candidate
    }

    private fun uniqueFile(parent: File, baseName: String): File {
        var candidate = File(parent, baseName)
        if (!candidate.exists()) return candidate

        var index = 1
        while (candidate.exists()) {
            val uniqueName = if (baseName.contains('.')) {
                val dot = baseName.lastIndexOf('.')
                val stem = baseName.substring(0, dot)
                val ext = baseName.substring(dot)
                "$stem ($index)$ext"
            } else {
                "$baseName ($index)"
            }
            candidate = File(parent, uniqueName)
            index++
        }
        return candidate
    }

    private fun resolveDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return DocumentFile.fromSingleUri(this, uri)?.name
            ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun guessMimeType(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase(Locale.getDefault())) {
            "txt", "md", "csv", "log", "json", "xml", "kts", "kt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    private fun navigateUp() {
        val parent = currentDir.parentFile ?: return
        if (parent.canonicalPath.startsWith(storageRoot.canonicalPath)) {
            currentDir = parent
            selectedItem = null
            closeEditor()
            renderDirectory()
            setStatus("Moved up.")
        }
    }

    private fun setStatus(message: String) {
        tvStatus.text = "Status: $message"
    }

    companion object {
        private const val PREFS_NAME = "nexyda_prefs"
        private const val KEY_EXPORT_TREE_URI = "export_tree_uri"
        private const val ROOT_FOLDER_NAME = "nexyda_storage"
    }
}
