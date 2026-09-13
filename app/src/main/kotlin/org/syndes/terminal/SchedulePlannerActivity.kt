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
import kotlinx.coroutines.withTimeoutOrNull
import org.syndes.terminal.databinding.ActivitySchedulePlannerBinding
import kotlin.math.abs
import kotlin.random.Random

class SchedulePlannerActivity : AppCompatActivity() {

    private var _binding: ActivitySchedulePlannerBinding? = null
    private val binding get() = _binding!!

    // Для поиска разных решений
    private var solutionCounter = 0

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
            solutionCounter = 0
            solveSchedule()
        }

        binding.btnFindAnother.setOnClickListener {
            solutionCounter++
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
        val minShiftsStr = binding.etMinShifts.text.toString()
        val maxShiftsStr = binding.etMaxShifts.text.toString()
        val minSlotSizeStr = binding.etMinSlotSize.text.toString()
        val maxSlotSizeStr = binding.etMaxSlotSize.text.toString()
        val forbiddenPairsStr = binding.etForbiddenPairs.text.toString()
        val requiredPairsStr = binding.etRequiredPairs.text.toString()
        val forbiddenSlotsStr = binding.etForbiddenSlots.text.toString()
        val requiredStr = binding.etRequired.text.toString()

        // Валидация слотов
        val slots = slotsStr.toIntOrNull()
        if (slots == null || slots <= 0) {
            showError(binding.errSlots, "Введите корректное количество слотов > 0")
            return
        }

        if (slots > 30) {
            showError(binding.errSlots, "Максимум 30 слотов")
            return
        }

        // Парсинг участников
        val participants = participantsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (participants.isEmpty()) {
            showError(binding.errParticipants, "Введите хотя бы одного участника")
            return
        }

        if (participants.size > 12) {
            showError(binding.errParticipants, "Максимум 12 участников")
            return
        }

        // Валидация диапазона смен
        val minShifts = minShiftsStr.toIntOrNull() ?: 0
        val maxShifts = if (maxShiftsStr.isEmpty()) slots else maxShiftsStr.toIntOrNull() ?: slots

        if (minShifts < 0) {
            showError(binding.errShifts, "Минимум смен не может быть отрицательным")
            return
        }

        if (maxShifts < minShifts) {
            showError(binding.errShifts, "Максимум не может быть меньше минимума")
            return
        }

        if (maxShifts > slots) {
            showError(binding.errShifts, "Максимум смен не может превышать количество слотов")
            return
        }

        // Валидация размера слота
        val minSlotSize = minSlotSizeStr.toIntOrNull() ?: 1
        val maxSlotSize = maxSlotSizeStr.toIntOrNull() ?: 2

        if (minSlotSize < 0 || minSlotSize > participants.size) {
            showError(binding.errSlotSize, "Мин. размер слота: от 0 до ${participants.size}")
            return
        }

        if (maxSlotSize < minSlotSize || maxSlotSize > participants.size) {
            showError(binding.errSlotSize, "Макс. размер слота: от $minSlotSize до ${participants.size}")
            return
        }

        // Парсинг запрещённых пар
        val forbiddenPairs = parsePairs(forbiddenPairsStr, participants, binding.errForbiddenPairs) ?: return

        // Парсинг обязательных пар
        val requiredPairs = parsePairs(requiredPairsStr, participants, binding.errRequiredPairs) ?: return

        // Парсинг запрещённых слотов
        val forbiddenSlots = mutableMapOf<String, Set<Int>>()
        if (forbiddenSlotsStr.isNotEmpty()) {
            val entries = forbiddenSlotsStr.split(";").map { it.trim() }
            for (entry in entries) {
                val parts = entry.split(":").map { it.trim() }
                if (parts.size != 2) {
                    showError(binding.errForbiddenSlots, "Неверный формат: '$entry'. Используйте 'Имя: 1,2,3'")
                    return
                }
                val name = parts[0]
                if (!participants.contains(name)) {
                    showError(binding.errForbiddenSlots, "Участник '$name' не найден")
                    return
                }
                val slotNumbers = parts[1].split(",").map { it.trim().toIntOrNull() }
                for (slotNum in slotNumbers) {
                    if (slotNum == null || slotNum < 1 || slotNum > slots) {
                        showError(binding.errForbiddenSlots, "Некорректный номер слота в '$entry'")
                        return
                    }
                }
                forbiddenSlots[name] = slotNumbers.filterNotNull().toSet()
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

        // Запускаем CSP в фоне с таймаутом
        lifecycleScope.launch {
            val result = withTimeoutOrNull(10_000) { // 10 секунд таймаут
                withContext(Dispatchers.Default) {
                    solveCSP(
                        slots = slots,
                        participants = participants,
                        minShifts = minShifts,
                        maxShifts = maxShifts,
                        minSlotSize = minSlotSize,
                        maxSlotSize = maxSlotSize,
                        forbiddenPairs = forbiddenPairs,
                        requiredPairs = requiredPairs,
                        forbiddenSlots = forbiddenSlots,
                        required = required,
                        solutionIndex = solutionCounter
                    )
                }
            } ?: "⏱️ Таймаут: решение не найдено за 10 секунд.\nПопробуйте упростить ограничения."

            binding.btnSolve.isEnabled = true
            binding.btnSolve.text = "Найти решение"

            displayResult(result)
        }
    }

    /**
     * Универсальный парсер пар
     */
    private fun parsePairs(
        input: String,
        participants: List<String>,
        errorView: TextView
    ): Set<Pair<String, String>>? {
        val pairs = mutableSetOf<Pair<String, String>>()
        if (input.isEmpty()) return pairs

        val pairStrings = input.split(",").map { it.trim() }
        for (pairStr in pairStrings) {
            val parts = pairStr.split(" - ").map { it.trim() }
            if (parts.size != 2) {
                showError(errorView, "Неверный формат пары: '$pairStr'. Используйте 'Имя1 - Имя2'")
                return null
            }
            val p1 = parts[0]
            val p2 = parts[1]
            if (!participants.contains(p1)) {
                showError(errorView, "Участник '$p1' не найден")
                return null
            }
            if (!participants.contains(p2)) {
                showError(errorView, "Участник '$p2' не найден")
                return null
            }
            pairs.add(p1 to p2)
            pairs.add(p2 to p1)
        }
        return pairs
    }

    /**
     * Расширенный CSP алгоритм
     */
    private fun solveCSP(
        slots: Int,
        participants: List<String>,
        minShifts: Int,
        maxShifts: Int,
        minSlotSize: Int,
        maxSlotSize: Int,
        forbiddenPairs: Set<Pair<String, String>>,
        requiredPairs: Set<Pair<String, String>>,
        forbiddenSlots: Map<String, Set<Int>>,
        required: List<String>,
        solutionIndex: Int
    ): String {
        val schedule = Array(slots) { mutableListOf<String>() }
        val shiftCount = participants.associateWith { 0 }.toMutableMap()
        val assigned = mutableSetOf<String>()
        var nodesVisited = 0

        /**
         * Проверка валидности назначения участника в слот
         */
        fun isValid(slotIndex: Int, participant: String): Boolean {
            // Проверка максимума смен
            if ((shiftCount[participant] ?: 0) >= maxShifts) return false

            // Проверка запрещённых слотов
            if (slotIndex + 1 in (forbiddenSlots[participant] ?: emptySet())) return false

            // Проверка запрещённых пар в текущем слоте
            for (existing in schedule[slotIndex]) {
                if ((participant to existing) in forbiddenPairs) return false
            }

            return true
        }

        /**
         * Проверка обязательных пар в слоте
         */
        fun checkRequiredPairs(slotIndex: Int): Boolean {
            val current = schedule[slotIndex].toSet()
            for ((p1, p2) in requiredPairs) {
                if (p1 in current && p2 !in current) return false
                if (p2 in current && p1 !in current) return false
            }
            return true
        }

        /**
         * Генерация возможных назначений для слота
         */
        fun generateAssignments(slotIndex: Int): List<List<String>> {
            val result = mutableListOf<List<String>>()
            val available = participants.filter { isValid(slotIndex, it) }

            // Если нужны обязательные участники, которые ещё не назначены
            val remainingRequired = required.filter { it !in assigned }
            if (remainingRequired.isNotEmpty()) {
                for (req in remainingRequired) {
                    if (available.contains(req)) {
                        result.add(listOf(req))
                    }
                }
            }

            // Генерация одиночных назначений
            for (p in available) {
                if (!result.contains(listOf(p))) {
                    result.add(listOf(p))
                }
            }

            // Генерация пар (если размер слота позволяет)
            if (maxSlotSize >= 2 && available.size >= 2) {
                for (i in available.indices) {
                    for (j in i + 1 until available.size) {
                        val p1 = available[i]
                        val p2 = available[j]
                        if ((p1 to p2) !in forbiddenPairs) {
                            result.add(listOf(p1, p2))
                        }
                    }
                }
            }

            // Рандомизация для разнообразия решений
            if (solutionIndex > 0) {
                result.shuffle(Random(solutionIndex))
            }

            return result
        }

        /**
         * Бэктрекинг
         */
        fun backtrack(slotIndex: Int): Boolean {
            nodesVisited++

            // Защита от слишком глубокой рекурсии
            if (nodesVisited > 100_000) return false

            if (slotIndex == slots) {
                // Финальная проверка
                if (!required.all { it in assigned }) return false
                if (shiftCount.values.any { it < minShifts }) return false
                return true
            }

            val assignments = generateAssignments(slotIndex)

            for (assignment in assignments) {
                // Проверка размера слота
                if (assignment.size < minSlotSize || assignment.size > maxSlotSize) continue

                // Проверка обязательных пар
                schedule[slotIndex].addAll(assignment)
                if (!checkRequiredPairs(slotIndex)) {
                    schedule[slotIndex].clear()
                    continue
                }

                // Применяем
                assignment.forEach {
                    shiftCount[it] = (shiftCount[it] ?: 0) + 1
                    assigned.add(it)
                }

                // Рекурсия
                if (backtrack(slotIndex + 1)) {
                    return true
                }

                // Откат
                schedule[slotIndex].clear()
                assignment.forEach {
                    shiftCount[it] = (shiftCount[it] ?: 0) - 1
                    assigned.remove(it)
                }
            }

            return false
        }

        // Предварительная проверка возможности
        val totalMinCapacity = participants.size * minShifts
        val totalMaxCapacity = participants.size * maxShifts
        val totalSlotsNeeded = slots * minSlotSize

        if (totalMaxCapacity < totalSlotsNeeded) {
            return "❌ Решение невозможно: недостаточная ёмкость.\n" +
                   "Требуется минимум: $totalSlotsNeeded назначений\n" +
                   "Доступно максимум: $totalMaxCapacity назначений"
        }

        if (totalMinCapacity > slots * maxSlotSize) {
            return "❌ Решение невозможно: слишком высокий минимум смен.\n" +
                   "Участники должны отработать: $totalMinCapacity смен\n" +
                   "Слотов доступно: ${slots * maxSlotSize}"
        }

        // Запуск
        val success = backtrack(0)

        return if (success) {
            formatSolution(schedule, shiftCount, nodesVisited)
        } else {
            "❌ Решение не найдено (просмотрено узлов: $nodesVisited)\n\n" +
            "Возможные причины:\n" +
            "• Слишком жёсткие ограничения\n" +
            "• Противоречивые требования пар\n" +
            "• Запрещённые слоты делают назначение невозможным"
        }
    }

    private fun formatSolution(
        schedule: Array<MutableList<String>>,
        shiftCount: Map<String, Int>,
        nodesVisited: Int
    ): String {
        return buildString {
            appendLine("✅ РЕШЕНИЕ НАЙДЕНО")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Просмотрено узлов: $nodesVisited")
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
            appendLine("📊 Статистика нагрузки:")
            val sorted = shiftCount.entries.sortedByDescending { it.value }
            val counts = sorted.map { it.value }
            val avg = if (counts.isNotEmpty()) counts.average() else 0.0
            val max = counts.maxOrNull() ?: 0
            val min = counts.minOrNull() ?: 0
            val balance = max - min

            sorted.forEach { (participant, count) ->
                val bar = "█".repeat(count)
                appendLine("  $participant: $count $bar")
            }

            appendLine()
            appendLine("Баланс нагрузки: разница $balance (среднее: ${"%.1f".format(avg)})")

            if (balance <= 1) {
                appendLine("🌟 Отличная балансировка!")
            } else if (balance <= 2) {
                appendLine("👍 Хорошая балансировка")
            } else {
                appendLine("⚠️ Нагрузка неравномерна")
            }
        }
    }

    private fun displayResult(result: String) {
        binding.tvResult.text = result.trimEnd()
        binding.tvResult.visibility = View.VISIBLE
        binding.llResultActions.visibility = View.VISIBLE
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
        binding.etMinShifts.text.clear()
        binding.etMaxShifts.text.clear()
        binding.etMinSlotSize.text.clear()
        binding.etMaxSlotSize.text.clear()
        binding.etForbiddenPairs.text.clear()
        binding.etRequiredPairs.text.clear()
        binding.etForbiddenSlots.text.clear()
        binding.etRequired.text.clear()
        binding.tvResult.visibility = View.GONE
        binding.llResultActions.visibility = View.GONE
        hideErrors()
        solutionCounter = 0
    }

    private fun showError(errorView: TextView, message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideErrors() {
        binding.errSlots.visibility = View.GONE
        binding.errParticipants.visibility = View.GONE
        binding.errShifts.visibility = View.GONE
        binding.errSlotSize.visibility = View.GONE
        binding.errForbiddenPairs.visibility = View.GONE
        binding.errRequiredPairs.visibility = View.GONE
        binding.errForbiddenSlots.visibility = View.GONE
        binding.errRequired.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
