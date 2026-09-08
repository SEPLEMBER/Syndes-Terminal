package org.syndes.terminal

import android.content.Context

object AliasManager {
    private const val PREFS_NAME = "terminal_prefs"
    private const val KEY_ALIASES = "aliases"

    /**
     * Возвращает словарь алиасов: ключ (в нижнем регистре) -> команда
     */
    fun getAliases(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawAliases = prefs.getString(KEY_ALIASES, "") ?: ""
        
        return rawAliases.split("\n")
            .filter { it.isNotBlank() && it.contains("=") }
            .associate { line ->
                val parts = line.split("=", limit = 2)
                parts[0].trim().lowercase() to parts[1].trim()
            }
    }

    /**
     * Проверяет, является ли первое слово команды алиасом, и подменяет его.
     * Поддерживает аргументы: если алиас "mcpe", а ввели "MCPE --debug", 
     * вернет "<команда_из_алиаса> --debug"
     */
    fun resolve(context: Context, input: String): String {
        val parts = input.split("\\s+".toRegex(), limit = 2)
        val firstWord = parts[0].lowercase()
        val aliases = getAliases(context)

        if (aliases.containsKey(firstWord)) {
            val aliasValue = aliases[firstWord]!!
            return if (parts.size > 1) {
                "$aliasValue ${parts[1]}" // Алиас + оригинальные аргументы
            } else {
                aliasValue
            }
        }
        return input
    }

    /**
     * Добавляет или обновляет алиас
     */
    fun addAlias(context: Context, name: String, command: String): Boolean {
        if (name.isBlank() || command.isBlank() || name.contains("=")) return false
        
        val currentAliases = getAliases(context).toMutableMap()
        currentAliases[name.lowercase()] = command
        
        return saveAliases(context, currentAliases)
    }

    /**
     * Удаляет алиас по имени
     */
    fun removeAlias(context: Context, name: String): Boolean {
        val currentAliases = getAliases(context).toMutableMap()
        val removed = currentAliases.remove(name.lowercase()) != null
        
        return if (removed) saveAliases(context, currentAliases) else false
    }

    /**
     * Форматирует список алиасов для вывода в терминал
     */
    fun getFormattedList(context: Context): String {
        val aliases = getAliases(context)
        if (aliases.isEmpty()) return "No aliases configured."
        
        return aliases.entries.joinToString("\n") { (key, value) ->
            "  $key => $value"
        }
    }

    private fun saveAliases(context: Context, aliases: Map<String, String>): Boolean {
        return try {
            val rawString = aliases.entries.joinToString("\n") { "${it.key}=${it.value}" }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ALIASES, rawString)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}
