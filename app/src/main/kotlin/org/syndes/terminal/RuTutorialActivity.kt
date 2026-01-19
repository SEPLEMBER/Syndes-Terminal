package org.syndes.terminal

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RuTutorialActivity : AppCompatActivity() {

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

        // Организованные категории (A..E). Команды в каждой категории отсортированы по алфавиту.
        val raw = """
Доступные команды:

about                - показать информацию о приложении и версию
aband                - открыть «О телефоне» / информация об устройстве
acc                  - открыть настройки аккаунтов / синхронизации
accs                 - открыть настройки доступности (Accessibility)
act                  - запустить Activity указанного приложения (если exported=true)
alarm                - открыть приложение будильника
alias <name>=<cmd>   - определить псевдоним (ярлык). Имена псевдонимов не могут содержать пробелы
alias list           - показать список определённых псевдонимов
alias run <name>     - выполнить псевдоним по имени
apm                  - открыть настройки режима полёта
apkkey|filekey <apk> - показать подписи/сертификаты APK
apps                 - открыть экран настроек приложения
appscheck            - утилита проверки package name программ (syndes component)
appmanager           - утилита менеджера приложений оболочки
apse <app|pkg>       - открыть настройки указанного приложения (application details)
backup|snapshot <path> - создать резервную копию файла/папки с SydBack (если доступно)
batchren             - утилита массового переименования файлов (syndes component)
batchedit <old> <new> <dir> [--dry] - массовая замена текста в файлах
bootshell            - открыть BootShell (UI для автозагрузки / редактирования автокоманд)
browser [url]        - открыть браузер (опционально с URL)
bts                  - открыть настройки Bluetooth
btss                 - открыть настройки энергосбережения / батареи
calc                 - открыть приложение калькулятора
call <number>        - открыть набор номера в Dialer
cam                  - открыть камеру (ACTION_IMAGE_CAPTURE)
cat <file>           - показать содержимое файла (поддерживаются SAF/относительные пути)
cd                   - сменить рабочую директорию
checksum <file> [md5|sha256] - вычислить хеш файла (по умолчанию sha256)
clear                - очистить вывод терминала
cleartrash           - очистить корзину
clk                  - открыть настройки даты/времени
cmp <f1> <f2>        - сравнить два текстовых файла
contacts             - открыть приложение контактов
console/settings     - настройки терминала
cp <src> <dst>       - копировать файл или директорию
cut -d<delim> -f<fields> <file> - извлечь поля из файла по разделителю
data                 - открыть настройки мобильных данных
date                 - показать текущие дату и время
decrypt <password> <path> - расшифровать файлы (рекурсивно)
dev                  - открыть настройки разработчика
device               - показать информацию об устройстве
diff [-u] [-c <n>] [-i] <f1> <f2> - показать текстовые отличия
dsp                  - открыть настройки дисплея
du <file|dir>        - показать размер в байтах
email [addr] [subj] [body] - открыть компоновщик письма
encrypt <password> <path> - зашифровать файлы (рекурсивно)
echo  <text>              - написать текст
exit                 - завершить работу приложения
find <name>          - найти файлы по имени
findpkg|pkgof <app>  - найти имя(а) пакета по видимому имени приложения
grep [-r] [-i] [-l] [-n] <pattern> <path> - поиск текста в файлах
hash utilities       - использовать checksum/sha256/md5
head <file> [n]      - показать первые n строк
help                 - показать эту справку
history              - показать историю ввода
home                 - открыть настройки лаунчера
join|merge <file1> <file2> [sep] - объединить файлы построчно
kbd                  - открыть настройки клавиатуры
lang                 - открыть языковые настройки
launch <app>         - запустить приложение по видимому имени
ln <src> <link>      - создать псевдоссылку файла
loc                  - открыть настройки местоположения
ls|dir               - перечислить файлы
md5|sha256 <path>    - вычислить хеш файла
mem [pkg]            - показать использование памяти
mkdir <name>         - создать директорию
mv <src> <dst>       - переместить файл или директорию
nfc                  - открыть настройки NFC
night                - открыть настройки ночного режима
notif                - открыть настройки уведомлений
notify -t <title> -m <message> - отправить системное уведомление
pm install <apk>     - установить APK
pm launch <pkg|app>  - запустить пакет или приложение
pm list [user|system]- перечислить установленные пакеты
pm uninstall <pkg>   - удалить пакет
pminfo|pkginfo <pkg> - показать информацию о пакете
preview <path> [lines] - предварительный просмотр файла
priv                 - открыть настройки приватности
ps|top               - показать запущенные процессы
rename <old> <new> <path> - переименовать файлы
replace <old> <new> <path> - заменить текст в файлах
replacetool          - утилита пакетной замены текста (syndes component)
rev <file> [--inplace] - обратить порядок строк
rm [-r] <path>       - удалить файл или директорию
runsyd <name>        - загрузить и выполнить скрипт из SAF/scripts
rust                 - текстовый редактор rust
search <query>       - поиск в интернете
sec                  - открыть безопасность / детали приложения
shortc               - создать ярлык команды терминала
sleep <min>/<ms>/<sec> - задержка выполнения
sms [number] [text]  - открыть SMS-приложение
snd                  - открыть настройки звука
sort-lines <file> [--unique] [--reverse] [--inplace] - сортировка строк
split <file> <lines_per_file> [prefix] - разбить файл
stg                  - открыть настройки хранилища
stat <path>          - статистика файла/директории
status               - утилита информации о системе
stash|trash <path>   - переместить в корзину
sysclipboard get|set <text> - системный буфер обмена
tail <file> [n]      - последние строки файла
touch <name>         - создать пустой файл
uname                - показать системное имя
unzip <archive> [dest] - распаковать ZIP
uptime               - время работы системы
vpns                 - открыть настройки VPN
wait <sec>           - блокировка выполнения
watchdog      - тоже самое, что и sleep
wc <file>            - подсчитать строки/слова/символы
wifi                 - открыть настройки Wi-Fi
whoami               - показать текущего пользователя
zip <source> <archive> - создать ZIP-архив

!!! ВНИМАНИЕ (ENCRYPT):
шифрование больших папок требует большой CPU-нагрузки.
Пожалуйста, шифруйте небольшие папки или отдельные директории по очереди.
Итерации = 75 000. Это даёт ограниченную стойкость KDF.
Используйте сильный пароль — рекомендуется минимум 8 различных символов. Алгоритм: AES 256 GCM.

Примечания:
  - runsyd читает скрипты из корня SAF → директории 'scripts' (пытается name.syd, name.sh, name.txt). Поддерживает как указание расширения, так и его отсутствие — например: "runsyd scriptname" или "runsyd scriptname.syd".
  - pm uninstall запускает системный поток удаления (пользователю нужно подтвердить каждый диалог удаления).
  - многие файловые операции поддерживают SAF-пути или относительные пути от настроенной рабочей директории.
  - псевдонимы (aliases) локальны для приложения и не влияют на Android shell.
  - Поддерживаются последовательности команд: команды вида 'cmd1; cmd2; cmd3' выполняются по очереди. Группы с префиксом 'parallel:' такие как 'parallel: cmd1; cmd2; cmd3' выполняются параллельно. Команды с '&' (например 'cmd1 & cmd2; cmd3') ведут себя как фоновые/неблокирующие — полезно, например, чтобы открыть Activity, не останавливая остальные команды.
  - Поддерживается команда 'button': 'button (Текст вопроса - Вариант1=cmd1 - Вариант2=cmd2 - ...)', разделитель частей — '-'. Если 'button(...)' встречается в цепочке команд (например 'button(...); othercommand'), последующие команды будут приостановлены до тех пор, пока пользователь не выберет один из вариантов. После выбора цепочка возобновится — к ней будет добавлена команда, связанная с выбранным вариантом (выполнится как будто пользователь ввёл её вручную).
  - Поддерживается команда 'random {cmd1-cmd2-cmd3}'. Она запускает случайно выбранную команду из предоставленного списка.
  - SyPL Compiler дополняет функции терминала следующими командами:
  - `if <left> = <right> then <command>` с поддержкой `else <command>`. `then` выполняет указанную команду как если бы пользователь ввёл её вручную (вплоть до ожиданий/блокировок). `else` относится ко всей последовательности подряд идущих `if` (если между ними нет других команд) и выполняется только если ни одно из предыдущих `if` в цепочке не сработало. Примеры с `echo`: `if 1 = 1 then echo ok` — сработает, выведет `ok` (литеральное сравнение). 
  -`echo hello` `if echo hello = whatever then echo prev_cmd_matched` — сработает, потому что сравнивается последняя выполненная команда. `echo hi` `if hi = hi then echo result_matched` — сработает, потому что сравнивается последний результат команды. Цепочка:
  - `if cmdA = x then echo A`
  - `if cmdB = y then echo B`
  `else echo fallback`
  — `else` выполнится только если ни `A`, ни `B` не сработали.
  - существует поддержка `cycle`. Поддерживаемые формы:
  - `cycle <N>t <interval>=<cmd>` — выполнить `<cmd>` N раз с паузой `<interval>` между запусками. Примеры: `cycle 10t 3ms=echo hi`, `cycle 5t 2s=echo tick`. Поддерживаются суффиксы времени `ms`, `s`, `m`.
  - `cycle next <Mi>i <N>t=<cmd>` — выполнять `<cmd>` N раз, каждый раз после того как будет обработано `Mi` команд (т.е. через указанное количество обработанных команд). Пример: `cycle next 3i 7t=echo every3` — команда `echo every3` будет инжектирована и выполнена 7 раз, каждый раз после обработки 3 команд.
  - циклы планируются как фоновые задачи и добавляют команды в очередь исполнения согласно расписанию/триггерам.

""".trimIndent()

        val sb = SpannableStringBuilder(raw)

        // 1) highlight the warning header in red (match Russian header)
        val warningHeader = "!!! ВНИМАНИЕ (ENCRYPT):"
        val whIndex = raw.indexOf(warningHeader)
        if (whIndex >= 0) {
            val start = whIndex
            val end = whIndex + warningHeader.length
            sb.setSpan(ForegroundColorSpan(colorWarning), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // 2) highlight the essence of the warning (Russian phrases)
        val essenceTargets = listOf(
            "шифрование больших папок требует большой CPU-нагрузки.",
            "Пожалуйста, шифруйте небольшие папки или отдельные директории по очереди.",
            "Итерации = 1000 (НИЗКО). Это даёт ограниченную стойкость KDF."
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
