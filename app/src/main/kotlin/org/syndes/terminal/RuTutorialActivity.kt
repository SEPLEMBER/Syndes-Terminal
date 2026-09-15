package org.syndes.terminal

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class RuTutorialActivity : AppCompatActivity() {

    private lateinit var tutorialText: TextView
    private lateinit var searchEdit: EditText
    private lateinit var clearSearch: ImageButton
    private lateinit var searchStats: TextView
    
    private var originalText: String = ""
    private val colorCommand = Color.parseColor("#4FC3F7")
    private val colorArg = Color.parseColor("#80E27E")
    private val colorWarning = Color.parseColor("#FF5252")
    private val colorNeonCyan = Color.parseColor("#00FFF0")
    private val colorDefault = Color.parseColor("#E0E0E0")
    private val colorSearchHighlight = Color.parseColor("#3300FFF0") // полупрозрачный cyan

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        tutorialText = findViewById(R.id.tutorial_text)
        searchEdit = findViewById(R.id.search_edit)
        clearSearch = findViewById(R.id.clear_search)
        searchStats = findViewById(R.id.search_stats)

        // Настройка TextView
        tutorialText.typeface = Typeface.MONOSPACE
        applyNeon(tutorialText, colorNeonCyan, radius = 4f)

        // Build original text
        originalText = buildHighlightedCommands().toString()
        tutorialText.setText(buildHighlightedCommands(), TextView.BufferType.SPANNABLE)

        // Search functionality
        setupSearch()
    }

    private fun setupSearch() {
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    performSearch(query)
                    clearSearch.visibility = android.view.View.VISIBLE
                    searchStats.visibility = android.view.View.VISIBLE
                } else {
                    tutorialText.setText(buildHighlightedCommands(), TextView.BufferType.SPANNABLE)
                    clearSearch.visibility = android.view.View.GONE
                    searchStats.visibility = android.view.View.GONE
                }
            }
        })

        searchEdit.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                // Можно добавить переход к следующему результату
                true
            } else {
                false
            }
        }

        clearSearch.setOnClickListener {
            searchEdit.setText("")
            searchEdit.clearFocus()
        }
    }

    private fun performSearch(query: String) {
        val sb = SpannableStringBuilder(originalText)
        val lowerQuery = query.lowercase()
        var matchCount = 0
        
        // Highlight command names that match
        val lines = originalText.split("\n")
        var offset = 0
        
        for (line in lines) {
            val lineStart = offset
            val lineLower = line.lowercase()
            
            // Check if line contains query
            if (lineLower.contains(lowerQuery)) {
                var searchStart = 0
                while (true) {
                    val idx = lineLower.indexOf(lowerQuery, searchStart)
                    if (idx == -1) break
                    
                    val absStart = lineStart + idx
                    val absEnd = absStart + query.length
                    
                    // Apply highlight
                    sb.setSpan(
                        BackgroundColorSpan(colorSearchHighlight),
                        absStart, absEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        UnderlineSpan(),
                        absStart, absEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    matchCount++
                    searchStart = idx + 1
                }
            }
            
            offset += line.length + 1
        }
        
        // Reapply syntax highlighting on top
        applySyntaxHighlighting(sb)
        
        tutorialText.setText(sb, TextView.BufferType.SPANNABLE)
        searchStats.text = "Найдено: $matchCount совпадений"
    }

    private fun applySyntaxHighlighting(sb: SpannableStringBuilder) {
        // Re-apply command colors (blue) and arg colors (green)
        val lines = originalText.split("\n")
        var offset = 0
        
        for (line in lines) {
            val lineStart = offset
            val sepIndexInLine = line.indexOf(" - ")
            
            if (sepIndexInLine >= 0 && line.trim().isNotEmpty()) {
                // Command part
                val commandEnd = lineStart + sepIndexInLine
                sb.setSpan(ForegroundColorSpan(colorCommand), lineStart, commandEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                // Args in command part
                val leftPart = line.substring(0, sepIndexInLine)
                highlightArgsInText(sb, leftPart, lineStart)
                
                // Description part
                val descStart = lineStart + sepIndexInLine + 3
                val descText = line.substring(sepIndexInLine + 3)
                highlightArgsInText(sb, descText, descStart)
            }
            
            offset += line.length + 1
        }
    }

    private fun highlightArgsInText(sb: SpannableStringBuilder, text: String, baseOffset: Int) {
        // Highlight <...>
        var relIndex = 0
        while (true) {
            val lt = text.indexOf('<', relIndex)
            if (lt < 0) break
            val gt = text.indexOf('>', lt + 1)
            if (gt < 0) break
            sb.setSpan(
                ForegroundColorSpan(colorArg),
                baseOffset + lt, baseOffset + gt + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            relIndex = gt + 1
        }
        
        // Highlight flags
        val flagRegex = Regex("""\B-[-\w\[\]]+""")
        flagRegex.findAll(text).forEach { m ->
            sb.setSpan(
                ForegroundColorSpan(colorArg),
                baseOffset + m.range.first, baseOffset + m.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun buildHighlightedCommands(): SpannableStringBuilder {
        val raw = """
Доступные команды:

=== ОСНОВНЫЕ КОМАНДЫ ===
about                - показать информацию о приложении и версию
aband                - открыть «О телефоне» / информация об устройстве
acc                  - открыть настройки аккаунтов / синхронизации
accs                 - открыть настройки доступности (Accessibility)
act                  - запустить Activity указанного приложения (если exported=true)
alarm                - открыть приложение будильника
apm                  - открыть настройки режима полёта
apps                 - открыть экран настроек приложения
apse <app|pkg>       - открыть настройки указанного приложения (application details)
backup|snapshot <path> - создать резервную копию файла/папки с SydBack
bootshell            - открыть BootShell (UI для автозагрузки)
browser [url]        - открыть браузер (опционально с URL)
bts                  - открыть настройки Bluetooth
btss                 - открыть настройки энергосбережения / батареи
calc                 - открыть приложение калькулятора
call <number>        - открыть набор номера в Dialer
cam                  - открыть камеру (ACTION_IMAGE_CAPTURE)
clk                  - открыть настройки даты/времени
contacts             - открыть приложение контактов
data                 - открыть настройки мобильных данных
date                 - показать текущие дату и время
dev                  - открыть настройки разработчика
device               - показать информацию об устройстве
dsp                  - открыть настройки дисплея
email [addr] [subj] [body] - открыть компоновщик письма
exit                 - завершить работу приложения
flashlight <1|0>     - включить/выключить фонарик (1=ON, 0=OFF)
help                 - показать эту справку
history              - показать историю ввода
home                 - открыть настройки лаунчера
kbd                  - открыть настройки клавиатуры
lang                 - открыть языковые настройки
loc                  - открыть настройки местоположения
nfc                  - открыть настройки NFC
night                - открыть настройки ночного режима
notif                - открыть настройки уведомлений
priv                 - открыть настройки приватности
search <query>       - поиск в интернете
sec                  - открыть безопасность / детали приложения
sms [number] [text]  - открыть SMS-приложение
snd                  - открыть настройки звука
stg                  - открыть настройки хранилища
vpns                 - открыть настройки VPN
wifi                 - открыть настройки Wi-Fi

=== ФАЙЛОВЫЕ ОПЕРАЦИИ ===
cat <file>           - показать содержимое файла (SAF/относительные пути)
cd <path>            - сменить рабочую директорию
checksum <file> [md5|sha256] - вычислить хеш файла (по умолчанию sha256)
cleartrash           - очистить корзину
cmp <f1> <f2>        - сравнить два текстовых файла
cp <src> <dst>       - копировать файл или директорию
cut -d<delim> -f<fields> <file> - извлечь поля из файла по разделителю
diff [-u] [-c <n>] [-i] <f1> <f2> - показать текстовые отличия
du <file|dir>        - показать размер в байтах
find <name>          - найти файлы по имени
grep [-r] [-i] [-l] [-n] <pattern> <path> - поиск текста в файлах
head <file> [n]      - показать первые n строк
join|merge <file1> <file2> [sep] - объединить файлы построчно
ls|dir               - перечислить файлы
md5|sha256 <path>    - вычислить хеш файла
mkdir <name>         - создать директорию
mv <src> <dst>       - переместить файл или директорию
preview <path> [lines] - предварительный просмотр файла
rename <old> <new> <path> - переименовать файлы
replace <old> <new> <path> - заменить текст в файлах
rev <file> [--inplace] - обратить порядок строк
rm [-r] <path>       - удалить файл или директорию
sort-lines <file> [--unique] [--reverse] [--inplace] - сортировка строк
split <file> <lines_per_file> [prefix] - разбить файл
stat <path>          - статистика файла/директории
stash|trash <path>   - переместить в корзину
tail <file> [n]      - последние строки файла
touch <name>         - создать пустой файл
unzip <archive> [dest] - распаковать ZIP
zip <source> <archive> - создать ZIP-архив

=== УТИЛИТЫ ===
apkkey|filekey <apk> - показать подписи/сертификаты APK
appscheck            - утилита проверки package name программ
appmanager           - утилита менеджера приложений оболочки
batchren             - утилита массового переименования файлов
batchedit <old> <new> <dir> [--dry] - массовая замена текста в файлах
basename <path>      - получить имя файла из пути
clear                - очистить вывод терминала
console/settings     - настройки терминала
dirname <path>       - получить путь к директории
echo <text>          - вывести текст
file <path>          - определить тип файла
findpkg|pkgof <app>  - найти имя пакета по имени приложения
mem [pkg]            - показать использование памяти
notify -t <title> -m <message> - отправить системное уведомление
ps|top               - показать запущенные процессы
replacetool          - утилита пакетной замены текста
shortc               - создать ярлык команды терминала
status               - утилита информации о системе
sysclipboard get|set <text> - системный буфер обмена
uname                - показать системное имя
uptime               - время работы системы
wait <sec>           - блокировка выполнения
watchdog             - то же самое, что sleep
wc <file>            - подсчитать строки/слова/символы
whoami               - показать текущего пользователя

=== РАСШИРЕННЫЕ УТИЛИТЫ ===
base64 <-e|-d> <file> - кодирование/декодирование base64
neopad <filename>    - открыть файл в редакторе NeonPad
printf <format> [args] - форматированный вывод
seq <start> <end>    - генерация последовательности чисел
strings <file>       - поиск печатаемых строк в файле
tree [path]          - показать структуру директорий
xxd <file>           - hex-дамп файла

=== АЛИАСЫ ===
alias <name>=<cmd>   - определить псевдоним (ярлык)
alias list           - показать список псевдонимов
alias run <name>     - выполнить псевдоним по имени
sydalias list        - показать все алиасы
sydalias create <name>=<cmd> - создать/обновить алиас
sydalias remove <name> - удалить алиас

=== ПАКЕТНЫЙ МЕНЕДЖМЕНТ ===
pm install <apk>     - установить APK
pm launch <pkg|app>  - запустить пакет или приложение
pm list [user|system]- перечислить установленные пакеты
pm uninstall <pkg>   - удалить пакет
pminfo|pkginfo <pkg> - показать информацию о пакете

=== СКРИПТЫ И АВТОМАТИЗАЦИЯ ===
runsyd <name>        - загрузить и выполнить syd скрипт из SAF/scripts
rust                 - текстовый редактор rust
sydcheck <name>      - поиск подозрительных команд в syd-скриптах

=== СПЕЦИАЛЬНЫЕ КОМАНДЫ ===
button (Текст - Опция1=cmd1 - Опция2=cmd2) - показать выбор пользователю
cycle <N>t <interval>=<cmd> - выполнить cmd N раз с интервалом
cycle next <Mi>i <N>t=<cmd> - выполнять cmd каждые Mi команд
if <left> = <right> then <cmd> [else <cmd>] - условное выполнение
parallel: cmd1; cmd2 - параллельное выполнение команд
random {cmd1-cmd2-cmd3} - случайная команда из списка
sleep <min>/<ms>/<sec> - задержка выполнения

=== ПРИЛОЖЕНИЯ И АКТИВНОСТИ ===
aspernet             - открыть AsperNet
bugfixer             - открыть игру BUG FIXER
bugtrack             - открыть BugTracker
bye script           - открыть Bye Script
epub editor          - открыть Epub Editor
flowscript           - открыть FlowScript
led form             - открыть Led Form
metro                - открыть METRO
numtrap              - открыть Numtrap
omnisearch           - открыть OMNISEARCH
scriptsteel          - открыть Scriptsteel
vectorframe          - открыть Vector Frame
wtchd                - открыть Watchdog

Примечания:
  - runsyd читает скрипты из корня SAF → директории 'scripts' (пытается name.syd, name.sh, name.txt)
  - pm uninstall требует подтверждения каждого диалога удаления
  - многие файловые операции поддерживают SAF-пути или относительные пути
  - псевдонимы (aliases) локальны для приложения
  - Последовательности: 'cmd1; cmd2; cmd3' выполняются по очереди
  - Параллельно: 'parallel: cmd1; cmd2' выполняются параллельно
  - Фоновые: 'cmd1 & cmd2' - неблокирующие команды
  - Поддерживаются суффиксы времени: ms, s, m

""".trimIndent()

        return SpannableStringBuilder(raw)
    }

    private fun applyNeon(tv: TextView, color: Int, radius: Float = 4f, dx: Float = 0f, dy: Float = 0f) {
        try {
            tv.setShadowLayer(radius, dx, dy, color)
        } catch (_: Throwable) {
            // ignore
        }
    }
}
