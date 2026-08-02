package org.syndes.terminal

import android.app.PendingIntent // <-- ВЕРНУТ (нужен для шорткатов)
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext // <-- ВЕРНУТ (для гарантии в sleep)

class MainActivity : AppCompatActivity() {
    private lateinit var terminalOutput: TextView
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var progressRow: LinearLayout
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    
    private var progressJob: Job? = null
    private var watchdogJob: Job? = null // <-- Управление таймером watchdog
    
    private val terminal = Terminal()
    private val terminal2 = Terminal2()
    private val PREFS_NAME = "terminal_prefs"
    
    // Флаг видимости приложения (учитывает сворачивание, выключение экрана и т.д.)
    private var isAppVisible = false

    private val heavyCommands = setOf(
        "rm", "cp", "mv", "replace", "cmp", "diff",
        "rename", "backup", "snapshot", "trash", "cleartrash",
        "sha256", "grep", "batchrename", "md5", "delete all y"
    )

    private val commandQueue = ArrayDeque<CommandItem>()
    private var processingJob: Job? = null
    private var processingQueue = false
    private val backgroundJobs = mutableListOf<Job>()
    private var pendingIntentCompletion: CompletableDeferred<Unit>? = null
    
    private val intentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingIntentCompletion?.complete(Unit)
        pendingIntentCompletion = null
    }
    
    private var stopQueueButton: Button? = null
    
    private val EXTRA_SHORTCUT_CMD = "shortcut_cmd"
    private val EXTRA_SHORTCUT_LABEL = "shortcut_label"
    private val ACTION_RUN_SHORTCUT = "org.syndes.terminal.RUN_SHORTCUT"
    private val PREF_KEY_BOOT_SHELL = "bootshell_cmds"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySecureFlagFromPrefs()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_main)
        
        terminalOutput = findViewById(R.id.terminalOutput)
        inputField = findViewById(R.id.inputField)
        sendButton = findViewById(R.id.sendButton)
        progressRow = findViewById(R.id.progressRow)
        progressText = findViewById(R.id.progressText)
        progressBar = findViewById(R.id.progressBar)
        
        terminalOutput.movementMethod = ScrollingMovementMethod()
        
        val infoColor = ContextCompat.getColor(this, R.color.color_info)
        appendToTerminal(colorize("Welcome to Syndes Terminal!\nType 'help' to see commands.\n\n", infoColor), infoColor)
        
        sendButton.text = "RUN"
        val embeddedYellow = Color.parseColor("#FFaf12ed")
        sendButton.setTextColor(embeddedYellow)
        sendButton.setBackgroundColor(Color.TRANSPARENT)
        
        addStopQueueButton()
        
        inputField.isFocusable = true
        inputField.isFocusableInTouchMode = true
        
        sendButton.setOnClickListener { sendCommand() }
        inputField.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                sendCommand()
                true
            } else {
                false
            }
        }
        
        terminalOutput.setOnClickListener { focusAndShowKeyboard() }
        
        inputField.post { inputField.requestFocus() }
        
        handleIncomingIntent(intent)
        checkBootShellOnStart()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isAppVisible = true // <-- Приложение на экране
        applySecureFlagFromPrefs()
    }

    override fun onPause() {
        super.onPause()
        isAppVisible = false // <-- Приложение ушло в фон или экран погашен
    }

    override fun onDestroy() {
        super.onDestroy()
        hideProgress()
        watchdogJob?.cancel() // <-- Гарантированная очистка таймера при закрытии
        backgroundJobs.forEach { it.cancel() }
        backgroundJobs.clear()
        processingJob?.cancel()
        pendingIntentCompletion?.completeExceptionally(CancellationException("activity destroyed"))
        pendingIntentCompletion = null
    }

    private fun handleIncomingIntent(intent: Intent?) {
        try {
            if (intent == null) return
            val cmdFromExtra = intent.getStringExtra(EXTRA_SHORTCUT_CMD)
            if (!cmdFromExtra.isNullOrBlank()) {
                runOnUiThread {
                    appendToTerminal(colorize("\n[shortcut] running: $cmdFromExtra\n", ContextCompat.getColor(this, R.color.color_info)), ContextCompat.getColor(this, R.color.color_info))
                    inputField.setText(cmdFromExtra)
                    inputField.setSelection(inputField.text.length)
                    sendCommand()
                }
                return
            }
            if (intent.action == ACTION_RUN_SHORTCUT) {
                val cmd = intent.getStringExtra(EXTRA_SHORTCUT_CMD)
                if (!cmd.isNullOrBlank()) {
                    runOnUiThread {
                        appendToTerminal(colorize("\n[shortcut] running: $cmd\n", ContextCompat.getColor(this, R.color.color_info)), ContextCompat.getColor(this, R.color.color_info))
                        inputField.setText(cmd)
                        inputField.setSelection(inputField.text.length)
                        sendCommand()
                    }
                }
            }
        } catch (_: Throwable) { /* ignore */ }
    }

    private fun sendCommand() {
        val rawInput = inputField.text.toString()
        if (rawInput.isBlank()) {
            focusAndShowKeyboard()
            return
        }
        val inputColor = ContextCompat.getColor(this, R.color.color_command)
        val items = parseInputToCommandItems(rawInput)
        for (item in items) {
            when (item) {
                is CommandItem.Single -> {
                    commandQueue.addLast(item)
                    appendToTerminal(colorize("\n> ${item.command}${if (item.background) " &" else ""}\n", inputColor), inputColor)
                }
                is CommandItem.Parallel -> {
                    commandQueue.addLast(item)
                    appendToTerminal(colorize("\n> parallel { ${item.commands.joinToString(" ; ")} }\n", inputColor), inputColor)
                }
            }
        }
        inputField.text.clear()
        focusAndShowKeyboard()
        if (!processingQueue) processCommandQueue()
    }

    private fun addStopQueueButton() {
        try {
            if (stopQueueButton != null) return
            val btn = Button(this).apply {
                text = "STOP QUEUE"
                setTextColor(Color.parseColor("#FF5F1F"))
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { stopQueue() }
                visibility = View.GONE
            }
            stopQueueButton = btn
            val parent = sendButton.parent
            if (parent is ViewGroup) {
                val idx = parent.indexOfChild(sendButton)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 16 }
                parent.addView(btn, idx, lp)
            } else {
                val root = findViewById<ViewGroup>(android.R.id.content)
                val flp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply { topMargin = 8; leftMargin = 8 }
                root.addView(btn, flp)
            }
        } catch (_: Throwable) { /* ignore */ }
    }

    private fun stopQueue() {
        commandQueue.clear()
        processingJob?.cancel(CancellationException("stopped by user"))
        processingJob = null
        processingQueue = false
        try { pendingIntentCompletion?.completeExceptionally(CancellationException("stopped by user")) } catch (_: Throwable) { /* ignore */ }
        pendingIntentCompletion = null
        backgroundJobs.forEach { it.cancel(CancellationException("stopped by user")) }
        backgroundJobs.clear()
        
        val infoColor = ContextCompat.getColor(this, R.color.color_info)
        appendToTerminal(colorize("\n[STOP] queue stopped and cleared\n", infoColor), infoColor)
        scrollToBottom()
        hideProgress()
        stopQueueButton?.visibility = View.GONE
    }

    private fun processCommandQueue() {
        processingQueue = true
        runOnUiThread { stopQueueButton?.visibility = View.VISIBLE }
        processingJob = lifecycleScope.launch {
            while (commandQueue.isNotEmpty() && isActive) {
                val item = commandQueue.removeFirst()
                try {
                    when (item) {
                        is CommandItem.Single -> {
                            if (item.background) {
                                val bgJob = lifecycleScope.launch {
                                    try { runSingleCommand(item.command) } catch (_: Throwable) { /* ignore */ }
                                }
                                backgroundJobs.add(bgJob)
                            } else {
                                runSingleCommand(item.command)
                            }
                        }
                        is CommandItem.Parallel -> {
                            val hasIntentCommands = item.commands.any { it.trim().split("\\s+".toRegex()).firstOrNull()?.lowercase() in setOf("uninstall") }
                            if (hasIntentCommands) {
                                val err = ContextCompat.getColor(this@MainActivity, R.color.color_error)
                                withContext(Dispatchers.Main) {
                                    appendToTerminal(colorize("Error: cannot run uninstall or intent-based commands in parallel group. Skipping parallel group.\n", err), err)
                                }
                                continue
                            }
                            val deferredJobs = item.commands.map { cmd ->
                                lifecycleScope.launch {
                                    try { runSingleCommand(cmd) } catch (t: Throwable) {
                                        val err = ContextCompat.getColor(this@MainActivity, R.color.color_error)
                                        withContext(Dispatchers.Main) {
                                            appendToTerminal(colorize("Error (parallel): ${t.message}\n", err), err)
                                        }
                                    }
                                }
                            }
                            deferredJobs.joinAll()
                        }
                    }
                } catch (t: Throwable) {
                    val errorColor = ContextCompat.getColor(this@MainActivity, R.color.color_error)
                    withContext(Dispatchers.Main) {
                        appendToTerminal(colorize("Error: failed to execute item : ${t.message}\n", errorColor), errorColor)
                    }
                }
            }
            processingQueue = false
            processingJob = null
            withContext(Dispatchers.Main) { stopQueueButton?.visibility = View.GONE }
        }
    }

    private fun executeWithFallback(command: String, context: Context): String? {
        val result1 = terminal.execute(command, context)
        if (result1 == null || result1.startsWith("Unknown command", ignoreCase = true)) {
            return terminal2.execute(command, context)
        }
        return result1
    }

    private suspend fun runSingleCommand(command: String): String? {
        val inputToken = command.split("\\s+".toRegex()).firstOrNull()?.lowercase() ?: ""
        val defaultColor = ContextCompat.getColor(this@MainActivity, R.color.terminal_text)
        val infoColor = ContextCompat.getColor(this, R.color.color_info)
        val errorColor = ContextCompat.getColor(this, R.color.color_error)
        val systemYellow = Color.parseColor("#FFD54F")

        // ==== WATCHDOG: Полностью внутри приложения, без Service ====
        if (inputToken == "watchdog") {
            val parts = command.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (parts.size < 3) {
                withContext(Dispatchers.Main) {
                    appendToTerminal(colorize("Usage: watchdog <duration> <command...>\nExample: watchdog 5m echo Hello\n", errorColor), errorColor)
                }
                return "Error: invalid watchdog syntax"
            }
            val durToken = parts[1]
            val targetCmd = parts.drop(2).joinToString(" ")
            val durSec = parseDurationToSeconds(durToken)
            
            if (durSec <= 0L) {
                withContext(Dispatchers.Main) {
                    appendToTerminal(colorize("Error: invalid duration '$durToken'\n", errorColor), errorColor)
                }
                return "Error: invalid duration"
            }

            watchdogJob?.cancel()
            
            withContext(Dispatchers.Main) {
                appendToTerminal(colorize("Watchdog scheduled: will run \"$targetCmd\" in ${formatWatchdogTime(durSec)}\n", infoColor), infoColor)
                progressRow.visibility = View.VISIBLE
                progressBar.isIndeterminate = true
                progressText.text = "watchdog timer ${formatWatchdogTime(durSec)}"
            }

            watchdogJob = lifecycleScope.launch {
                var remaining = durSec
                try {
                    while (remaining > 0 && isActive) {
                        delay(1000L)
                        remaining--
                        withContext(Dispatchers.Main) {
                            progressText.text = "watchdog timer ${formatWatchdogTime(remaining)}"
                        }
                    }

                    if (isActive) {
                        withContext(Dispatchers.Main) {
                            progressText.text = "watchdog timer: executing..."
                        }
                        
                        // ГЛАВНОЕ УСЛОВИЕ: Ждём, пока приложение не станет видимым на экране
                        while (!isAppVisible && isActive) {
                            delay(200L)
                        }

                        if (isActive) {
                            withContext(Dispatchers.Main) {
                                hideProgress()
                                appendToTerminal(colorize("\n[watchdog] executing now: $targetCmd\n", infoColor), infoColor)
                                commandQueue.addLast(CommandItem.Single(targetCmd, conditionalNext = false, background = false))
                                if (!processingQueue) processCommandQueue()
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    withContext(Dispatchers.Main) {
                        hideProgress()
                        appendToTerminal(colorize("\n[watchdog] cancelled\n", errorColor), errorColor)
                    }
                }
            }
            return "Info: watchdog scheduled"
        }

        if (inputToken == "act") {
            val parts = command.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (parts.size < 2) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Usage: act <package> [<activity>]\n", errorColor), errorColor) }
                return "Error: act usage"
            }
            val pkg = parts[1].trim()
            val activityPart = if (parts.size >= 3) parts.drop(2).joinToString(" ").trim() else null
            try {
                val launchIntent: Intent? = when {
                    activityPart.isNullOrBlank() -> packageManager.getLaunchIntentForPackage(pkg)
                    else -> {
                        var actName = activityPart
                        if (actName.startsWith("/")) actName = actName.removePrefix("/")
                        if (actName.startsWith(".")) actName = "$pkg$actName"
                        else if (actName.contains("/")) {
                            actName = actName.substringAfter('/')
                            if (actName.startsWith(".")) actName = "$pkg$actName"
                        }
                        Intent().apply { setClassName(pkg, actName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    }
                }
                if (launchIntent == null) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: cannot resolve activity for package '$pkg'\n", errorColor), errorColor) }
                    return "Error: cannot resolve activity"
                }
                try {
                    withContext(Dispatchers.Main) {
                        startActivity(launchIntent)
                        appendToTerminal(colorize("Launched activity for package $pkg\n", infoColor), infoColor)
                    }
                    return "Info: activity launched"
                } catch (se: SecurityException) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: SecurityException when launching activity\n", errorColor), errorColor) }
                    return "Error: security"
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: failed to launch activity: ${t.message}\n", errorColor), errorColor) }
                    return "Error: launch failed"
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: act failed: ${t.message}\n", errorColor), errorColor) }
                return "Error: act"
            }
        }

        if (inputToken == "shortc") {
            val parts = command.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (parts.size < 3) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Usage: shortc <label> <command...>\n", errorColor), errorColor) }
                return "Error: shortc usage"
            }
            val label = parts[1]
            val cmd = parts.drop(2).joinToString(" ")
            try {
                val target = Intent(this, MainActivity::class.java).apply {
                    action = ACTION_RUN_SHORTCUT
                    putExtra(EXTRA_SHORTCUT_CMD, cmd)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val sm = getSystemService(ShortcutManager::class.java)
                    if (sm != null) {
                        val id = "syd_shortcut_${System.currentTimeMillis()}"
                        val info = ShortcutInfo.Builder(this, id).setShortLabel(label).setIntent(target).build()
                        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                        val confirmationIntent = PendingIntent.getBroadcast(this, 0, Intent(), piFlags)
                        sm.requestPinShortcut(info, confirmationIntent.intentSender)
                        withContext(Dispatchers.Main) { appendToTerminal(colorize("Shortcut requested: $label\n", infoColor), infoColor) }
                        return "Info: shortcut requested"
                    }
                }
                val install = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                    putExtra(Intent.EXTRA_SHORTCUT_INTENT, target)
                    putExtra(Intent.EXTRA_SHORTCUT_NAME, label)
                    putExtra("duplicate", false)
                }
                sendBroadcast(install)
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Broadcasted shortcut install (legacy).\n", infoColor), infoColor) }
                return "Info: shortcut broadcasted"
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: cannot create shortcut: ${t.message}\n", errorColor), errorColor) }
                return "Error: shortc failed"
            }
        }

        if (inputToken == "bootshell") {
            withContext(Dispatchers.Main) { showBootShellOverlay() }
            return "Info: bootshell opened"
        }

        if (inputToken == "sleep") {
            val parts = command.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (parts.size < 2) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Usage: sleep <duration>. Examples: sleep 5s | sleep 200ms | sleep 2m\n", errorColor), errorColor) }
                return "Error: sleep usage"
            }
            val durTok = parts[1]
            val millis = parseDurationToMillis(durTok)
            if (millis <= 0L) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: invalid duration '$durTok'\n", errorColor), errorColor) }
                return "Error: invalid duration"
            }
            withContext(Dispatchers.Main) { appendToTerminal(colorize("...\n", infoColor), infoColor) }
            var remaining = millis
            val chunk = 500L
            while (remaining > 0 && coroutineContext.isActive) {
                val to = if (remaining > chunk) chunk else remaining
                delay(to)
                remaining -= to
            }
            withContext(Dispatchers.Main) { appendToTerminal(colorize("...\n", infoColor), infoColor) }
            return "Info: slept ${durTok}"
        }

        if (inputToken == "runsyd") {
            val parts = command.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (parts.size < 2) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Usage: runsyd <name>\n", errorColor), errorColor) }
                return "Error: runsyd usage"
            }
            val name = parts[1].trim()
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val safRoot = prefs.getString("work_dir_uri", null) ?: prefs.getString("current_dir_uri", null)
            if (safRoot.isNullOrBlank()) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: SAF root not configured.\n", errorColor), errorColor) }
                return "Error: saf not configured"
            }
            try {
                val tree = DocumentFile.fromTreeUri(this, Uri.parse(safRoot))
                if (tree == null || !tree.isDirectory) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: cannot access SAF root\n", errorColor), errorColor) }
                    return "Error: saf root invalid"
                }
                val scriptsDir = tree.findFile("scripts")?.takeIf { it.isDirectory }
                if (scriptsDir == null) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: 'scripts' folder not found\n", errorColor), errorColor) }
                    return "Error: scripts folder missing"
                }
                val candidates = if (name.contains('.')) listOf(name) else listOf("$name.syd", "$name.sh", "$name.txt")
                var found: DocumentFile? = null
                for (c in candidates) {
                    val f = scriptsDir.findFile(c)
                    if (f != null && f.isFile) { found = f; break }
                }
                if (found == null) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: script not found\n", errorColor), errorColor) }
                    return "Error: scripts not found"
                }
                val foundFile = found
                val uri = foundFile.uri
                val sb = StringBuilder()
                contentResolver.openInputStream(uri)?.use { ins ->
                    BufferedReader(InputStreamReader(ins)).use { br ->
                        var line: String?
                        while (br.readLine().also { line = it } != null) { sb.append(line).append('\n') }
                    }
                } ?: run {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: cannot open script file\n", errorColor), errorColor) }
                    return "Error: cannot open"
                }
                val content = sb.toString().trimEnd()
                withContext(Dispatchers.Main) {
                    inputField.setText(content)
                    inputField.setSelection(inputField.text.length)
                    appendToTerminal(colorize("Loaded script '${foundFile.name}' — injecting commands...\n", infoColor), infoColor)
                    sendCommand()
                }
                return "Info: runsyd loaded ${foundFile.name}"
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: failed to read script: ${t.message}\n", errorColor), errorColor) }
                return "Error: runsyd failed"
            }
        }

        if (inputToken == "sydcheck") {
            val parts = command.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (parts.size < 2) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Usage: sydcheck <name>\n", errorColor), errorColor) }
                return "Error: sydcheck usage"
            }
            val name = parts[1].trim()
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val safRoot = prefs.getString("work_dir_uri", null) ?: prefs.getString("current_dir_uri", null)
            if (safRoot.isNullOrBlank()) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: SAF root not configured.\n", errorColor), errorColor) }
                return "Error: saf not configured"
            }
            try {
                val tree = DocumentFile.fromTreeUri(this, Uri.parse(safRoot))
                if (tree == null || !tree.isDirectory) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: cannot access SAF root\n", errorColor), errorColor) }
                    return "Error: saf root invalid"
                }
                val scriptsDir = tree.findFile("scripts")?.takeIf { it.isDirectory }
                if (scriptsDir == null) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: 'scripts' folder not found\n", errorColor), errorColor) }
                    return "Error: scripts folder missing"
                }
                val candidates = if (name.contains('.')) listOf(name) else listOf("$name.syd", "$name.sh", "$name.txt")
                var found: DocumentFile? = null
                for (c in candidates) {
                    val f = scriptsDir.findFile(c)
                    if (f != null && f.isFile) { found = f; break }
                }
                if (found == null) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: script not found\n", errorColor), errorColor) }
                    return "Error: scripts not found"
                }
                val foundFile = found
                val uri = foundFile.uri
                val sb = StringBuilder()
                contentResolver.openInputStream(uri)?.use { ins ->
                    BufferedReader(InputStreamReader(ins)).use { br ->
                        var line: String?
                        while (br.readLine().also { line = it } != null) { sb.append(line).append('\n') }
                    }
                } ?: run {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: cannot open script file\n", errorColor), errorColor) }
                    return "Error: cannot open"
                }
                val content = sb.toString().trimEnd()
                val items = parseInputToCommandItems(content)
                val suspiciousPrefixes = setOf("rm", "pm", "runsyd")
                val matches = mutableListOf<String>()
                var idx = 0
                for (it in items) {
                    when (it) {
                        is CommandItem.Single -> {
                            idx++
                            val firstTok = it.command.trim().split("\\s+".toRegex()).firstOrNull()?.lowercase() ?: ""
                            if (suspiciousPrefixes.any { p -> firstTok.startsWith(p) }) matches.add("[$idx] ${it.command}")
                        }
                        is CommandItem.Parallel -> {
                            for (c in it.commands) {
                                idx++
                                val firstTok = c.trim().split("\\s+".toRegex()).firstOrNull()?.lowercase() ?: ""
                                if (suspiciousPrefixes.any { p -> firstTok.startsWith(p) }) matches.add("[$idx] (parallel) $c")
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    if (matches.isEmpty()) {
                        appendToTerminal(colorize("sydcheck: no suspicious commands found in '${foundFile.name}'\n", infoColor), infoColor)
                    } else {
                        appendToTerminal(colorize("sydcheck: suspicious commands found in '${foundFile.name}':\n", errorColor), errorColor)
                        for (m in matches) appendToTerminal(colorize("  $m\n", errorColor), errorColor)
                        appendToTerminal(colorize("Warning: these commands may be destructive. Review before running.\n", errorColor), errorColor)
                    }
                }
                return "Info: sydcheck completed"
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: sydcheck failed: ${t.message}\n", errorColor), errorColor) }
                return "Error: sydcheck failed"
            }
        }

        if (inputToken == "sydc") {
            val parts = command.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (parts.size < 3) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Usage: sydc <filename> <text>\n", errorColor), errorColor) }
                return "Error: sydc usage"
            }
            val filename = parts[1].trim()
            val prefix = parts[0] + " " + parts[1]
            val content = command.substringAfter(prefix, "").trim()
            if (content.isEmpty()) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: sydc: empty content\n", errorColor), errorColor) }
                return "Error: sydc empty content"
            }
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val safRoot = prefs.getString("work_dir_uri", null) ?: prefs.getString("current_dir_uri", null)
            if (safRoot.isNullOrBlank()) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: SAF root not configured.\n", errorColor), errorColor) }
                return "Error: saf not configured"
            }
            try {
                val tree = DocumentFile.fromTreeUri(this, Uri.parse(safRoot))
                if (tree == null || !tree.isDirectory) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: cannot access SAF root\n", errorColor), errorColor) }
                    return "Error: saf root invalid"
                }
                var scriptsDir = tree.findFile("scripts")?.takeIf { it.isDirectory }
                if (scriptsDir == null) {
                    scriptsDir = try { tree.createDirectory("scripts") } catch (_: Throwable) { null }
                }
                if (scriptsDir == null) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: cannot create or access 'scripts' folder\n", errorColor), errorColor) }
                    return "Error: scripts folder missing"
                }
                val scriptsDirFinal = scriptsDir
                val existing = scriptsDirFinal.findFile(filename)
                if (existing != null && existing.isFile) {
                    try { existing.delete() } catch (_: Throwable) { /* ignore */ }
                }
                val newFile = scriptsDirFinal.createFile("text/plain", filename)
                if (newFile == null) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: cannot create file '$filename'\n", errorColor), errorColor) }
                    return "Error: cannot create file"
                }
                try {
                    contentResolver.openOutputStream(newFile.uri)?.use { out ->
                        OutputStreamWriter(out, Charsets.UTF_8).use { ow -> ow.write(content); ow.flush() }
                    } ?: run {
                        withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: cannot open output stream\n", errorColor), errorColor) }
                        return "Error: cannot open output"
                    }
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("sydc: created '${newFile.name}' in scripts folder\n", infoColor), infoColor) }
                    return "Info: sydc created ${newFile.name}"
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: failed to write file: ${t.message}\n", errorColor), errorColor) }
                    return "Error: write failed"
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: sydc failed: ${t.message}\n", errorColor), errorColor) }
                return "Error: sydc failed"
            }
        }

        if (inputToken == "random") {
            val afterBrace = command.substringAfter('{', "").substringBefore('}', "")
            if (afterBrace.isBlank()) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Usage: random {cmd1-cmd2-cmd3}\n", errorColor), errorColor) }
                return "Error: random usage"
            }
            val options = afterBrace.split('-').map { it.trim() }.filter { it.isNotEmpty() }
            if (options.isEmpty()) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: no options found inside {}\n", errorColor), errorColor) }
                return "Error: random no options"
            }
            val idx = kotlin.random.Random.nextInt(options.size)
            val chosen = options[idx]
            withContext(Dispatchers.Main) { appendToTerminal(colorize("Random chose: \"$chosen\"\n", infoColor), infoColor) }
            return try { runSingleCommand(chosen) } catch (t: Throwable) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: failed to run chosen command: ${t.message}\n", errorColor), errorColor) }
                "Error: random execution failed"
            }
        }

        if (inputToken == "button") {
            val inside = command.substringAfter('(', "").substringBefore(')', "").trim()
            if (inside.isBlank()) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Usage: button (echo: Question - opt1=cmd1 - opt2=cmd2)\n", errorColor), errorColor) }
                return "Error: button usage"
            }
            val parts = inside.split('-').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: button: no parts found\n", errorColor), errorColor) }
                return "Error: button parse"
            }
            var question = parts[0]
            if (question.lowercase().startsWith("echo:")) question = question.substringAfter(":", "").trim()
            
            val opts = parts.drop(1).mapNotNull { p ->
                val eq = p.indexOf('=')
                if (eq <= 0) p to p
                else {
                    val lab = p.substring(0, eq).trim()
                    val cmd = p.substring(eq + 1).trim()
                    if (lab.isEmpty() || cmd.isEmpty()) null else lab to cmd
                }
            }
            if (opts.isEmpty()) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: button: no options provided\n", errorColor), errorColor) }
                return "Error: button no options"
            }
            val selection = CompletableDeferred<String?>()
            var overlayView: View? = null
            
            withContext(Dispatchers.Main) {
                try {
                    val root = findViewById<ViewGroup>(android.R.id.content)
                    val overlay = FrameLayout(this@MainActivity).apply {
                        setBackgroundColor(Color.parseColor("#800A0A0A"))
                        isClickable = true
                    }
                    val container = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        val pad = (16 * resources.displayMetrics.density).toInt()
                        setPadding(pad, pad, pad, pad)
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                        setBackgroundColor(Color.parseColor("#0A0A0A"))
                    }
                    val tv = TextView(this@MainActivity).apply {
                        text = question
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.color_info))
                        val padv = (8 * resources.displayMetrics.density).toInt()
                        setPadding(padv, padv, padv, padv)
                    }
                    container.addView(tv)
                    val btnCol = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                    for ((label, cmd) in opts) {
                        val b = Button(this@MainActivity).apply {
                            text = label
                            isAllCaps = false
                            setTextColor(Color.WHITE)
                            setBackgroundColor(Color.TRANSPARENT)
                            setOnClickListener {
                                try { selection.complete(cmd) } catch (_: Throwable) { /* ignore */ }
                                try { root.removeView(overlay) } catch (_: Throwable) { }
                            }
                        }
                        val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (6 * resources.displayMetrics.density).toInt() }
                        btnCol.addView(b, blp)
                    }
                    container.addView(btnCol)
                    overlay.addView(container)
                    root.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    overlayView = overlay
                    appendToTerminal(colorize("\n[button] $question\n", infoColor), infoColor)
                    appendToTerminal(colorize("[button] choose one of: ${opts.map { it.first }.joinToString(", ")}\n", infoColor), infoColor)
                } catch (t: Throwable) {
                    appendToTerminal(colorize("Error: cannot show button UI: ${t.message}\n", errorColor), errorColor)
                    selection.complete(null)
                }
            }
            
            val chosenCmd: String? = try { selection.await() } catch (t: Throwable) { null } finally {
                withContext(Dispatchers.Main) {
                    try {
                        overlayView?.let { rootView ->
                            val root = findViewById<ViewGroup>(android.R.id.content)
                            root.removeView(rootView)
                        }
                    } catch (_: Throwable) { /* ignore */ }
                }
            }
            if (chosenCmd.isNullOrBlank()) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Button selection cancelled or failed\n", errorColor), errorColor) }
                return "Error: button cancelled"
            }
            withContext(Dispatchers.Main) { appendToTerminal(colorize("Button selected — executing: $chosenCmd\n", infoColor), infoColor) }
            return try { runSingleCommand(chosenCmd) } catch (t: Throwable) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Error: failed to execute chosen command: ${t.message}\n", errorColor), errorColor) }
                "Error: button execution failed"
            }
        }

        if (command.equals("clear", ignoreCase = true)) {
            withContext(Dispatchers.Main) { terminalOutput.text = "" }
            val maybe = try { withContext(Dispatchers.Main) { executeWithFallback(command, this@MainActivity) } } catch (_: Throwable) { null }
            if (maybe != null && !maybe.startsWith("Info: Screen cleared.", ignoreCase = true)) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize(maybe + "\n", infoColor), infoColor) }
            }
            return maybe ?: "Info: screen cleared"
        }

        if (command.equals("exit", ignoreCase = true)) {
            withContext(Dispatchers.Main) { appendToTerminal(colorize("shutting down...\n", infoColor), infoColor) }
            delay(300)
            withContext(Dispatchers.Main) { finishAffinity() }
            return "Info: exit"
        }

        if (inputToken == "uninstall") {
            val parts = command.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (parts.size < 2) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Usage: uninstall <package.name>\n", errorColor), errorColor) }
                return "Error: uninstall usage"
            }
            val pkg = parts[1].trim()
            val installed = try { packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
            if (!installed) {
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Not installed: $pkg\n", errorColor), errorColor) }
                return "Error: not installed"
            }
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).setData(Uri.parse("package:$pkg")).putExtra(Intent.EXTRA_RETURN_RESULT, true)
            pendingIntentCompletion = CompletableDeferred()
            try {
                intentLauncher.launch(intent)
                pendingIntentCompletion?.await()
                withContext(Dispatchers.Main) { appendToTerminal(colorize("Uninstall flow finished for $pkg\n", infoColor), infoColor) }
                val stillInstalled = try { packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
                val msg = if (!stillInstalled) {
                    val s = "Info: package removed: $pkg"
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("$s\n", infoColor), infoColor) }
                    s
                } else {
                    val s = "Info: package still installed: $pkg"
                    withContext(Dispatchers.Main) { appendToTerminal(colorize("$s\n", defaultColor), defaultColor) }
                    s
                }
                return msg
            } catch (t: Throwable) {
                pendingIntentCompletion = null
                val errMsg = "Error: cannot launch uninstall for $pkg: ${t.message}"
                withContext(Dispatchers.Main) { appendToTerminal(colorize("$errMsg\n", errorColor), errorColor) }
                return errMsg
            }
        }

        val runInIo = heavyCommands.contains(inputToken)
        if (runInIo) {
            withContext(Dispatchers.Main) { showProgress("Working") }
            val result = try {
                withContext(Dispatchers.IO) {
                    try { executeWithFallback(command, this@MainActivity) } catch (t: Throwable) { "Error: ${t.message ?: "execution failed"}" }
                }
            } finally {
                withContext(Dispatchers.Main) { hideProgress() }
            }
            withContext(Dispatchers.Main) { handleResultAndScroll(command, result, defaultColor, infoColor, errorColor, systemYellow) }
            return result
        } else {
            val result = try {
                withContext(Dispatchers.Main) { executeWithFallback(command, this@MainActivity) }
            } catch (t: Throwable) { "Error: command execution failed" }
            withContext(Dispatchers.Main) { handleResultAndScroll(command, result, defaultColor, infoColor, errorColor, systemYellow) }
            return result
        }
    }

    private fun parseInputToCommandItems(raw: String): List<CommandItem> {
        val result = mutableListOf<CommandItem>()
        val lines = raw.lines()
        for (line0 in lines) {
            var line = line0.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("parallel ", ignoreCase = true) || line.startsWith("parallel:", ignoreCase = true)) {
                val rest = line.substringAfter(':', missingDelimiterValue = "").ifEmpty { line.substringAfter("parallel", "") }.trim().trimStart(':').trim()
                val groupText = if (rest.isEmpty()) "" else rest
                val parts = groupText.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.isNotEmpty()) result.add(CommandItem.Parallel(parts))
                continue
            }
            var i = 0
            val sb = StringBuilder()
            while (i < line.length) {
                if (i + 1 < line.length && line.substring(i, i + 2) == "&&") {
                    val token = sb.toString().trim()
                    if (token.isNotEmpty()) result.add(CommandItem.Single(token, conditionalNext = true, background = token.endsWith(" &")))
                    sb.setLength(0)
                    i += 2
                    continue
                } else if (line[i] == ';') {
                    val token = sb.toString().trim()
                    if (token.isNotEmpty()) result.add(CommandItem.Single(token, conditionalNext = false, background = token.endsWith(" &")))
                    sb.setLength(0)
                    i++
                    continue
                } else {
                    sb.append(line[i])
                    i++
                }
            }
            val last = sb.toString().trim()
            if (last.isNotEmpty()) result.add(CommandItem.Single(last, conditionalNext = false, background = last.endsWith(" &")))
        }
        return result.map { item ->
            when (item) {
                is CommandItem.Single -> item.cleanupBackgroundSuffix()
                is CommandItem.Parallel -> item
            }
        }
    }

    private fun handleResultAndScroll(command: String, result: String?, defaultColor: Int, infoColor: Int, errorColor: Int, systemYellow: Int) {
        if (result != null) {
            val firstToken = command.split("\\s+".toRegex()).firstOrNull()?.lowercase() ?: ""
            val resultColor = when {
                result.startsWith("Error", ignoreCase = true) -> errorColor
                result.startsWith("Info", ignoreCase = true) -> infoColor
                firstToken in setOf("mem", "device", "uname", "uptime", "date") -> systemYellow
                else -> defaultColor
            }
            appendToTerminal(colorize(result + "\n", resultColor), resultColor)
        }
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoScroll = prefs.getBoolean("scroll_behavior", true)
        if (autoScroll) scrollToBottom()
        inputField.post { inputField.requestFocus() }
    }

    private fun focusAndShowKeyboard() {
        inputField.post {
            inputField.requestFocus()
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)
            } catch (_: Throwable) { /* ignore */ }
        }
    }

    private fun showProgress(baseText: String) {
        progressRow.visibility = TextView.VISIBLE
        progressText.text = "$baseText..."
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            var dots = 0
            while (isActive) {
                val s = buildString {
                    append(baseText)
                    repeat(dots + 1) { append('.') }
                }
                progressText.text = s
                dots = (dots + 1) % 3
                delay(300)
            }
        }
    }

    private fun hideProgress() {
        progressJob?.cancel()
        progressJob = null
        progressRow.visibility = TextView.GONE
    }

    private fun parseDurationToSeconds(tok: String): Long {
        if (tok.isEmpty()) return 0L
        val lower = tok.lowercase().trim()
        return try {
            when {
                lower.endsWith("s") && lower.length > 1 -> lower.dropLast(1).toLongOrNull() ?: 0L
                lower.endsWith("m") && lower.length > 1 -> (lower.dropLast(1).toLongOrNull() ?: 0L) * 60L
                lower.endsWith("h") && lower.length > 1 -> (lower.dropLast(1).toLongOrNull() ?: 0L) * 3600L
                else -> lower.toLongOrNull() ?: 0L
            }
        } catch (_: Throwable) { 0L }
    }

    private fun parseDurationToMillis(tok: String): Long {
        if (tok.isEmpty()) return 0L
        val lower = tok.lowercase().trim()
        return try {
            when {
                lower.endsWith("ms") && lower.length > 2 -> lower.dropLast(2).toLongOrNull() ?: 0L
                else -> parseDurationToSeconds(lower) * 1000L
            }
        } catch (_: Throwable) { 0L }
    }

    private fun formatWatchdogTime(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format("%02dh %02dm %02ds", h, m, s)
        else if (m > 0) String.format("%02dm %02ds", m, s)
        else String.format("%02ds", s)
    }

    private fun colorize(text: String, color: Int): SpannableStringBuilder {
        val spannable = SpannableStringBuilder(text)
        spannable.setSpan(ForegroundColorSpan(color), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    private fun appendToTerminal(sp: SpannableStringBuilder, color: Int) {
        runOnUiThread {
            terminalOutput.append(sp)
            scrollToBottom()
        }
    }

    private fun applySecureFlagFromPrefs() {
        try {
            val secure = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("secure_screenshots", false)
            if (secure) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        } catch (_: Throwable) { /* ignore */ }
    }

    private fun scrollToBottom() {
        terminalOutput.post {
            val layout = terminalOutput.layout ?: return@post
            val scrollAmount = layout.getLineTop(terminalOutput.lineCount) - terminalOutput.height
            if (scrollAmount > 0) terminalOutput.scrollTo(0, scrollAmount) else terminalOutput.scrollTo(0, 0)
        }
    }

    private sealed class CommandItem {
        data class Single(val command: String, val conditionalNext: Boolean = false, val background: Boolean = false) : CommandItem() {
            fun cleanupBackgroundSuffix(): Single {
                var c = command
                if (background) c = c.removeSuffix("&").trimEnd()
                return Single(c, conditionalNext, background)
            }
        }
        data class Parallel(val commands: List<String>) : CommandItem()
    }

    private fun checkBootShellOnStart() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(PREF_KEY_BOOT_SHELL, "") ?: ""
            if (saved.isNotBlank()) {
                runOnUiThread {
                    appendToTerminal(colorize("\n[bootshell] auto-running saved commands\n", ContextCompat.getColor(this@MainActivity, R.color.color_info)), ContextCompat.getColor(this@MainActivity, R.color.color_info))
                    inputField.setText(saved)
                    inputField.setSelection(inputField.text.length)
                    lifecycleScope.launch {
                        delay(120)
                        sendCommand()
                    }
                }
            }
        } catch (_: Throwable) { /* ignore */ }
    }

    private fun showBootShellOverlay(initialText: String = "", autoRun: Boolean = false) {
        try {
            val root = findViewById<ViewGroup>(android.R.id.content)
            val existing = root.findViewWithTag<View>("bootshell_overlay")
            if (existing != null) { existing.bringToFront(); return }
            
            val overlay = FrameLayout(this).apply {
                tag = "bootshell_overlay"
                setBackgroundColor(Color.parseColor("#CC000000"))
                isClickable = true
            }
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (14 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                    marginStart = (20 * resources.displayMetrics.density).toInt()
                    marginEnd = (20 * resources.displayMetrics.density).toInt()
                }
                setBackgroundColor(Color.parseColor("#101010"))
            }
            val tv = TextView(this).apply {
                text = "BootShell — автозагрузка команд\n(вставьте команды; сохраните чтобы включить, очистите чтобы отключить)"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.color_info))
                val padv = (8 * resources.displayMetrics.density).toInt()
                setPadding(padv, padv, padv, padv)
            }
            container.addView(tv)
            val et = EditText(this).apply {
                isSingleLine = false
                minLines = 3
                maxLines = 10
                setText(initialText)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terminal_text))
                setBackgroundColor(Color.TRANSPARENT)
                val p = (6 * resources.displayMetrics.density).toInt()
                setPadding(p, p, p, p)
            }
            container.addView(et, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * resources.displayMetrics.density).toInt() })
            
            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            val saveBtn = Button(this).apply {
                text = "Save"; isAllCaps = false
                setOnClickListener {
                    val txt = et.text.toString()
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(PREF_KEY_BOOT_SHELL, txt).apply()
                    appendToTerminal(colorize("\n[bootshell] saved autoload commands\n", ContextCompat.getColor(this@MainActivity, R.color.color_info)), ContextCompat.getColor(this@MainActivity, R.color.color_info))
                }
            }
            val runNowBtn = Button(this).apply {
                text = "Run Now"; isAllCaps = false
                setOnClickListener {
                    val txt = et.text.toString()
                    if (txt.isNotBlank()) {
                        inputField.setText(txt)
                        inputField.setSelection(inputField.text.length)
                        appendToTerminal(colorize("\n[bootshell] running autoload commands\n", ContextCompat.getColor(this@MainActivity, R.color.color_info)), ContextCompat.getColor(this@MainActivity, R.color.color_info))
                        sendCommand()
                    } else {
                        appendToTerminal(colorize("\n[bootshell] nothing to run\n", ContextCompat.getColor(this@MainActivity, R.color.color_info)), ContextCompat.getColor(this@MainActivity, R.color.color_info))
                    }
                }
            }
            val clearBtn = Button(this).apply {
                text = "Clear"; isAllCaps = false
                setOnClickListener {
                    et.setText("")
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(PREF_KEY_BOOT_SHELL, "").apply()
                    appendToTerminal(colorize("\n[bootshell] autoload cleared\n", ContextCompat.getColor(this@MainActivity, R.color.color_info)), ContextCompat.getColor(this@MainActivity, R.color.color_info))
                }
            }
            val closeBtn = Button(this).apply {
                text = "Close"; isAllCaps = false
                setOnClickListener { try { root.removeView(overlay) } catch (_: Throwable) {} }
            }
            
            val paramsBtn = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = (6 * resources.displayMetrics.density).toInt() }
            btnRow.addView(saveBtn, paramsBtn)
            btnRow.addView(runNowBtn, paramsBtn)
            btnRow.addView(clearBtn, paramsBtn)
            btnRow.addView(closeBtn, paramsBtn)
            container.addView(btnRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (10 * resources.displayMetrics.density).toInt() })
            
            overlay.addView(container)
            root.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            
            if (autoRun && initialText.isNotBlank()) {
                inputField.setText(initialText)
                inputField.setSelection(inputField.text.length)
                appendToTerminal(colorize("\n[bootshell] auto-running saved commands\n", ContextCompat.getColor(this@MainActivity, R.color.color_info)), ContextCompat.getColor(this@MainActivity, R.color.color_info))
                lifecycleScope.launch {
                    delay(120)
                    sendCommand()
                }
            }
        } catch (t: Throwable) {
            appendToTerminal(colorize("Error: cannot show bootshell UI: ${t.message}\n", ContextCompat.getColor(this, R.color.color_error)), ContextCompat.getColor(this, R.color.color_error))
        }
    }
}
