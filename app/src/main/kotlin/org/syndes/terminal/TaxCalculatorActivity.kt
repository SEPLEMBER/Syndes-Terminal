package org.syndes.terminal

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.syndes.terminal.databinding.ActivityTaxCalculatorBinding
import java.text.NumberFormat
import java.util.Locale

class TaxCalculatorActivity : AppCompatActivity() {

    private var _binding: ActivityTaxCalculatorBinding? = null
    private val binding get() = _binding!!

    private val taxTypes = arrayOf("НДС", "НДФЛ (доход)", "Иные доходы")
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    // Уникальные ID для динамических View (гарантия отсутствия ошибок ClassCastException)
    private val ID_AMOUNT = View.generateViewId()
    private val ID_PERIOD = View.generateViewId()         // <-- НОВОЕ: Период (месяц/год)
    private val ID_RATE_SPINNER = View.generateViewId()
    private val ID_CUSTOM_RATE = View.generateViewId()
    private val ID_OPERATION_SPINNER = View.generateViewId()
    private val ID_INCOME_TYPE_SPINNER = View.generateViewId()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        _binding = ActivityTaxCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTaxTypeSpinner()
        
        binding.btnCalculate.setOnClickListener {
            calculateTax()
        }
    }

    private fun setupTaxTypeSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, taxTypes)
        binding.spinnerTaxType.adapter = adapter

        binding.spinnerTaxType.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                setupDynamicFields(taxTypes[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
    }

    private fun setupDynamicFields(taxType: String) {
        binding.layoutDynamicFields.removeAllViews()
        when (taxType) {
            "НДС" -> setupNdsFields()
            "НДФЛ (доход)" -> setupNdfFields()
            "Иные доходы" -> setupOtherIncomeFields()
        }
    }

    private fun setupNdsFields() {
        addLabel("Сумма (₽):")
        binding.layoutDynamicFields.addView(addEditText("100000", ID_AMOUNT, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL))

        addLabel("Ставка НДС:")
        val rates = arrayOf("20% (основная)", "10% (льготная)", "0% (экспорт)", "Своя ставка (%)")
        val spinnerRate = addSpinner(rates, ID_RATE_SPINNER)
        binding.layoutDynamicFields.addView(spinnerRate)

        val etCustomRate = addEditText("Например, 5", ID_CUSTOM_RATE, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        etCustomRate.visibility = View.GONE
        binding.layoutDynamicFields.addView(etCustomRate)

        spinnerRate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                etCustomRate.visibility = if (rates[position] == "Своя ставка (%)") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        addLabel("Операция:")
        val operations = arrayOf("Выделить НДС из суммы", "Начислить НДС на сумму")
        binding.layoutDynamicFields.addView(addSpinner(operations, ID_OPERATION_SPINNER))
    }

    private fun setupNdfFields() {
        addLabel("Сумма дохода (₽):")
        binding.layoutDynamicFields.addView(addEditText("100000", ID_AMOUNT, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL))

        // <-- НОВОЕ: Выбор периода (по умолчанию "В месяц")
        addLabel("Период дохода:")
        val periods = arrayOf("В месяц", "В год")
        binding.layoutDynamicFields.addView(addSpinner(periods, ID_PERIOD))

        addLabel("Режим расчёта:")
        val modes = arrayOf("Прогрессивная шкала (2024: 13%-22%)", "Своя ставка (%) (напр. 4%, 6%, 13%, 30%)")
        val spinnerMode = addSpinner(modes, ID_RATE_SPINNER)
        binding.layoutDynamicFields.addView(spinnerMode)

        val etCustomRate = addEditText("Например, 13", ID_CUSTOM_RATE, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        etCustomRate.visibility = View.GONE
        binding.layoutDynamicFields.addView(etCustomRate)

        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                etCustomRate.visibility = if (modes[position] == "Своя ставка (%) (напр. 4%, 6%, 13%, 30%)") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupOtherIncomeFields() {
        addLabel("Тип дохода:")
        val types = arrayOf("Дивиденды (резидент, 13%)", "Дивиденды (нерезидент, 15%)", "Выигрыш (35%)", "Продажа имущества (13%)", "Своя ставка (%)")
        binding.layoutDynamicFields.addView(addSpinner(types, ID_INCOME_TYPE_SPINNER))

        addLabel("Сумма дохода (₽):")
        binding.layoutDynamicFields.addView(addEditText("100000", ID_AMOUNT, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL))
        
        val etCustomRate = addEditText("Ваша ставка (%)", ID_CUSTOM_RATE, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        etCustomRate.visibility = View.GONE
        binding.layoutDynamicFields.addView(etCustomRate)

        val spinnerType = binding.layoutDynamicFields.findViewById<Spinner>(ID_INCOME_TYPE_SPINNER)
        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                etCustomRate.visibility = if (types[position] == "Своя ставка (%)") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // --- Вспомогательные функции для создания UI ---
    private fun addLabel(text: String) {
        val label = TextView(this).apply {
            this.text = text
            setTextColor(0xFF00FFFF.toInt())
            textSize = 16f
            setPadding(0, 16, 0, 4)
        }
        binding.layoutDynamicFields.addView(label)
    }

    private fun addEditText(hint: String, id: Int, inputType: Int): EditText {
        return EditText(this).apply {
            this.id = id
            this.hint = hint
            this.inputType = inputType
            setTextColor(0xFF00FFFF.toInt())
            setHintTextColor(0xFF008B8B.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FFFF.toInt())
            setPadding(0, 8, 0, 8)
        }
    }

    private fun addSpinner(items: Array<String>, id: Int): Spinner {
        return Spinner(this).apply {
            this.id = id
            adapter = ArrayAdapter(this@TaxCalculatorActivity, android.R.layout.simple_spinner_dropdown_item, items)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FFFF.toInt())
        }
    }

    // --- ЛОГИКА РАСЧЁТОВ ---
    private fun calculateTax() {
        when (binding.spinnerTaxType.selectedItem.toString()) {
            "НДС" -> calculateNds()
            "НДФЛ (доход)" -> calculateNdf()
            "Иные доходы" -> calculateOtherIncome()
        }
    }

    private fun calculateNds() {
        val amount = binding.layoutDynamicFields.findViewById<EditText>(ID_AMOUNT).text.toString().toDoubleOrNull()
        if (amount == null || amount <= 0) return showError("Введите корректную сумму > 0")

        val rateSpinner = binding.layoutDynamicFields.findViewById<Spinner>(ID_RATE_SPINNER)
        val rateStr = rateSpinner.selectedItem.toString()
        
        val rate = if (rateStr == "Своя ставка (%)") {
            val custom = binding.layoutDynamicFields.findViewById<EditText>(ID_CUSTOM_RATE).text.toString().toDoubleOrNull()
            if (custom == null || custom < 0 || custom > 100) return showError("Введите ставку от 0 до 100")
            custom
        } else {
            rateStr.substringBefore("%").toDouble()
        }

        val operation = binding.layoutDynamicFields.findViewById<Spinner>(ID_OPERATION_SPINNER).selectedItem.toString()
        
        val resultText = if (operation.contains("Выделить")) {
            val nds = amount * rate / (100 + rate)
            val withoutNds = amount - nds
            "= НДС ${rate}%:\n= Сумма с НДС: ${currencyFormat.format(amount)}\n= Сумма без НДС: ${currencyFormat.format(withoutNds)}\n= Выделенный НДС: ${currencyFormat.format(nds)}"
        } else {
            val nds = amount * rate / 100
            val total = amount + nds
            "= НДС ${rate}%:\n= Сумма без НДС: ${currencyFormat.format(amount)}\n= Начисленный НДС: ${currencyFormat.format(nds)}\n= Итого с НДС: ${currencyFormat.format(total)}"
        }
        binding.tvResult.text = resultText
    }

    private fun calculateNdf() {
        val incomeInput = binding.layoutDynamicFields.findViewById<EditText>(ID_AMOUNT).text.toString().toDoubleOrNull()
        if (incomeInput == null || incomeInput <= 0) return showError("Введите корректный доход > 0")

        val period = binding.layoutDynamicFields.findViewById<Spinner>(ID_PERIOD).selectedItem.toString()
        val mode = binding.layoutDynamicFields.findViewById<Spinner>(ID_RATE_SPINNER).selectedItem.toString()

        // Приводим всё к годовому доходу для корректного расчёта прогрессивной шкалы
        val annualIncome = if (period == "В месяц") incomeInput * 12.0 else incomeInput
        
        var annualTax = 0.0
        var description = ""

        if (mode == "Своя ставка (%) (напр. 4%, 6%, 13%, 30%)") {
            val custom = binding.layoutDynamicFields.findViewById<EditText>(ID_CUSTOM_RATE).text.toString().toDoubleOrNull()
            if (custom == null || custom < 0 || custom > 100) return showError("Введите ставку от 0 до 100")
            annualTax = annualIncome * (custom / 100.0)
            description = "НДФЛ (Своя ставка ${custom}%)"
        } else {
            description = "НДФЛ (Прогрессивная шкала 2024)"
            var remaining = annualIncome
            val b1 = minOf(remaining, 2_400_000.0); annualTax += b1 * 0.13; remaining -= b1
            if (remaining > 0) { val b2 = minOf(remaining, 2_600_000.0); annualTax += b2 * 0.15; remaining -= b2 }
            if (remaining > 0) { val b3 = minOf(remaining, 15_000_000.0); annualTax += b3 * 0.18; remaining -= b3 }
            if (remaining > 0) { val b4 = minOf(remaining, 30_000_000.0); annualTax += b4 * 0.20; remaining -= b4 }
            if (remaining > 0) { annualTax += remaining * 0.22 }
        }

        // Делим обратно на 12 для отображения месячных значений
        val monthlyIncome = annualIncome / 12.0
        val monthlyTax = annualTax / 12.0
        val monthlyAfterTax = monthlyIncome - monthlyTax
        val annualAfterTax = annualIncome - annualTax
        val effectiveRate = (annualTax / annualIncome) * 100

        val resultText = buildString {
            appendLine("= $description:")
            appendLine("= Доход в месяц: ${currencyFormat.format(monthlyIncome)}")
            appendLine("= Налог в месяц: ${currencyFormat.format(monthlyTax)}")
            appendLine("= На руки в месяц: ${currencyFormat.format(monthlyAfterTax)}")
            appendLine("----------------------------------------")
            appendLine("= Доход в год: ${currencyFormat.format(annualIncome)}")
            appendLine("= Налог в год: ${currencyFormat.format(annualTax)}")
            appendLine("= На руки в год: ${currencyFormat.format(annualAfterTax)}")
            appendLine("= Эффективная ставка: ${String.format(Locale.US, "%.2f", effectiveRate)}%")
        }
        binding.tvResult.text = resultText.trimEnd()
    }

    private fun calculateOtherIncome() {
        val amount = binding.layoutDynamicFields.findViewById<EditText>(ID_AMOUNT).text.toString().toDoubleOrNull()
        if (amount == null || amount <= 0) return showError("Введите корректную сумму > 0")

        val type = binding.layoutDynamicFields.findViewById<Spinner>(ID_INCOME_TYPE_SPINNER).selectedItem.toString()
        
        val (rate, desc) = if (type == "Своя ставка (%)") {
            val custom = binding.layoutDynamicFields.findViewById<EditText>(ID_CUSTOM_RATE).text.toString().toDoubleOrNull()
            if (custom == null || custom < 0 || custom > 100) return showError("Введите ставку от 0 до 100")
            custom to "Иной доход (Своя ставка ${custom}%)"
        } else {
            when (type) {
                "Дивиденды (резидент, 13%)" -> 13.0 to "Дивиденды (резидент)"
                "Дивиденды (нерезидент, 15%)" -> 15.0 to "Дивиденды (нерезидент)"
                "Выигрыш (35%)" -> 35.0 to "Выигрыш (реклама/конкурс)"
                "Продажа имущества (13%)" -> 13.0 to "Продажа имущества"
                else -> 13.0 to "Прочее"
            }
        }

        val tax = amount * (rate / 100.0)
        val afterTax = amount - tax

        binding.tvResult.text = "= $desc:\n= Доход: ${currencyFormat.format(amount)}\n= Налог: ${currencyFormat.format(tax)}\n= На руки: ${currencyFormat.format(afterTax)}"
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
