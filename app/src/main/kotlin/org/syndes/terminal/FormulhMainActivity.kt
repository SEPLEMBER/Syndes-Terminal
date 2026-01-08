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
        "(a + b)^2 = a^2 + 2ab + b^2",
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
        formulas.forEach { text ->
            val tv = TextView(this).apply {
                id = View.generateViewId()
                this.text = text
                textSize = 18f

                // === NEON PINK TEXT ===
                setTextColor(0xFFFF00B8.toInt())

                typeface = Typeface.MONOSPACE
                setPadding(18, 18, 18, 18)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                // Разрешаем выделение и копирование
                isLongClickable = true
                setTextIsSelectable(true)
                setOnLongClickListener { false }
            }

            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(tv)
            }

            container.addView(wrapper)
        }

        // Нормализация: lowercase + только буквы и цифры
        fun normalize(s: String): String {
            return s.lowercase().filter { it.isLetterOrDigit() }
        }

        fun clearHighlight() {
            highlightedView?.let { v ->
                v.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                if (v is LinearLayout && v.childCount > 0) {
                    v.getChildAt(0).setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            }
            highlightedView = null
        }

        fun highlightAndScroll(targetView: View) {
            clearHighlight()

            // Фон подсветки (тёмный, чтобы розовый текст светился)
            targetView.setBackgroundColor(android.graphics.Color.parseColor("#2A001C"))

            if (targetView is LinearLayout && targetView.childCount > 0) {
                val child = targetView.getChildAt(0)
                child.setBackgroundColor(android.graphics.Color.parseColor("#1A0011"))
                child.setPadding(20, 20, 20, 20)
            }

            highlightedView = targetView

            scroll.post {
                val y = targetView.top - 40
                scroll.smoothScrollTo(0, if (y < 0) 0 else y)
            }
        }

        fun doSearch(query: String) {
            val qnorm = normalize(query)
            if (qnorm.isEmpty()) {
                Toast.makeText(this, "Введите текст для поиска", Toast.LENGTH_SHORT).show()
                return
            }

            var found = false
            for (i in 0 until container.childCount) {
                val wrapper = container.getChildAt(i) as LinearLayout
                val tv = wrapper.getChildAt(0) as TextView
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

        searchButton.setOnClickListener {
            doSearch(searchInput.text.toString())
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE
            ) {
                doSearch(searchInput.text.toString())
                true
            } else {
                false
            }
        }

        searchInput.inputType = InputType.TYPE_CLASS_TEXT
        searchInput.imeOptions = EditorInfo.IME_ACTION_SEARCH
    }
}
