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

    private val taxTypes = arrayOf("НДС", "НДФЛ (резидент РФ)", "НДФЛ (нерезидент)", "Иные доходы")
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

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
            "НДФЛ (резидент РФ)" -> setupNdfFields(isResident = true)
            "НДФЛ (нерезидент)" -> setupNdfFields(isResident = false)
            "Иные доходы" -> setupOtherIncomeFields()
        }
    }

    private fun setupNdsFields() {
        // Сумма
        addLabel("Сумма (₽):")
        val etAmount = addEditText("100000", android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        etAmount.id = View.generateViewId()
        binding.layoutDynamicFields.addView(etAmount)

        // Ставка НДС
        addLabel("Ставка НДС:")
        val spinnerRate = Spinner(this)
        val rates = arrayOf("20% (основная)", "10% (льготная)", "0% (экспорт)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rates)
        spinnerRate.adapter = adapter
        spinnerRate.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FFFF.toInt())
        binding.layoutDynamicFields.addView(spinnerRate)

        // Операция
        addLabel("Операция:")
        val spinnerOperation = Spinner(this)
        val operations = arrayOf("Выделить НДС из суммы", "Начислить НДС на сумму")
        val adapterOp = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, operations)
        spinnerOperation.adapter = adapterOp
        spinnerOperation.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FFFF.toInt())
        binding.layoutDynamicFields.addView(spinnerOperation)
    }

    private fun setupNdfFields(isResident: Boolean) {
        addLabel("Годовой доход до налогообложения (₽):")
        val etIncome = addEditText("2400000", android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        etIncome.id = View.generateViewId()
        binding.layoutDynamicFields.addView(etIncome)

        if (!isResident) {
            addLabel("Примечание: Для нерезидентов ставка 30% (кроме дивидендов)")
        }
    }

    private fun setupOtherIncomeFields() {
        addLabel("Тип дохода:")
        val spinnerIncomeType = Spinner(this)
        val incomeTypes = arrayOf("Дивиденды (резидент)", "Дивиденды (нерезидент)", "Выигрыш (реклама/конкурс)", "Продажа имущества")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, incomeTypes)
        spinnerIncomeType.adapter = adapter
        spinnerIncomeType.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FFFF.toInt())
        binding.layoutDynamicFields.addView(spinnerIncomeType)

        addLabel("Сумма дохода (₽):")
        val etAmount = addEditText("100000", android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        etAmount.id = View.generateViewId()
        binding.layoutDynamicFields.addView(etAmount)
    }

    private fun addLabel(text: String) {
        val label = TextView(this)
        label.text = text
        label.setTextColor(0xFF00FFFF.toInt())
        label.textSize = 16f
        label.setPadding(0, 8, 0, 4)
        binding.layoutDynamicFields.addView(label)
    }

    private fun addEditText(hint: String, inputType: Int): EditText {
        val editText = EditText(this)
        editText.hint = hint
        editText.inputType = inputType
        editText.setTextColor(0xFF00FFFF.toInt())
        editText.setHintTextColor(0xFF008B8B.toInt())
        editText.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FFFF.toInt())
        editText.setPadding(0, 8, 0, 8)
        return editText
    }

    private fun calculateTax() {
        val selectedTaxType = binding.spinnerTaxType.selectedItem.toString()

        when (selectedTaxType) {
            "НДС" -> calculateNds()
            "НДФЛ (резидент РФ)" -> calculateNdfResident()
            "НДФЛ (нерезидент)" -> calculateNdfNonResident()
            "Иные доходы" -> calculateOtherIncome()
        }
    }

    private fun calculateNds() {
        val views = binding.layoutDynamicFields
        val etAmount = views.getChildAt(1) as EditText
        val spinnerRate = views.getChildAt(2) as Spinner
        val spinnerOperation = views.getChildAt(4) as Spinner

        val amountStr = etAmount.text.toString()
        val amount = amountStr.toDoubleOrNull()
        
        if (amount == null || amount <= 0) {
            showError("Введите корректную сумму > 0")
            return
        }

        val rateStr = spinnerRate.selectedItem.toString()
        val rate = when {
            rateStr.startsWith("20%") -> 20.0
            rateStr.startsWith("10%") -> 10.0
            else -> 0.0
        }

        val operation = spinnerOperation.selectedItem.toString()
        
        val resultText = if (operation.contains("Выделить")) {
            val nds = amount * rate / (100 + rate)
            val amountWithoutNds = amount - nds
            buildString {
                appendLine("= НДС ${rate.toInt()}%:")
                appendLine("= Сумма с НДС: ${currencyFormat.format(amount)}")
                appendLine("= Сумма без НДС: ${currencyFormat.format(amountWithoutNds)}")
                appendLine("= Выделенный НДС: ${currencyFormat.format(nds)}")
            }
        } else {
            val nds = amount * rate / 100
            val totalWithNds = amount + nds
            buildString {
                appendLine("= НДС ${rate.toInt()}%:")
                appendLine("= Сумма без НДС: ${currencyFormat.format(amount)}")
                appendLine("= Начисленный НДС: ${currencyFormat.format(nds)}")
                appendLine("= Итого с НДС: ${currencyFormat.format(totalWithNds)}")
            }
        }

        binding.tvResult.text = resultText.trimEnd()
    }

    private fun calculateNdfResident() {
        val views = binding.layoutDynamicFields
        val etIncome = views.getChildAt(1) as EditText
        val incomeStr = etIncome.text.toString()
        val income = incomeStr.toDoubleOrNull()

        if (income == null || income <= 0) {
            showError("Введите корректный доход > 0")
            return
        }

        // Прогрессивная шкала НДФЛ 2024
        var tax = 0.0
        var remaining = income

        // До 2.4 млн - 13%
        val bracket1 = minOf(remaining, 2_400_000.0)
        tax += bracket1 * 0.13
        remaining -= bracket1

        // 2.4 - 5 млн - 15%
        if (remaining > 0) {
            val bracket2 = minOf(remaining, 2_600_000.0)
            tax += bracket2 * 0.15
            remaining -= bracket2
        }

        // 5 - 20 млн - 18%
        if (remaining > 0) {
            val bracket3 = minOf(remaining, 15_000_000.0)
            tax += bracket3 * 0.18
            remaining -= bracket3
        }

        // 20 - 50 млн - 20%
        if (remaining > 0) {
            val bracket4 = minOf(remaining, 30_000_000.0)
            tax += bracket4 * 0.20
            remaining -= bracket4
        }

        // Свыше 50 млн - 22%
        if (remaining > 0) {
            tax += remaining * 0.22
        }

        val afterTax = income - tax
        val effectiveRate = (tax / income) * 100

        val resultText = buildString {
            appendLine("= НДФЛ (резидент РФ):")
            appendLine("= Доход: ${currencyFormat.format(income)}")
            appendLine("= Налог: ${currencyFormat.format(tax)}")
            appendLine("= На руки: ${currencyFormat.format(afterTax)}")
            appendLine("= Эффективная ставка: ${String.format("%.2f", effectiveRate)}%")
        }

        binding.tvResult.text = resultText.trimEnd()
    }

    private fun calculateNdfNonResident() {
        val views = binding.layoutDynamicFields
        val etIncome = views.getChildAt(1) as EditText
        val incomeStr = etIncome.text.toString()
        val income = incomeStr.toDoubleOrNull()

        if (income == null || income <= 0) {
            showError("Введите корректный доход > 0")
            return
        }

        val tax = income * 0.30
        val afterTax = income - tax

        val resultText = buildString {
            appendLine("= НДФЛ (нерезидент):")
            appendLine("= Доход: ${currencyFormat.format(income)}")
            appendLine("= Налог (30%): ${currencyFormat.format(tax)}")
            appendLine("= На руки: ${currencyFormat.format(afterTax)}")
        }

        binding.tvResult.text = resultText.trimEnd()
    }

    private fun calculateOtherIncome() {
        val views = binding.layoutDynamicFields
        val spinnerIncomeType = views.getChildAt(1) as Spinner
        val etAmount = views.getChildAt(3) as EditText

        val amountStr = etAmount.text.toString()
        val amount = amountStr.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            showError("Введите корректную сумму > 0")
            return
        }

        val incomeType = spinnerIncomeType.selectedItem.toString()
        val (rate, description) = when (incomeType) {
            "Дивиденды (резидент)" -> 0.13 to "Дивиденды (резидент РФ)"
            "Дивиденды (нерезидент)" -> 0.15 to "Дивиденды (нерезидент)"
            "Выигрыш (реклама/конкурс)" -> 0.35 to "Выигрыш (реклама/конкурс)"
            "Продажа имущества" -> 0.13 to "Продажа имущества"
            else -> 0.13 to "Прочее"
        }

        val tax = amount * rate
        val afterTax = amount - tax

        val resultText = buildString {
            appendLine("= $description:")
            appendLine("= Доход: ${currencyFormat.format(amount)}")
            appendLine("= Налог (${(rate * 100).toInt()}%): ${currencyFormat.format(tax)}")
            appendLine("= На руки: ${currencyFormat.format(afterTax)}")
        }

        binding.tvResult.text = resultText.trimEnd()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
