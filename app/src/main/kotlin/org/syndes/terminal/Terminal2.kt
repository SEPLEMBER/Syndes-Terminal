package org.syndes.terminal

import android.content.Context

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
                    // Если ввели просто "привет" или "привет мир"
                    if (args.isEmpty() || args.joinToString(" ").lowercase() == "мир") {
                        "hello world"
                    } else {
                        null // Если "привет" с другими аргументами, не обрабатываем
                    }
                }
                
                // -------------------------
                // Здесь можно добавлять другие команды для Terminal2
                // Например:
                // "mycommand" -> {
                //     "Info: mycommand executed"
                // }
                // -------------------------
                
                else -> null // Команда не найдена, возвращаем null
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
