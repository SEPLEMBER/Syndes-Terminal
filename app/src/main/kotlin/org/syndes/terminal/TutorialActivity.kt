package org.syndes.terminal

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TutorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        val tv: TextView = findViewById(R.id.tutorial_text)
        // monospace for terminal feel
        tv.typeface = Typeface.MONOSPACE
        // subtle neon glow
        val neonCyan = Color.parseColor("#00FFF0")
        applyNeon(tv, neonCyan, radius = 4f)

        tv.setText(buildHighlightedCommands(), TextView.BufferType.SPANNABLE)
    }

    private fun buildHighlightedCommands(): SpannableStringBuilder {
        // Цвета
        val colorCommand = Color.parseColor("#4FC3F7") // blue-ish
        val colorArg = Color.parseColor("#80E27E")     // green-ish
        val colorWarning = Color.parseColor("#FF5252") // red
        val colorNeonCyan = Color.parseColor("#00FFF0") // neon cyan
        val colorDefault = Color.parseColor("#E0E0E0")

        // Organized categories (A..E). Commands in each category are alphabetically sorted.
        val raw = """
Available commands:

about                - show application information and version
aband                - open “About phone” / device information
acc                  - open accounts / sync settings
accs                 - open accessibility settings
act                  - launch an Activity of the specified app (if exported=true)
alarm                - open the alarm application
alias <name>=<cmd>   - define an alias (shortcut). Alias names must not contain spaces
alias list           - show list of defined aliases
alias run <name>     - execute alias by name
apm                  - open airplane mode settings
apkkey|filekey <apk> - show APK signatures / certificates
apps                 - open application settings screen
appscheck            - utility to check application package names (syndes component)
appmanager           - shell application manager utility
apse <app|pkg>       - open application details for specified app
backup|snapshot <path> - create a backup of a file/folder using SydBack (if available)
batchren             - batch file renaming utility (syndes component)
batchedit <old> <new> <dir> [--dry] - batch text replacement in files
bootshell            - open BootShell (UI for autostart / auto-command editing)
browser [url]        - open browser (optionally with URL)
bts                  - open Bluetooth settings
btss                 - open battery / power saving settings
calc                 - open calculator application
call <number>        - open dialer with specified number
cam                  - open camera (ACTION_IMAGE_CAPTURE)
cat <file>           - show file contents (SAF/relative paths supported)
cd                   - change working directory
checksum <file> [md5|sha256] - calculate file hash (default: sha256)
clear                - clear terminal output
cleartrash           - empty trash
clk                  - open date/time settings
cmp <f1> <f2>        - compare two text files
contacts             - open contacts application
console/settings     - terminal settings
cp <src> <dst>       - copy file or directory
cut -d<delim> -f<fields> <file> - extract fields from file using delimiter
data                 - open mobile data settings
date                 - show current date and time
decrypt <password> <path> - decrypt files (recursively)
dev                  - open developer options
device               - show device information
diff [-u] [-c <n>] [-i] <f1> <f2> - show text differences
dsp                  - open display settings
du <file|dir>        - show size in bytes
email [addr] [subj] [body] - open email composer
encrypt <password> <path> - encrypt files (recursively)
echo <text>          - print text
exit                 - exit application
find <name>          - find files by name
findpkg|pkgof <app>  - find package name(s) by visible app name
grep [-r] [-i] [-l] [-n] <pattern> <path> - search text in files
hash utilities       - use checksum/sha256/md5
head <file> [n]      - show first n lines
help                 - show this help
history              - show input history
home                 - open launcher settings
join|merge <file1> <file2> [sep] - merge files line by line
kbd                  - open keyboard settings
lang                 - open language settings
launch <app>         - launch app by visible name
ln <src> <link>      - create file pseudo-link
loc                  - open location settings
ls|dir               - list files
md5|sha256 <path>    - calculate file hash
mem [pkg]            - show memory usage
mkdir <name>         - create directory
mv <src> <dst>       - move file or directory
nfc                  - open NFC settings
night                - open night mode settings
notif                - open notification settings
notify -t <title> -m <message> - send system notification
pm install <apk>     - install APK
pm launch <pkg|app>  - launch package or app
pm list [user|system]- list installed packages
pm uninstall <pkg>   - uninstall package
pminfo|pkginfo <pkg> - show package information
preview <path> [lines] - preview file
priv                 - open privacy settings
ps|top               - show running processes
rename <old> <new> <path> - rename files
replace <old> <new> <path> - replace text in files
replacetool          - batch text replacement utility (syndes component)
rev <file> [--inplace] - reverse line order
rm [-r] <path>       - delete file or directory
runsyd <name>        - load and execute script from SAF/scripts
rust                 - rust text editor
search <query>       - internet search
sec                  - open security / app security details
shortc               - create terminal command shortcut
sleep <min>/<ms>/<sec> - execution delay
sms [number] [text]  - open SMS application
snd                  - open sound settings
sort-lines <file> [--unique] [--reverse] [--inplace] - sort lines
split <file> <lines_per_file> [prefix] - split file
stg                  - open storage settings
stat <path>          - file/directory statistics
status               - system information utility
stash|trash <path>   - move to trash
sysclipboard get|set <text> - system clipboard access
tail <file> [n]      - show last lines of file
touch <name>         - create empty file
uname                - show system name
unzip <archive> [dest] - extract ZIP archive
uptime               - system uptime
vpns                 - open VPN settings
wait <sec>           - block execution
watchdog             - same as sleep
wc <file>            - count lines/words/characters
wifi                 - open Wi-Fi settings
whoami               - show current user
zip <source> <archive> - create ZIP archive


==G: Misc / Notes & Warnings==
  !!! WARNING (ENCRYPT): Encrypting large folders requires high CPU load.
Please encrypt small folders or individual directories sequentially.
Iterations = 75,000. This provides limited KDF strength.
Use a strong password — at least 8 different characters recommended.
Algorithm: AES-256-GCM.

Notes:
  - runsyd reads scripts from SAF root → 'scripts' directory (tries name.syd, name.sh, name.txt). Supports both specifying the file extension and omitting it — e.g. you can run "runsyd scriptname" or "runsyd scriptname.syd".
  - pm uninstall starts system uninstall flow (user must confirm each uninstall dialog).
  - resetup opens UI that iterates package list and launches system uninstall dialogs one-by-one.
  - many file operations support SAF paths or relative paths from the configured work directory.
  - aliases are local to the app and do not affect the Android shell.
  - Command sequences are supported: commands of the form 'cmd1; cmd2; cmd3' run sequentially. Groups prefixed with 'parallel:' such as 'parallel: cmd1; cmd2; cmd3' run concurrently. Commands that include '&' (for example 'cmd1 & cmd2; cmd3') behave as backgrounded or non-blocking tasks — useful when, for example, you need to start an Activity without stopping the rest of the chain.
  - Supports the 'button' command: 'button (Question text - Option1=cmd1 - Option2=cmd2 - ...)', using '-' as the separator between parts. If a 'button(...)' appears in a command chain (for example 'button(...); othercommand'), the following commands will be paused until the user selects one of the options. After a choice is made the chain resumes, and the command associated with the chosen option is appended to the chain (executed as if the user had entered it by pressing the button).
  - Supports the 'random {cmd1-cmd2-cmd3}' command. This runs a randomly selected command from the provided list.
  
  - SyPL Compiler extends the terminal functions with the following commands:
  - `if <left> = <right> then <command>` with `else <command>` support. `then` executes the specified command as if the user typed it manually (including waits/blocks). `else` refers to the entire sequence of consecutive `if` statements (as long as there are no other commands between them) and is executed only if none of the previous `if` statements in the chain have triggered. Examples with `echo`: `if 1 = 1 then echo ok` — will trigger and output `ok` (literal comparison).
  - `echo hello` `if echo hello = whatever then echo prev_cmd_matched` — will trigger because the last executed command is being compared. `echo hi` `if hi = hi then echo result_matched` — will trigger because the last command result is being compared. Chain:
    - `if cmdA = x then echo A`
    - `if cmdB = y then echo B`
    `else echo fallback`
    — `else` will execute only if neither `A` nor `B` was triggered.
  - cycle support exists. Supported forms:
  - `cycle <N>t <interval>=<cmd>` — execute `<cmd>` N times with `<interval>` pause between executions. Examples: `cycle 10t 3ms=echo hi`, `cycle 5t 2s=echo tick`. Time suffixes `ms`, `s`, `m` are supported.
  - `cycle next <Mi>i <N>t=<cmd>` — execute `<cmd>` N times, each time after `Mi` commands have been processed (i.e., after the specified number of processed commands). Example: `cycle next 3i 7t=echo every3` — the command `echo every3` will be injected and executed 7 times, each time after processing 3 commands.
  - cycles are scheduled as background tasks and add commands to the execution queue according to their schedule/trigger.

""".trimIndent()

        val sb = SpannableStringBuilder(raw)

        // 1) highlight the "!!! WARNING (ENCRYPT):" header in red
        val warningHeader = "!!! WARNING (ENCRYPT):"
        val whIndex = raw.indexOf(warningHeader)
        if (whIndex >= 0) {
            val start = whIndex
            val end = whIndex + warningHeader.length
            sb.setSpan(ForegroundColorSpan(colorWarning), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // 2) highlight the essence of the warning (look for sentence containing "encrypting large folders")
        val essenceTargets = listOf(
            "encrypting large folders is CPU-intensive.",
            "Please encrypt small folders or specific directories one-by-one.",
            "AES-GCM-256"
        )
        essenceTargets.forEach { t ->
            val idx = raw.indexOf(t, ignoreCase = true)
            if (idx >= 0) {
                sb.setSpan(ForegroundColorSpan(colorNeonCyan), idx, idx + t.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // 3) per-line parsing: command part (left of " - ") -> blue; args <...> and flags -x -> green
        val lines = raw.split("\n")
        var offset = 0
        for (line in lines) {
            // position of this line in sb
            val lineStart = offset
            val lineEnd = offset + line.length

            // find separator " - "
            val sepIndexInLine = line.indexOf(" - ")
            val commandPartEnd = if (sepIndexInLine >= 0) lineStart + sepIndexInLine else lineEnd

            if (line.trim().isNotEmpty()) {
                // color command part (left of " - ") in blue (only if it's not description/warning block)
                if (commandPartEnd > lineStart) {
                    sb.setSpan(ForegroundColorSpan(colorCommand), lineStart, commandPartEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // inside left part, highlight <...> and -flags as green
                    val leftPart = line.substring(0, if (sepIndexInLine >= 0) sepIndexInLine else line.length)
                    var relIndex = 0
                    while (true) {
                        val lt = leftPart.indexOf('<', relIndex)
                        if (lt < 0) break
                        val gt = leftPart.indexOf('>', lt + 1)
                        if (gt < 0) break
                        val absStart = lineStart + lt
                        val absEnd = lineStart + gt + 1
                        sb.setSpan(ForegroundColorSpan(colorArg), absStart, absEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        relIndex = gt + 1
                    }
                    val flagRegex = Regex("""\B-[-\w\[\]]+""")
                    flagRegex.findAll(leftPart).forEach { m ->
                        val absStart = lineStart + m.range.first
                        val absEnd = lineStart + m.range.last + 1
                        sb.setSpan(ForegroundColorSpan(colorArg), absStart, absEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else {
                    val firstNonSpace = line.indexOfFirst { !it.isWhitespace() }
                    if (firstNonSpace >= 0) {
                        val tokenEndInLine = line.indexOfFirst { it.isWhitespace() }.let { if (it < 0) line.length else it }
                        val absStart = lineStart + firstNonSpace
                        val absEnd = lineStart + tokenEndInLine
                        if (absEnd > absStart) {
                            sb.setSpan(ForegroundColorSpan(colorCommand), absStart, absEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
            }

            // description part highlight (<...> and flags)
            if (sepIndexInLine >= 0) {
                val descStart = lineStart + sepIndexInLine + 3 // after " - "
                val descText = line.substring(sepIndexInLine + 3)
                var relIndex = 0
                while (true) {
                    val lt = descText.indexOf('<', relIndex)
                    if (lt < 0) break
                    val gt = descText.indexOf('>', lt + 1)
                    if (gt < 0) break
                    val absStart = descStart + lt
                    val absEnd = descStart + gt + 1
                    sb.setSpan(ForegroundColorSpan(colorArg), absStart, absEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    relIndex = gt + 1
                }
                val flagRegex = Regex("""\B-[-\w\[\]]+""")
                flagRegex.findAll(descText).forEach { m ->
                    val absStart = descStart + m.range.first
                    val absEnd = descStart + m.range.last + 1
                    sb.setSpan(ForegroundColorSpan(colorArg), absStart, absEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            // advance offset (+1 for newline)
            offset += line.length + 1
        }

        return sb
    }

    // subtle neon glow helper
    private fun applyNeon(tv: TextView, color: Int, radius: Float = 4f, dx: Float = 0f, dy: Float = 0f) {
        try {
            tv.setShadowLayer(radius, dx, dy, color)
        } catch (_: Throwable) {
            // defensive: some devices could behave differently; ignore failure
        }
    }
}
