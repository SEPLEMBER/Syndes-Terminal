package org.syndes.terminal

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.io.InputStream

class Terminal2 {
    
    /**
     * Fallback-терминал для команд, которые не распознал основной Terminal.kt.
     * Возвращает строку-результат или null, если команда не найдена.
     */
    fun execute(command: String, context: Context): String? {
        val (cmdName, args) = parseCommand(command)
        if (cmdName.isEmpty()) return null
        
        return try {
            when (cmdName.lowercase()) {
                "привет" -> {
                    if (args.isEmpty() || args.joinToString(" ").lowercase() == "мир") {
                        "hello world"
                    } else {
                        null 
                    }
                }

                // НОВЫЕ КОМАНДЫ
                "flashlight" -> cmdFlashlight(context, args)
                "1" -> if (args.firstOrNull()?.uppercase() == "FLASHLIGHT") cmdFlashlight(context, listOf("1")) else null
                "0" -> if (args.firstOrNull()?.uppercase() == "FLASHLIGHT") cmdFlashlight(context, listOf("0")) else null
                "neoraven" -> cmdNeoraven(context, args)

                // СТАРЫЕ КОМАНДЫ
                "tree" -> cmdTree(context, args)
                "basename" -> cmdBasename(args)
                "dirname" -> cmdDirname(args)
                "printf" -> cmdPrintf(args)
                "seq" -> cmdSeq(args)
                "file" -> cmdFile(context, args)
                "base64" -> cmdBase64(context, args)
                "xxd" -> cmdXxd(context, args)
                "strings" -> cmdStrings(context, args)
                "neopad" -> cmdNeopad(context, args)
                
                else -> null
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // =====================================================================
    // УМНЫЙ ПАРСИНГ КОМАНД (ПОДДЕРЖКА КАВЫЧЕК)
    // =====================================================================

    private fun parseCommand(command: String): Pair<String, List<String>> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '
        
        for (c in command) {
            when {
                !inQuotes && (c == '"' || c == '\'') -> {
                    inQuotes = true
                    quoteChar = c
                }
                inQuotes && c == quoteChar -> {
                    inQuotes = false
                }
                !inQuotes && c.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        
        if (result.isEmpty()) return "" to emptyList()
        return result[0] to result.drop(1)
    }

    // =====================================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ РАБОТЫ С SAF
    // =====================================================================

    private fun getCurrentDir(context: Context): DocumentFile? {
        val prefs = context.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("current_dir_uri", null) ?: prefs.getString("work_dir_uri", null)
        if (uriStr == null) return null
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
        } catch (e: Exception) { null }
    }

    private fun resolveFile(context: Context, path: String): DocumentFile? {
        if (path.startsWith("content://")) {
            return DocumentFile.fromSingleUri(context, Uri.parse(path))
        }
        val currentDir = getCurrentDir(context) ?: return null
        val parts = path.split("/").filter { it.isNotEmpty() && it != "." }
        var current: DocumentFile? = currentDir
        for (part in parts) {
            if (part == "..") {
                current = current?.parentFile
            } else {
                current = current?.findFile(part)
            }
            if (current == null) return null
        }
        return current
    }

    private fun InputStream.readAllBytesSafe(): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(1024)
        var count: Int
        while (this.read(data).also { count = it } != -1) {
            buffer.write(data, 0, count)
        }
        return buffer.toByteArray()
    }

    // =====================================================================
    // РЕАЛИЗАЦИЯ НОВЫХ КОМАНД
    // =====================================================================

    private fun cmdFlashlight(context: Context, args: List<String>): String {
        val state = args.firstOrNull()
        if (state != "1" && state != "0") {
            return "Usage: flashlight 1 (on) or 0 (off)\nAlternatively: 1 FLASHLIGHT or 0 FLASHLIGHT"
        }

        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return "\u001B[31m[ERROR]\u001B[0m No camera found"
            
            if (state == "1") {
                cameraManager.setTorchMode(cameraId, true)
                "\u001B[32m[SUCCESS]\u001B[0m Flashlight turned ON"
            } else {
                cameraManager.setTorchMode(cameraId, false)
                "\u001B[33m[INFO]\u001B[0m Flashlight turned OFF"
            }
        } catch (e: SecurityException) {
            "\u001B[31m[ERROR]\u001B[0m Permission denied. Please grant CAMERA permission in Android settings."
        } catch (e: Exception) {
            "\u001B[31m[ERROR]\u001B[0m Failed to control flashlight: ${e.message}"
        }
    }

    private fun cmdNeoraven(context: Context, args: List<String>): String {
        val prefs = context.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
        val workDir = prefs.getString("work_dir_uri", null)
        val secure = prefs.getBoolean("secure_screenshots", false)
        
        val fsConnect = if (!workDir.isNullOrEmpty()) "\u001B[32mTRUE\u001B[0m" else "\u001B[31mFALSE\u001B[0m"
        val flSecure = if (secure) "\u001B[32mTRUE\u001B[0m" else "\u001B[31mFALSE\u001B[0m"
        
        val androidVer = Build.VERSION.RELEASE ?: "Unknown"
        
        val uptimeMs = SystemClock.elapsedRealtime()
        val days = uptimeMs / (1000 * 60 * 60 * 24)
        val hours = (uptimeMs / (1000 * 60 * 60)) % 24
        val minutes = (uptimeMs / (1000 * 60)) % 60
        val seconds = (uptimeMs / 1000) % 60
        
        val uptimeStr = if (days > 0) {
            "\u001B[33m${days}d ${hours}h ${minutes}m\u001B[0m"
        } else {
            "\u001B[33m${hours}h ${minutes}m ${seconds}s\u001B[0m"
        }

        // Абсолютно надёжный способ работы с ANSI (избегает багов парсинга \u001B в Kotlin)
        val ESC = Char(27).toString()
        val ANSI_REGEX = Regex("$ESC\\[[0-9;]*[a-zA-Z]")
        val BLUE = "${ESC}[34m"
        val RESET = "${ESC}[0m"

        // Функция для идеального выравнивания с учётом скрытых ANSI-кодов
        fun makeRow(content: String, width: Int = 40): String {
            val clean = content.replace(ANSI_REGEX, "")
            val padLen = maxOf(0, width - clean.length)
            val padding = " ".repeat(padLen)
            return "${BLUE}│${RESET}$content$padding${BLUE}│${RESET}"
        }

        val top = "${BLUE}╭${"─".repeat(40)}╮${RESET}"
        val bottom = "${BLUE}╰${"─".repeat(40)}╯${RESET}"
        val divider = "${BLUE}├${"─".repeat(40)}┤${RESET}"

        val line1 = makeRow("  \u001B[31mS\u001B[32mY\u001B[33mN\u001B[34mD\u001B[35mE\u001B[36mS\u001B[0m \u001B[37mTERMINAL\u001B[0m")
        val line2 = makeRow("  \u001B[36mOS      :\u001B[0m Android \u001B[31m$androidVer\u001B[0m")
        val line3 = makeRow("  \u001B[36mKERNEL  :\u001B[0m ANDROID KERNEL: \u001B[31m$androidVer\u001B[0m")
        val line4 = makeRow("  \u001B[36mUPTIME  :\u001B[0m $uptimeStr")
        val line5 = makeRow("  \u001B[36mFS_CONN :\u001B[0m $fsConnect")
        val line6 = makeRow("  \u001B[36mFL_SEC  :\u001B[0m $flSecure")
        val line7 = makeRow("  \u001B[36mNEORAVEN:\u001B[0m \u001B[35mTRUE\u001B[0m")

        return "$top\n$line1\n$divider\n$line2\n$line3\n$line4\n$line5\n$line6\n$line7\n$bottom"
    }

    // =====================================================================
    // РЕАЛИЗАЦИЯ СТАРЫХ КОМАНД (без изменений)
    // =====================================================================

    private fun cmdTree(context: Context, args: List<String>): String {
        val targetPath = args.firstOrNull()
        val startDir = if (targetPath != null) resolveFile(context, targetPath) else getCurrentDir(context)
        if (startDir == null || !startDir.isDirectory) return "Error: Directory not found"
        val sb = StringBuilder()
        fun traverse(dir: DocumentFile, indent: String) {
            val files = dir.listFiles()
            val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() }))
            for (file in sorted) {
                val name = file.name ?: "unknown"
                sb.append(indent).append(if (file.isDirectory) "📁 " else "📄 ").appendLine(name)
                if (file.isDirectory) traverse(file, indent + "  ")
            }
        }
        traverse(startDir, "")
        return sb.toString().trimEnd()
    }

    private fun cmdBasename(args: List<String>): String {
        val path = args.firstOrNull() ?: return "Usage: basename <path>"
        return path.substringAfterLast("/")
    }

    private fun cmdDirname(args: List<String>): String {
        val path = args.firstOrNull() ?: return "Usage: dirname <path>"
        val lastSlash = path.lastIndexOf('/')
        return if (lastSlash == -1) "." else path.substring(0, lastSlash)
    }

    private fun cmdPrintf(args: List<String>): String {
        if (args.isEmpty()) return "Usage: printf <format> [args]"
        return try {
            var format = args[0]
            format = format.replace("\\\\", "\u0000").replace("\\n", "\n").replace("\\t", "\t").replace("\u0000", "\\")
            val preparedArgs = args.drop(1).map { arg -> arg.toIntOrNull() ?: arg.toDoubleOrNull() ?: arg }.toTypedArray()
            String.format(format, *preparedArgs)
        } catch (e: Exception) {
            "Error: Format mismatch - ${e.message}"
        }
    }

    private fun cmdSeq(args: List<String>): String {
        if (args.size < 2) return "Usage: seq <start> <end>"
        return try {
            val start = args[0].toInt()
            val end = args[1].toInt()
            val sb = StringBuilder()
            if (start <= end) for (i in start..end) sb.appendLine(i)
            else for (i in start downTo end) sb.appendLine(i)
            sb.toString().trimEnd()
        } catch (e: NumberFormatException) {
            "Error: Invalid numbers"
        }
    }

    private fun cmdFile(context: Context, args: List<String>): String {
        val path = args.firstOrNull() ?: return "Usage: file <path>"
        val doc = resolveFile(context, path)
        if (doc == null) return "Error: File not found"
        return "${doc.name ?: "unknown"}: ${doc.type ?: "unknown"}"
    }

    private fun cmdBase64(context: Context, args: List<String>): String {
        if (args.size < 2) return "Usage: base64 <-e|-d> <file>"
        val mode = args[0]; val path = args[1]
        val doc = resolveFile(context, path)
        if (doc == null || doc.uri == null) return "Error: File not found"
        return try {
            val resolver = context.contentResolver
            if (mode == "-e" || mode == "--encode") {
                resolver.openInputStream(doc.uri)?.use { input ->
                    android.util.Base64.encodeToString(input.readAllBytesSafe(), android.util.Base64.DEFAULT)
                } ?: "Error: Cannot read file"
            } else if (mode == "-d" || mode == "--decode") {
                resolver.openInputStream(doc.uri)?.use { input ->
                    String(android.util.Base64.decode(input.bufferedReader().readText(), android.util.Base64.DEFAULT))
                } ?: "Error: Cannot read file"
            } else "Error: Unknown mode. Use -e or -d"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    private fun cmdXxd(context: Context, args: List<String>): String {
        val path = args.firstOrNull() ?: return "Usage: xxd <file>"
        val doc = resolveFile(context, path)
        if (doc == null || doc.uri == null) return "Error: File not found"
        return try {
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                val bytes = input.readAllBytesSafe()
                val sb = StringBuilder()
                for (i in bytes.indices step 16) {
                    val hex = StringBuilder(); val ascii = StringBuilder()
                    for (j in 0 until 16) {
                        if (i + j < bytes.size) {
                            val b = bytes[i + j]
                            hex.append(String.format("%02x ", b))
                            ascii.append(if (b in 32..126) b.toInt().toChar() else '.')
                        } else hex.append("   ")
                    }
                    sb.append(String.format("%08x: %-48s %s\n", i, hex.toString(), ascii.toString()))
                }
                sb.toString().trimEnd()
            } ?: "Error: Cannot read file"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    private fun cmdStrings(context: Context, args: List<String>): String {
        val path = args.firstOrNull() ?: return "Usage: strings <file>"
        val doc = resolveFile(context, path)
        if (doc == null || doc.uri == null) return "Error: File not found"
        return try {
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                val text = input.bufferedReader().readText()
                val sb = StringBuilder(); val current = StringBuilder()
                var i = 0
                while (i < text.length) {
                    if (i + 4 <= text.length && text[i] == '\\' && text[i+1] == 'x') {
                        val hexStr = text.substring(i + 2, i + 4)
                        try {
                            val byteVal = hexStr.toInt(16)
                            if (byteVal in 32..126) current.append(byteVal.toChar())
                            else { if (current.length >= 4) sb.appendLine(current.toString()); current.clear() }
                            i += 4; continue
                        } catch (e: NumberFormatException) {}
                    }
                    val c = text[i]; val code = c.code
                    if (code in 32..126) current.append(c)
                    else { if (current.length >= 4) sb.appendLine(current.toString()); current.clear() }
                    i++
                }
                if (current.length >= 4) sb.appendLine(current.toString())
                sb.toString().trimEnd()
            } ?: "Error: Cannot read file"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    // =====================================================================
    // ЗАПУСК NEONPAD
    // =====================================================================

    private fun cmdNeopad(context: Context, args: List<String>): String {
        if (args.isEmpty()) {
            return "Usage: neopad <filename.syd|lua|ft>"
        }
        
        val fileName = args[0]
        val existingFile = resolveFile(context, fileName)
        
        val intent = Intent(context, ScriptIdeActivity::class.java).apply {
            putExtra(ScriptIdeActivity.EXTRA_FILE_NAME, fileName)
            
            if (existingFile != null && existingFile.isFile && existingFile.uri != null) {
                putExtra(ScriptIdeActivity.EXTRA_FILE_URI, existingFile.uri.toString())
            }
            
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        return try {
            context.startActivity(intent)
            "⚡ Opening NeonPad for '$fileName'..."
        } catch (e: Exception) {
            "Error launching NeonPad: ${e.message}"
        }
    }
}
