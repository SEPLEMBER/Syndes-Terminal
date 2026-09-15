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
        private const val WALK_STEPS_MULTIPLIER = 2
        private const val ENEMY_BASE_COUNT = 3
        private const val LOOT_COUNT = 3
        private const val ENEMY_KILL_CHANCE = 70

        private const val CHAR_PLAYER = "🧑‍💻"
        private const val CHAR_PLAYER_SHIELD = "🛡️" // Можно изменить на "🧑‍💻🛡️" для различения
        private const val CHAR_WALL = "🧱"
        private const val CHAR_FLOOR = "⬛"
        private const val CHAR_FOG = "░"
        private const val CHAR_ENEMY = "👾"
        private const val CHAR_LOOT = "💾"
        private const val CHAR_HEAL = "💊"
        private const val CHAR_SHIELD = "🛡️"
        private const val CHAR_EXIT = "🚪"
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
            setBackgroundColor(Color.parseColor("#0D1117"))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        tvStats = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00FF00"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
            mainLayout.addView(this)
        }

        tvGrid = TextView(this).apply {
            textSize = 22f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            mainLayout.addView(this)
        }

        TextView(this).apply {
            text = "📟 SYSTEM LOG:"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 16, 0, 4)
            mainLayout.addView(this)
        }

        scrollViewLog = NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            mainLayout.addView(this)
        }

        tvLog = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#58A6FF"))
            setPadding(8, 8, 8, 8)
            scrollViewLog.addView(this)
        }

        val dpadContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
            mainLayout.addView(this)
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; dpadContainer.addView(this) }
        addDpadButton(row1, "⬆️", { processTurn(0, -1) })

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; dpadContainer.addView(this) }
        addDpadButton(row2, "⬅️", { processTurn(-1, 0) })
        addDpadButton(row2, "🔄", { restartGame() }, "Рестарт")
        addDpadButton(row2, "➡️", { processTurn(1, 0) })

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; dpadContainer.addView(this) }
        addDpadButton(row3, "⬇️", { processTurn(0, 1) })

        setContentView(mainLayout)
        log("Система инициализирована. Рекорд: $highScore", Color.parseColor("#00FF00"))
        log("Используйте свайпы или кнопки для перемещения.", Color.parseColor("#8B949E"))
    }

    private fun addDpadButton(container: LinearLayout, text: String, onClick: () -> Unit, label: String? = null) {
        val btn = Button(this).apply {
            this.text = text
            textSize = 20f
            setBackgroundColor(Color.parseColor("#21262D"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(100, 100).apply { setMargins(6, 6, 6, 6) }
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
        log("--- ПЕРЕЗАГРУЗКА СИСТЕМЫ ---", Color.YELLOW)
        render()
    }

    private fun generateLevel() {
        var attempts = 0
        val maxAttempts = 10
        
        do {
            generateLevelInternal()
            attempts++
        } while (!isMapSolvable() && attempts < maxAttempts)
        
        if (!isMapSolvable()) {
            log("⚠️ Карта не проходима. Генерация упрощена.", Color.RED)
            simplifyMap()
        }
        
        updateFog()
        log("Уровень $level загружен. Найдите 🚪 Выход.", Color.parseColor("#00FFFF"))
        updateStats()
    }

    private fun generateLevelInternal() {
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                grid[y][x] = CHAR_WALL[0]
                explored[y][x] = false
            }
        }

        var cx = GRID_SIZE / 2
        var cy = GRID_SIZE / 2
        val steps = GRID_SIZE * GRID_SIZE * WALK_STEPS_MULTIPLIER
        
        for (i in 0 until steps) {
            grid[cy][cx] = CHAR_FLOOR[0]
            when (Random.nextInt(4)) {
                0 -> if (cx < GRID_SIZE - 2) cx++
                1 -> if (cx > 1) cx--
                2 -> if (cy < GRID_SIZE - 2) cy++
                3 -> if (cy > 1) cy--
            }
        }

        val freeCells = mutableListOf<Pair<Int, Int>>()
        for (y in 1 until GRID_SIZE - 1) {
            for (x in 1 until GRID_SIZE - 1) {
                if (grid[y][x] == CHAR_FLOOR[0]) {
                    freeCells.add(Pair(x, y))
                }
            }
        }

        if (freeCells.size < 10) return

        freeCells.shuffle()

        var cellIndex = 0
        
        if (cellIndex < freeCells.size) {
            playerX = freeCells[cellIndex].first
            playerY = freeCells[cellIndex].second
            cellIndex++
        }

        if (cellIndex < freeCells.size) {
            val (ex, ey) = freeCells[cellIndex]
            grid[ey][ex] = CHAR_EXIT[0]
            cellIndex++
        }

        val enemyCount = ENEMY_BASE_COUNT + (level / 2)
        repeat(enemyCount) {
            if (cellIndex < freeCells.size) {
                val (ex, ey) = freeCells[cellIndex]
                grid[ey][ex] = CHAR_ENEMY[0]
                cellIndex++
            }
        }

        repeat(LOOT_COUNT) {
            if (cellIndex < freeCells.size) {
                val (lx, ly) = freeCells[cellIndex]
                grid[ly][lx] = CHAR_LOOT[0]
                cellIndex++
            }
        }

        if (Random.nextBoolean() && cellIndex < freeCells.size) {
            val (hx, hy) = freeCells[cellIndex]
            grid[hy][hx] = CHAR_HEAL[0]
            cellIndex++
        }

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
        for (x in 1 until GRID_SIZE - 1) {
            grid[GRID_SIZE / 2][x] = CHAR_FLOOR[0]
        }
        playerX = 1
        playerY = GRID_SIZE / 2
        grid[GRID_SIZE / 2][GRID_SIZE - 2] = CHAR_EXIT[0]
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
                log("СИСТЕМА ЗАБЛОКИРОВАНА. Нажмите 🔄 для рестарта.", Color.RED)
                return
            }

            val newX = playerX + dx
            val newY = playerY + dy

            if (newX < 0 || newX >= GRID_SIZE || newY < 0 || newY >= GRID_SIZE) return

            val target = grid[newY][newX]

            when (target) {
                CHAR_WALL[0] -> log("⛔ Брандмауэр блокирует путь.", Color.RED)
                
                CHAR_ENEMY[0] -> {
                    log("⚔️ Атака аномалии! Вы наносите 1 урон.", Color.YELLOW)
                    if (Random.nextInt(100) < ENEMY_KILL_CHANCE) {
                        grid[newY][newX] = CHAR_FLOOR[0]
                        score += 50
                        log("✅ Аномалия устранена. +50 очков.", Color.parseColor("#00FF00"))
                        playerX = newX
                        playerY = newY
                    } else {
                        takeDamage(1)
                        log("⚠️ Атака отражена, но вы получили повреждение!", Color.RED)
                    }
                }
                
                CHAR_LOOT[0] -> {
                    playerX = newX
                    playerY = newY
                    score += 100
                    grid[newY][newX] = CHAR_FLOOR[0]
                    log("💾 Пакет данных извлечен. +100 очков.", Color.parseColor("#00FFFF"))
                }
                
                CHAR_HEAL[0] -> {
                    playerX = newX
                    playerY = newY
                    if (hp < maxHp) {
                        hp++
                        log("💊 Системный патч применен. HP восстановлено.", Color.parseColor("#00FF00"))
                    } else {
                        log("💊 Здоровье уже полно. Патч сохранен в кэш (+25 очков).", Color.parseColor("#8B949E"))
                        score += 25
                    }
                    grid[newY][newX] = CHAR_FLOOR[0]
                }

                CHAR_SHIELD[0] -> {
                    playerX = newX
                    playerY = newY
                    shieldActive = true
                    grid[newY][newX] = CHAR_FLOOR[0]
                    log("🛡️ Временный брандмауэр активирован.", Color.parseColor("#00FFFF"))
                }

                CHAR_EXIT[0] -> {
                    level++
                    score += 500
                    shieldActive = false
                    log("🚪 Уровень пройден! Синхронизация...", Color.parseColor("#00FF00"))
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
            log("🛡️ Брандмауэр поглотил урон!", Color.parseColor("#00FFFF"))
            return
        }
        
        hp = maxOf(0, hp - amount)
        
        if (hp <= 0) {
            isGameOver = true
            log("💀 КРИТИЧЕСКИЙ СБОЙ ЯДРА. Игра окончена.", Color.RED)
            if (score > highScore) {
                highScore = score
                prefs.edit().putInt(KEY_HIGH_SCORE, highScore).apply()
                log("🏆 НОВЫЙ РЕКОРД: $highScore", Color.parseColor("#FFD700"))
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
        val hpStr = "❤️".repeat(hp) + "🖤".repeat(maxOf(0, maxHp - hp))
        val shieldStr = if (shieldActive) " 🛡️" else ""
        tvStats.text = "ГЛУБИНА: $level | СЧЕТ: $score (Рек: $highScore)\nЦЕЛОСТНОСТЬ: $hpStr$shieldStr"
    }

    private fun log(message: String, color: Int = Color.parseColor("#58A6FF")) {
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
