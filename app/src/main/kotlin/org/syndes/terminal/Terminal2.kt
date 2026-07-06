package org.syndes.terminal

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.io.InputStream

class Terminal2 {
    
    /**
     * Fallback-терминал для команд, которые не распознал основной Terminal.kt.
     * Возвращает строку-результат или null, если команда не найдена.
     */
    fun execute(command: String, context: Context): String? {
        val parts = command.trim().split("\\s+".toRegex())
        if (parts.isEmpty()) return null
        
        val cmdName = parts[0].lowercase()
        val args = parts.drop(1)
        
        return try {
            when (cmdName) {
                // -------------------------
                // Заглушка: "привет мир" -> "hello world"
                // -------------------------
                "привет" -> {
                    if (args.isEmpty() || args.joinToString(" ").lowercase() == "мир") {
                        "hello world"
                    } else {
                        null 
                    }
                }

                // -------------------------
                // НОВЫЕ КОМАНДЫ
                // -------------------------
                "tree" -> cmdTree(context, args)
                "basename" -> cmdBasename(args)
                "dirname" -> cmdDirname(args)
                "printf" -> cmdPrintf(args)
                "seq" -> cmdSeq(args)
                "file" -> cmdFile(context, args)
                "base64" -> cmdBase64(context, args)
                "xxd" -> cmdXxd(context, args)
                "strings" -> cmdStrings(context, args)
                
                else -> null // Команда не найдена, возвращаем null
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // =====================================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ РАБОТЫ С SAF (Storage Access Framework)
    // =====================================================================

    /**
     * Получает текущую рабочую директорию из SharedPreferences.
     */
    private fun getCurrentDir(context: Context): DocumentFile? {
        val prefs = context.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("current_dir_uri", null) ?: prefs.getString("work_dir_uri", null)
        if (uriStr == null) return null
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
        } catch (e: Exception) { null }
    }

    /**
     * Резолвит путь к файлу. Поддерживает относительные пути (от текущей папки) 
     * и абсолютные SAF-пути (content://...).
     */
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

    /**
     * Безопасно читает весь InputStream в ByteArray (для старых версий Android).
     */
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
    // РЕАЛИЗАЦИЯ КОМАНД
    // =====================================================================

    /**
     * tree [dir] - выводит дерево каталогов.
     */
    private fun cmdTree(context: Context, args: List<String>): String {
        val targetPath = args.firstOrNull()
        val startDir = if (targetPath != null) resolveFile(context, targetPath) else getCurrentDir(context)
        if (startDir == null || !startDir.isDirectory) {
            return "Error: Directory not found"
        }
        val sb = StringBuilder()
        fun traverse(dir: DocumentFile, indent: String) {
            val files = dir.listFiles()
            // Сортировка: сначала папки, потом файлы, по алфавиту
            val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() }))
            for (file in sorted) {
                val name = file.name ?: "unknown"
                sb.append(indent).append(if (file.isDirectory) "📁 " else "📄 ").appendLine(name)
                if (file.isDirectory) {
                    traverse(file, indent + "  ")
                }
            }
        }
        traverse(startDir, "")
        return sb.toString().trimEnd()
    }

    /**
     * basename <path> - извлекает имя файла из пути.
     */
    private fun cmdBasename(args: List<String>): String {
        val path = args.firstOrNull() ?: return "Usage: basename <path>"
        return path.substringAfterLast("/")
    }

    /**
     * dirname <path> - извлекает директорию из пути.
     */
    private fun cmdDirname(args: List<String>): String {
        val path = args.firstOrNull() ?: return "Usage: dirname <path>"
        val lastSlash = path.lastIndexOf('/')
        return if (lastSlash == -1) "." else path.substring(0, lastSlash)
    }

    /**
     * printf <format> [args] - форматированный вывод (аналог String.format).
     */
    private fun cmdPrintf(args: List<String>): String {
        if (args.isEmpty()) return "Usage: printf <format> [args]"
        return try {
            val format = args[0]
            String.format(format, *args.drop(1).toTypedArray())
        } catch (e: Exception) {
            "Error: Format mismatch - ${e.message}"
        }
    }

    /**
     * seq <start> <end> - генерирует последовательность чисел.
     */
    private fun cmdSeq(args: List<String>): String {
        if (args.size < 2) return "Usage: seq <start> <end>"
        return try {
            val start = args[0].toInt()
            val end = args[1].toInt()
            val sb = StringBuilder()
            if (start <= end) {
                for (i in start..end) sb.appendLine(i)
            } else {
                for (i in start downTo end) sb.appendLine(i)
            }
            sb.toString().trimEnd()
        } catch (e: NumberFormatException) {
            "Error: Invalid numbers"
        }
    }

    /**
     * file <path> - определяет MIME-тип файла.
     */
    private fun cmdFile(context: Context, args: List<String>): String {
        val path = args.firstOrNull() ?: return "Usage: file <path>"
        val doc = resolveFile(context, path)
        if (doc == null) return "Error: File not found"
        val mimeType = doc.type ?: "unknown"
        val name = doc.name ?: "unknown"
        return "$name: $mimeType"
    }

    /**
     * base64 <-e|-d> <file> - кодирует или декодирует файл в Base64.
     */
    private fun cmdBase64(context: Context, args: List<String>): String {
        if (args.size < 2) return "Usage: base64 <-e|-d> <file>"
        val mode = args[0]
        val path = args[1]
        val doc = resolveFile(context, path)
        if (doc == null || doc.uri == null) return "Error: File not found"
        
        return try {
            val resolver = context.contentResolver
            if (mode == "-e" || mode == "--encode") {
                resolver.openInputStream(doc.uri)?.use { input ->
                    val bytes = input.readAllBytesSafe()
                    android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                } ?: "Error: Cannot read file"
            } else if (mode == "-d" || mode == "--decode") {
                resolver.openInputStream(doc.uri)?.use { input ->
                    val text = input.bufferedReader().readText()
                    val bytes = android.util.Base64.decode(text, android.util.Base64.DEFAULT)
                    String(bytes) // Возвращаем как текст
                } ?: "Error: Cannot read file"
            } else {
                "Error: Unknown mode. Use -e or -d"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * xxd <file> - показывает hex-дамп файла.
     */
    private fun cmdXxd(context: Context, args: List<String>): String {
        val path = args.firstOrNull() ?: return "Usage: xxd <file>"
        val doc = resolveFile(context, path)
        if (doc == null || doc.uri == null) return "Error: File not found"
        
        return try {
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                val bytes = input.readAllBytesSafe()
                val sb = StringBuilder()
                for (i in bytes.indices step 16) {
                    val hex = StringBuilder()
                    val ascii = StringBuilder()
                    for (j in 0 until 16) {
                        if (i + j < bytes.size) {
                            val b = bytes[i + j]
                            hex.append(String.format("%02x ", b))
                            ascii.append(if (b in 32..126) b.toInt().toChar() else '.')
                        } else {
                            hex.append("   ")
                        }
                    }
                    sb.append(String.format("%08x: %-48s %s\n", i, hex.toString(), ascii.toString()))
                }
                sb.toString().trimEnd()
            } ?: "Error: Cannot read file"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * strings <file> - извлекает печатаемые ASCII-строки из бинарного файла.
     */
    private fun cmdStrings(context: Context, args: List<String>): String {
        val path = args.firstOrNull() ?: return "Usage: strings <file>"
        val doc = resolveFile(context, path)
        if (doc == null || doc.uri == null) return "Error: File not found"
        
        return try {
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                val bytes = input.readAllBytesSafe()
                val sb = StringBuilder()
                val current = StringBuilder()
                for (b in bytes) {
                    if (b in 32..126) { // Печатаемые ASCII символы
                        current.append(b.toInt().toChar())
                    } else {
                        if (current.length >= 4) { // Минимальная длина строки
                            sb.appendLine(current.toString())
                        }
                        current.clear()
                    }
                }
                if (current.length >= 4) sb.appendLine(current.toString())
                sb.toString().trimEnd()
            } ?: "Error: Cannot read file"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
