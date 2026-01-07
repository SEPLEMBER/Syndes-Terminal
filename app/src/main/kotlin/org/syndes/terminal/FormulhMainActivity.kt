package org.syndes.terminal

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FormulhMainActivity : AppCompatActivity() {

    // Хардкодированный список формул (plain text)
    private val formulas = listOf(
        "10*(4)",
        "2 + 2 = 4",
        "sqrt(81) = 9",
        "3^3 = 27",
        "100 / 25 = 4",
        "pi ≈ 3.14159",
        "e^1 = 2.71828",
        " (a + b)^2 = a^2 + 2ab + b^2 ",
        "F = m * a",
        "Area = π * r^2",
        "log10(1000) = 3",
        "sin(90°) = 1",
        "10*(4) + 5"
    )

    private var highlightedView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_formulh)

        val searchInput = findViewById<EditText>(R.id.formulh_search)
        val searchButton = findViewById<Button>(R.id.formulh_search_btn)
        val scroll = findViewById<ScrollView>(R.id.formulh_scroll)
        val container = findViewById<LinearLayout>(R.id.formulh_container)

        // Заполняем контейнер текстовыми элементами формул
        formulas.forEachIndexed { index, text ->
            val tv = TextView(this).apply {
                id = View.generateViewId()
                this.text = text
                textSize = 18f
                // neon green monospace on black
                setTextColor(android.graphics.Color.parseColor("#00FF66"))
                typeface = Typeface.MONOSPACE
                setPadding(18, 18, 18, 18)
                // фон прозрачный по умолчанию
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            // небольшой разделитель
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(tv)
            }
            container.addView(wrapper)
        }

        // Вспомогательная нормализация: lowercase + оставить только буквы и цифры
        fun normalize(s: String): String {
            return s.lowercase()
                .filter { it.isLetterOrDigit() } // удаляем пробелы и пунктуацию
        }

        fun clearHighlight() {
            highlightedView?.let { v ->
                v.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            highlightedView = null
        }

        fun highlightAndScroll(targetView: View) {
            // Снимаем предыдущую подсветку
            clearHighlight()
            // Подсветка: neon зелёный фоновый прямоугольник
            targetView.setBackgroundColor(android.graphics.Color.parseColor("#003300")) // тёмный оттенок
            // добавим тонкую яркую рамку (через padding + другой цвет бэкграунда у вложенного TextView)
            if (targetView is LinearLayout && targetView.childCount > 0) {
                val child = targetView.getChildAt(0)
                child.setBackgroundColor(android.graphics.Color.parseColor("#001A00"))
                // верхняя подсветка (яркий край)
                child.setPadding(20, 20, 20, 20)
            }
            highlightedView = targetView

            // Скроллим к позиции (отложенно, чтобы layout успел измериться)
            val scrollView = findViewById<ScrollView>(R.id.formulh_scroll)
            scrollView.post {
                val y = targetView.top - 40 // немного отступ сверху
                scrollView.smoothScrollTo(0, if (y < 0) 0 else y)
            }
        }

        fun doSearch(query: String) {
            val qnorm = normalize(query)
            if (qnorm.isEmpty()) {
                Toast.makeText(this, "Введите текст для поиска", Toast.LENGTH_SHORT).show()
                return
            }

            // Ищем первое совпадение
            var found = false
            for (i in 0 until container.childCount) {
                val wrapper = container.getChildAt(i)
                // child 0 is TextView with formula
                val tv = (wrapper as LinearLayout).getChildAt(0) as TextView
                val tnorm = normalize(tv.text.toString())
                if (tnorm.contains(qnorm)) {
                    highlightAndScroll(wrapper)
                    found = true
                    break
                }
            }

            if (!found) {
                clearHighlight()
                Toast.makeText(this, "Совпадений не найдено", Toast.LENGTH_SHORT).show()
            }
        }

        // Кнопка поиска
        searchButton.setOnClickListener {
            doSearch(searchInput.text.toString())
        }

        // По нажатию Done/Search на клавиатуре
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                doSearch(searchInput.text.toString())
                true
            } else false
        }

        // Настройка поля поиска: моноширинный текст, neon hint, dark background
        searchInput.inputType = InputType.TYPE_CLASS_TEXT
        searchInput.imeOptions = EditorInfo.IME_ACTION_SEARCH
    }
}
