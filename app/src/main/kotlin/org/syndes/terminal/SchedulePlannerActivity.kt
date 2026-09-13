package org.syndes.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.syndes.terminal.databinding.ActivitySchedulePlannerBinding

class SchedulePlannerActivity : AppCompatActivity() {

    private var _binding: ActivitySchedulePlannerBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        _binding = ActivitySchedulePlannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.btnSolve.setOnClickListener {
            solveSchedule()
        }

        binding.btnClear.setOnClickListener {
            clearInputs()
        }

        binding.btnCopyResult.setOnClickListener {
            copyResultToClipboard()
        }
    }

    private fun solveSchedule() {
        hideErrors()

        // Парсинг входных данных
        val slotsStr = binding.etSlots.text.toString()
        val participantsStr = binding.etParticipants.text.toString()
        val maxShiftsStr = binding.etMaxShifts.text.toString()
        val forbiddenPairsStr = binding.etForbiddenPairs.text.toString()
        val requiredStr = binding.etRequired.text.toString()

        // Валидация слотов
        val slots = slotsStr.toIntOrNull()
        if (slots == null || slots <= 0) {
            showError(binding.errSlots, "Введите корректное количество слотов > 0")
            return
        }

        // ИСПРАВЛЕНО: Уменьшен лимит для защиты от зависания
        if (slots > 20) {
            showError(binding.errSlots, "Максимум 20 слотов для производительности")
            return
        }

        // Парсинг участников
        val participants = participantsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (participants.isEmpty()) {
            showError(binding.errParticipants, "Введите хотя бы одного участника")
            return
        }

        // ИСПРАВЛЕНО: Уменьшен лимит участников
        if (participants.size > 10) {
            showError(binding.errParticipants, "Максимум 10 участников")
            return
        }

        // Валидация максимума смен
        val maxShifts = if (maxShiftsStr.isEmpty()) {
            slots // Если пусто, максимум = количество слотов
        } else {
            val ms = maxShiftsStr.toIntOrNull()
            if (ms == null || ms <= 0) {
                showError(binding.errMaxShifts, "Максимум смен должен быть > 0")
                return
            }
            ms
        }

        if (maxShifts > slots) {
            showError(binding.errMaxShifts, "Максимум смен не может превышать количество слотов ($slots)")
            return
        }

        // ИСПРАВЛЕНО: Парсинг запрещённых пар с поддержкой имён с дефисом
        val forbiddenPairs = mutableSetOf<Pair<String, String>>()
        if (forbiddenPairsStr.isNotEmpty()) {
            val pairs = forbiddenPairsStr.split(",").map { it.trim() }
            for (pair in pairs) {
                // Ищем разделитель " - " (с пробелами) для поддержки имён с дефисом
                val parts = pair.split(" - ").map { it.trim() }
                if (parts.size != 2) {
                    showError(binding.errForbiddenPairs, "Неверный формат пары: '$pair'. Используйте формат 'Имя1 - Имя2'")
                    return
                }
                val p1 = parts[0]
                val p2 = parts[1]
                if (!participants.contains(p1)) {
                    showError(binding.errForbiddenPairs, "Участник '$p1' не найден в списке")
                    return
                }
                if (!participants.contains(p2)) {
                    showError(binding.errForbiddenPairs, "Участник '$p2' не найден в списке")
                    return
                }
                forbiddenPairs.add(p1 to p2)
                forbiddenPairs.add(p2 to p1)
            }
        }

        // Парсинг обязательных
        val required = requiredStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (req in required) {
            if (!participants.contains(req)) {
                showError(binding.errRequired, "Обязательный участник '$req' не найден")
                return
            }
        }

        // Блокируем кнопку
        binding.btnSolve.isEnabled = false
        binding.btnSolve.text = "Поиск решения..."

        // Запускаем CSP в фоне
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                solveCSP(slots, participants, maxShifts, forbiddenPairs, required)
            }

            binding.btnSolve.isEnabled = true
            binding.btnSolve.text = "Найти решение"

            displayResult(result)
        }
    }

    /**
     * ИСПРАВЛЕННЫЙ CSP алгоритм с бэктрекингом
     */
    private fun solveCSP(
        slots: Int,
        participants: List<String>,
        maxShifts: Int,
        forbiddenPairs: Set<Pair<String, String>>,
        required: List<String>
    ): String {
        // schedule[slot] = список участников в этом слоте
        val schedule = Array(slots) { mutableListOf<String>() }
        
        // Счётчик смен для каждого участника
        val shiftCount = participants.associateWith { 0 }.toMutableMap()
        
        // ИСПРАВЛЕНО: Множество назначенных участников для обязательных
        val assigned = mutableSetOf<String>()

        /**
         * ИСПРАВЛЕНО: Проверка ограничений для добавления участника в слот
         */
        fun isValid(slotIndex: Int, participant: String): Boolean {
            // Проверка 1: Не превышен ли максимум смен
            if ((shiftCount[participant] ?: 0) >= maxShifts) {
                return false
            }

            // Проверка 2: Нет ли запрещённых пар в этом слоте
            for (existing in schedule[slotIndex]) {
                if ((participant to existing) in forbiddenPairs) {
                    return false
                }
            }

            return true
        }

        /**
         * ИСПРАВЛЕНО: Бэктрекинг с правильным откатом состояния
         */
        fun backtrack(slotIndex: Int): Boolean {
            // Базовый случай: все слоты заполнены
            if (slotIndex == slots) {
                // Проверяем, все ли обязательные назначены
                return required.all { it in assigned }
            }

            // ИСПРАВЛЕНО: Приоритет обязательным участникам, которые ещё не назначены
            val remainingRequired = required.filter { it !in assigned }
            
            // Если остались обязательные, а слотов мало — назначаем их
            if (remainingRequired.isNotEmpty() && (slots - slotIndex) <= remainingRequired.size) {
                val toAssign = remainingRequired.take(1)
                if (toAssign.all { isValid(slotIndex, it) }) {
                    schedule[slotIndex].addAll(toAssign)
                    toAssign.forEach {
                        shiftCount[it] = (shiftCount[it] ?: 0) + 1
                        assigned.add(it)
                    }

                    if (backtrack(slotIndex + 1)) {
                        return true
                    }

                    // Откат
                    schedule[slotIndex].clear()
                    toAssign.forEach {
                        shiftCount[it] = (shiftCount[it] ?: 0) - 1
                        assigned.remove(it) // ИСПРАВЛЕНО: Правильный откат
                    }
                }
                return false
            }

            // Генерация возможных назначений
            val possibleAssignments = generatePossibleAssignments(slotIndex, participants)

            for (assignment in possibleAssignments) {
                // Проверяем валидность
                if (assignment.all { isValid(slotIndex, it) }) {
                    // Применяем назначение
                    schedule[slotIndex].addAll(assignment)
                    assignment.forEach {
                        shiftCount[it] = (shiftCount[it] ?: 0) + 1
                        assigned.add(it) // ИСПРАВЛЕНО: Добавляем в назначенные
                    }

                    // Рекурсия
                    if (backtrack(slotIndex + 1)) {
                        return true
                    }

                    // ИСПРАВЛЕНО: Полный откат состояния
                    schedule[slotIndex].clear()
                    assignment.forEach {
                        shiftCount[it] = (shiftCount[it] ?: 0) - 1
                        assigned.remove(it)
                    }
                }
            }

            return false
        }

        /**
         * ИСПРАВЛЕНО: Оптимизированная генерация назначений
         */
        fun generatePossibleAssignments(slotIndex: Int, participants: List<String>): List<List<String>> {
            val result = mutableListOf<List<String>>()
            
            // ИСПРАВЛЕНО: Приоритет обязательным участникам
            val remainingRequired = required.filter { it !in assigned }
            if (remainingRequired.isNotEmpty()) {
                // Сначала пробуем назначить обязательных
                for (p in remainingRequired) {
                    result.add(listOf(p))
                }
            }

            // Вариант 1: Один участник (все остальные)
            for (p in participants) {
                if (!result.contains(listOf(p))) {
                    result.add(listOf(p))
                }
            }

            // Вариант 2: Два участника (если разрешено и нет запрещённых пар)
            if (participants.size >= 2) {
                for (i in participants.indices) {
                    for (j in i + 1 until participants.size) {
                        val p1 = participants[i]
                        val p2 = participants[j]
                        if ((p1 to p2) !in forbiddenPairs) {
                            result.add(listOf(p1, p2))
                        }
                    }
                }
            }

            return result
        }

        // ИСПРАВЛЕНО: Проверка возможности решения до запуска
        val totalCapacity = participants.size * maxShifts
        if (totalCapacity < slots) {
            return "❌ Решение невозможно: недостаточная ёмкость участников ($totalCapacity < $slots)"
        }

        // Запуск алгоритма
        val success = backtrack(0)

        return if (success) {
            formatSolution(schedule, shiftCount)
        } else {
            "❌ Решение не найдено\n\nВозможные причины:\n• Слишком жёсткие ограничения\n• Недостаточно участников для покрытия всех слотов\n• Противоречивые требования обязательных участников"
        }
    }

    private fun formatSolution(schedule: Array<MutableList<String>>, shiftCount: Map<String, Int>): String {
        return buildString {
            appendLine("✅ РЕШЕНИЕ НАЙДЕНО")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()

            schedule.forEachIndexed { index, participants ->
                appendLine("Слот ${index + 1}:")
                if (participants.isEmpty()) {
                    appendLine("  (пусто)")
                } else {
                    participants.forEach { p ->
                        appendLine("  • $p")
                    }
                }
                appendLine()
            }

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📊 Статистика смен:")
            shiftCount.entries.sortedByDescending { it.value }.forEach { (participant, count) ->
                appendLine("  $participant: $count смен")
            }
        }
    }

    private fun displayResult(result: String) {
        binding.tvResult.text = result.trimEnd()
        binding.tvResult.visibility = View.VISIBLE
        binding.btnCopyResult.visibility = View.VISIBLE
    }

    private fun copyResultToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Расписание", binding.tvResult.text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(applicationContext, "✓ Решение скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun clearInputs() {
        binding.etSlots.text.clear()
        binding.etParticipants.text.clear()
        binding.etMaxShifts.text.clear()
        binding.etForbiddenPairs.text.clear()
        binding.etRequired.text.clear()
        binding.tvResult.visibility = View.GONE
        binding.btnCopyResult.visibility = View.GONE
        hideErrors()
    }

    private fun showError(errorView: TextView, message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideErrors() {
        binding.errSlots.visibility = View.GONE
        binding.errParticipants.visibility = View.GONE
        binding.errMaxShifts.visibility = View.GONE
        binding.errForbiddenPairs.visibility = View.GONE
        binding.errRequired.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
