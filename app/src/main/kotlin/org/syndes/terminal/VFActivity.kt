package org.syndes.terminal

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.syndes.terminal.databinding.ActivityVfBinding

class VFActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVfBinding
    private val externalPackage = "es.zelliot.epubeditor"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Опционально: флаг безопасности, как в NTRActivity, если нужен
        // window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityVfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // --- ВНУТРЕННИЕ МОДУЛИ ---
        binding.btnMorphAnalyzer.setOnClickListener { 
            performHaptic(binding.btnMorphAnalyzer)
            launchInternal(MorphAnalyzerActivity::class.java, "Морфологический анализатор") 
        }
        binding.btnMultiReplace.setOnClickListener { 
            performHaptic(binding.btnMultiReplace)
            launchInternal(MultiReplaceActivity::class.java, "Множественная замена") 
        }
        binding.btnSchedulePlanner.setOnClickListener { 
            performHaptic(binding.btnSchedulePlanner)
            launchInternal(SchedulePlannerActivity::class.java, "Планировщик расписания") 
        }
        binding.btnDataSanitizer.setOnClickListener { 
            performHaptic(binding.btnDataSanitizer)
            launchInternal(DataSanitizerActivity::class.java, "Очистка данных") 
        }
        binding.btnAnalyzerTool.setOnClickListener { 
            performHaptic(binding.btnAnalyzerTool)
            launchInternal(AnalyzerToolActivity::class.java, "Инструмент анализа") 
        }

        // --- ВНЕШНИЕ МОДУЛИ ---
        binding.btnTedSearch.setOnClickListener { 
            performHaptic(binding.btnTedSearch)
            launchExternal("es.zelliot.epubeditor.TedSearchActivity", "Поиск Ted") 
        }
        binding.btnTextWalker.setOnClickListener { 
            performHaptic(binding.btnTextWalker)
            launchExternal("es.zelliot.epubeditor.TextWalkerActivity", "TextWalker утилита") 
        }
        binding.btnExperimentalText.setOnClickListener { 
            performHaptic(binding.btnExperimentalText)
            // ВНИМАНИЕ: Если ваш класс называется ExperimentalTextProcessionActivity, 
            // замените строку ниже на "es.zelliot.epubeditor.ExperimentalTextProcessionActivity"
            launchExternal("es.zelliot.epubeditor.ExperimentalTextProcession", "Экспериментальная обработка текста") 
        }
        binding.btnTextFormatter.setOnClickListener { 
            performHaptic(binding.btnTextFormatter)
            launchExternal("es.zelliot.epubeditor.TextFormatterActivity", "Форматирование текста") 
        }
    }

    private fun launchInternal(activityClass: Class<*>, moduleName: String) {
        try {
            startActivity(Intent(this, activityClass))
        } catch (e: Exception) {
            showToast("Ошибка: Внутренний модуль '$moduleName' не найден или упал.")
        }
    }

    private fun launchExternal(fullClassName: String, moduleName: String) {
        val intent = Intent().setClassName(externalPackage, fullClassName)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showToast("Ошибка: Внешнее приложение '$moduleName' не установлено.")
        }
    }

    private fun performHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
