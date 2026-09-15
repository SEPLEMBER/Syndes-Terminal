package org.syndes.terminal

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import java.util.ArrayDeque
import java.util.LinkedList
import kotlin.math.abs
import kotlin.random.Random

class SystemRogueActivity : AppCompatActivity() {

    companion object {
        private const val GRID_SIZE = 12
        private const val PREFS_NAME = "SystemRoguePrefs"
        private const val KEY_HIGH_SCORE = "high_score"
        private const val MAX_LOG_LINES = 50
        private const val WALK_STEPS_MULTIPLIER = 3
        private const val ENEMY_BASE_COUNT = 3
        private const val LOOT_COUNT = 3
        private const val ENEMY_KILL_CHANCE = 70

        // Надежные ASCII/Unicode символы вместо эмодзи
        private const val CHAR_PLAYER = "@"
        private const val CHAR_PLAYER_SHIELD = "A"
        private const val CHAR_WALL = "#"
        private const val CHAR_FLOOR = "."
        private const val CHAR_FOG = " "
        private const val CHAR_ENEMY = "E"
        private const val CHAR_LOOT = "$"
        private const val CHAR_HEAL = "+"
        private const val CHAR_SHIELD = "S"
        private const val CHAR_EXIT = ">"
    }

    private lateinit var tvGrid: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLog: TextView
    private lateinit var scrollViewLog: NestedScrollView
    private lateinit var gestureDetector: GestureDetector
    private lateinit var prefs: SharedPreferences

    private var grid = Array(GRID_SIZE) { CharArray(GRID_SIZE) }
    private var explored = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }
    
    private var playerX = 1
    private var playerY = 1
    private var level = 1
    private var score = 0
    private var hp = 3
    private var maxHp = 3
    private var shieldActive = false
    private var highScore = 0
    private var isProcessingTurn = false
    private var isGameOver = false
    
    private val logLines = LinkedList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        highScore = prefs.getInt(KEY_HIGH_SCORE, 0)
        
        gestureDetector = GestureDetector(this, SwipeGestureListener())
        setupUI()
        generateLevel()
        render()
    }

    override fun onDestroy() {
        super.onDestroy()
        logLines.clear()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) return true
        return super.onTouchEvent(event)
    }

    private fun setupUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Статистика
        tvStats = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.GREEN)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
            mainLayout.addView(this)
        }

        // Игровое поле
        tvGrid = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(12, 12, 12, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            mainLayout.addView(this)
        }

        // Легенда
        TextView(this).apply {
            text = "@-Вы  >-Выход  E-Враг  $-Лут  +-Лечение  S-Щит"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 4)
            mainLayout.addView(this)
        }

        // Лог событий
        TextView(this).apply {
            text = "SYSTEM LOG:"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.CYAN)
            setPadding(0, 8, 0, 2)
            mainLayout.addView(this)
        }

        scrollViewLog = NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            ).apply { setMargins(0, 0, 0, 8) }
            mainLayout.addView(this)
        }

        tvLog = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00FFFF")) // Яркий циан
            setPadding(4, 4, 4, 4)
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            scrollViewLog.addView(this)
        }

        // Кнопки управления - более компактные
        val dpadContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            mainLayout.addView(this)
        }

        // Верхний ряд
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            dpadContainer.addView(this)
        }
        addDpadButton(row1, "↑", { processTurn(0, -1) })

        // Средний ряд
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            dpadContainer.addView(this)
        }
        addDpadButton(row2, "←", { processTurn(-1, 0) })
        addDpadButton(row2, "↻", { restartGame() })
        addDpadButton(row2, "→", { processTurn(1, 0) })

        // Нижний ряд
        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            dpadContainer.addView(this)
        }
        addDpadButton(row3, "↓", { processTurn(0, 1) })

        setContentView(mainLayout)
        log("Система инициализирована. Рекорд: $highScore", Color.GREEN)
        log("Свайпы или кнопки для перемещения", Color.GRAY)
    }

    private fun addDpadButton(container: LinearLayout, text: String, onClick: () -> Unit) {
        val btn = Button(this).apply {
            this.text = text
            textSize = 18f
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(90, 90).apply { setMargins(4, 4, 4, 4) }
            setOnClickListener { onClick() }
        }
        container.addView(btn)
    }

    private fun restartGame() {
        level = 1
        score = 0
        hp = maxHp
        shieldActive = false
        isGameOver = false
        isProcessingTurn = false
        logLines.clear()
        tvLog.text = ""
        generateLevel()
        log("=== ПЕРЕЗАГРУЗКА ===", Color.YELLOW)
        render()
    }

    private fun generateLevel() {
        var attempts = 0
        val maxAttempts = 20
        
        do {
            generateLevelInternal()
            attempts++
        } while (!isMapSolvable() && attempts < maxAttempts)
        
        if (!isMapSolvable()) {
            log("Генерация упрощена", Color.RED)
            simplifyMap()
        }
        
        updateFog()
        log("Уровень $level. Найдите выход (>)", Color.CYAN)
        updateStats()
    }

    private fun generateLevelInternal() {
        // Заполнение стенами
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                grid[y][x] = CHAR_WALL[0]
                explored[y][x] = false
            }
        }

        // Random Walk для создания проходов
        var cx = GRID_SIZE / 2
        var cy = GRID_SIZE / 2
        val steps = GRID_SIZE * GRID_SIZE * WALK_STEPS_MULTIPLIER
        
        for (i in 0 until steps) {
            grid[cy][cx] = CHAR_FLOOR[0]
            when (Random.nextInt(4)) {
                0 -> if (cx < GRID_SIZE - 1) cx++
                1 -> if (cx > 0) cx--
                2 -> if (cy < GRID_SIZE - 1) cy++
                3 -> if (cy > 0) cy--
            }
        }

        // Сбор всех свободных клеток
        val freeCells = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                if (grid[y][x] == CHAR_FLOOR[0]) {
                    freeCells.add(Pair(x, y))
                }
            }
        }

        if (freeCells.size < 20) return

        freeCells.shuffle()

        var cellIndex = 0
        
        // Игрок
        if (cellIndex < freeCells.size) {
            playerX = freeCells[cellIndex].first
            playerY = freeCells[cellIndex].second
            cellIndex++
        }

        // Выход
        if (cellIndex < freeCells.size) {
            val (ex, ey) = freeCells[cellIndex]
            grid[ey][ex] = CHAR_EXIT[0]
            cellIndex++
        }

        // Враги
        val enemyCount = ENEMY_BASE_COUNT + (level / 2)
        repeat(enemyCount) {
            if (cellIndex < freeCells.size) {
                val (ex, ey) = freeCells[cellIndex]
                grid[ey][ex] = CHAR_ENEMY[0]
                cellIndex++
            }
        }

        // Лут
        repeat(LOOT_COUNT) {
            if (cellIndex < freeCells.size) {
                val (lx, ly) = freeCells[cellIndex]
                grid[ly][lx] = CHAR_LOOT[0]
                cellIndex++
            }
        }

        // Лечение
        if (Random.nextBoolean() && cellIndex < freeCells.size) {
            val (hx, hy) = freeCells[cellIndex]
            grid[hy][hx] = CHAR_HEAL[0]
            cellIndex++
        }

        // Щит
        if (Random.nextBoolean() && level > 1 && cellIndex < freeCells.size) {
            val (sx, sy) = freeCells[cellIndex]
            grid[sy][sx] = CHAR_SHIELD[0]
            cellIndex++
        }
    }

    private fun isMapSolvable(): Boolean {
        val visited = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }
        val queue = ArrayDeque<Pair<Int, Int>>()
        
        queue.add(Pair(playerX, playerY))
        visited[playerY][playerX] = true
        
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            
            if (grid[y][x] == CHAR_EXIT[0]) return true
            
            val directions = arrayOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))
            for ((dx, dy) in directions) {
                val nx = x + dx
                val ny = y + dy
                
                if (nx in 0 until GRID_SIZE && ny in 0 until GRID_SIZE && 
                    !visited[ny][nx] && grid[ny][nx] != CHAR_WALL[0]) {
                    visited[ny][nx] = true
                    queue.add(Pair(nx, ny))
                }
            }
        }
        
        return false
    }

    private fun simplifyMap() {
        // Простой коридор через центр
        for (x in 0 until GRID_SIZE) {
            grid[GRID_SIZE / 2][x] = CHAR_FLOOR[0]
        }
        playerX = 0
        playerY = GRID_SIZE / 2
        grid[GRID_SIZE / 2][GRID_SIZE - 1] = CHAR_EXIT[0]
    }

    private fun updateFog() {
        for (dy in -1..1) {
            for (dx in -1..1) {
                val nx = playerX + dx
                val ny = playerY + dy
                if (nx in 0 until GRID_SIZE && ny in 0 until GRID_SIZE) {
                    explored[ny][nx] = true
                }
            }
        }
    }

    private fun processTurn(dx: Int, dy: Int) {
        if (isProcessingTurn || isGameOver) return
        
        isProcessingTurn = true
        
        try {
            if (hp <= 0) {
                isGameOver = true
                log("СБОЙ. Нажмите ↻", Color.RED)
                return
            }

            val newX = playerX + dx
            val newY = playerY + dy

            if (newX < 0 || newX >= GRID_SIZE || newY < 0 || newY >= GRID_SIZE) return

            val target = grid[newY][newX]

            when (target) {
                CHAR_WALL[0] -> log("Стена", Color.RED)
                
                CHAR_ENEMY[0] -> {
                    log("Атака!", Color.YELLOW)
                    if (Random.nextInt(100) < ENEMY_KILL_CHANCE) {
                        grid[newY][newX] = CHAR_FLOOR[0]
                        score += 50
                        log("Враг уничтожен +50", Color.GREEN)
                        playerX = newX
                        playerY = newY
                    } else {
                        takeDamage(1)
                        log("Урон! HP: $hp", Color.RED)
                    }
                }
                
                CHAR_LOOT[0] -> {
                    playerX = newX
                    playerY = newY
                    score += 100
                    grid[newY][newX] = CHAR_FLOOR[0]
                    log("Данные +100", Color.CYAN)
                }
                
                CHAR_HEAL[0] -> {
                    playerX = newX
                    playerY = newY
                    if (hp < maxHp) {
                        hp++
                        log("Лечение HP: $hp", Color.GREEN)
                    } else {
                        log("HP полно +25", Color.GRAY)
                        score += 25
                    }
                    grid[newY][newX] = CHAR_FLOOR[0]
                }

                CHAR_SHIELD[0] -> {
                    playerX = newX
                    playerY = newY
                    shieldActive = true
                    grid[newY][newX] = CHAR_FLOOR[0]
                    log("Щит активен", Color.CYAN)
                }

                CHAR_EXIT[0] -> {
                    level++
                    score += 500
                    shieldActive = false
                    log("Уровень пройден! +500", Color.GREEN)
                    generateLevel()
                    return
                }

                CHAR_FLOOR[0] -> {
                    playerX = newX
                    playerY = newY
                }
            }

            updateFog()
            updateStats()
            render()
        } finally {
            isProcessingTurn = false
        }
    }

    private fun takeDamage(amount: Int) {
        if (shieldActive) {
            shieldActive = false
            log("Щит поглотил урон", Color.CYAN)
            return
        }
        
        hp = maxOf(0, hp - amount)
        
        if (hp <= 0) {
            isGameOver = true
            log("КРИТИЧЕСКИЙ СБОЙ", Color.RED)
            if (score > highScore) {
                highScore = score
                prefs.edit().putInt(KEY_HIGH_SCORE, highScore).apply()
                log("НОВЫЙ РЕКОРД: $highScore", Color.YELLOW)
            }
        }
    }

    private fun render() {
        val sb = StringBuilder()
        val playerChar = if (shieldActive) CHAR_PLAYER_SHIELD else CHAR_PLAYER
        
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                if (x == playerX && y == playerY) {
                    sb.append(playerChar)
                } else if (!explored[y][x]) {
                    sb.append(CHAR_FOG)
                } else {
                    sb.append(grid[y][x])
                }
                sb.append(" ")
            }
            sb.append("\n")
        }
        tvGrid.text = sb.toString().trimEnd()
    }

    private fun updateStats() {
        val hpStr = "♥".repeat(hp) + "♡".repeat(maxOf(0, maxHp - hp))
        val shieldStr = if (shieldActive) " [S]" else ""
        tvStats.text = "LVL:$level SCORE:$score REC:$highScore\nHP:$hpStr$shieldStr"
    }

    private fun log(message: String, color: Int = Color.CYAN) {
        if (isFinishing || isDestroyed) return
        
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val newLine = "[$time] $message"
        
        logLines.addLast(newLine)
        
        while (logLines.size > MAX_LOG_LINES) {
            logLines.removeFirst()
        }
        
        tvLog.text = logLines.joinToString("\n")
        
        scrollViewLog.post {
            if (!isFinishing && !isDestroyed) {
                scrollViewLog.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null || isProcessingTurn || isGameOver) return false
            
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            
            val minSwipeDistance = 30
            
            if (abs(dx) > abs(dy)) {
                if (abs(dx) > minSwipeDistance) {
                    processTurn(if (dx > 0) 1 else -1, 0)
                }
            } else {
                if (abs(dy) > minSwipeDistance) {
                    processTurn(0, if (dy > 0) 1 else -1)
                }
            }
            return true
        }
    }
}
