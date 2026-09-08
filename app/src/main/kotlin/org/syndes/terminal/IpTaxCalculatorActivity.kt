package org.syndes.terminal

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.syndes.terminal.databinding.ActivityIpTaxCalculatorBinding
import java.text.NumberFormat
import java.util.Locale

class IpTaxCalculatorActivity : AppCompatActivity() {

    private var _binding: ActivityIpTaxCalculatorBinding? = null
    private val binding get() = _binding!!

    private val calcTypes = arrayOf("Страховые взносы ИП", "УСН", "Патент (ПСН)")

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    // Уникальные ID для динамических View
    private val ID_INCOME = View.generateViewId()
    private val ID_EXPENSES = View.generateViewId()
    private val ID_USN_MODE = View.generateViewId()
    private val ID_CUSTOM_RATE = View.generateViewId()
    private val ID_HAS_EMPLOYEES = View.generateViewId()
    private val ID_INSURANCE_PREMIUM = View.generateViewId()
    private val ID_POTENTIAL_INCOME = View.generateViewId()

    // Константы 2024 года
    private val FIXED_PREMIUM_2024 = 49_500.0
    private val ADDITIONAL_PREMIUM_MAX_2024 = 278_940.0
    private val PREMIUM_THRESHOLD = 300_000.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        _binding = ActivityIpTaxCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCalcTypeSpinner()

        binding.btnCalculate.setOnClickListener {
            calculate()
        }
    }

    private fun setupCalcTypeSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, calcTypes)
        binding.spinnerCalcType.adapter = adapter

        binding.spinnerCalcType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                setupDynamicFields(calcTypes[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDynamicFields(calcType: String) {
        binding.layoutDynamicFields.removeAllViews()
        when (calcType) {
            "Страховые взносы ИП" -> setupInsuranceFields()
            "УСН" -> setupUsnFields()
            "Патент (ПСН)" -> setupPatentFields()
        }
    }

    private fun setupInsuranceFields() {
        addLabel("Годовой доход ИП (₽):")
        binding.layoutDynamicFields.addView(
            addEditText("1000000", ID_INCOME, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )
        addLabel("Примечание: Фиксированные взносы 2024 = 49 500 ₽. Доп. взнос 1% с дохода свыше 300 000 ₽ (макс. 278 940 ₽)")
    }

    private fun setupUsnFields() {
        addLabel("Режим УСН:")
        val modes = arrayOf("УСН 6% (Доходы)", "УСН 15% (Доходы - Расходы)", "Своя ставка (%)")
        binding.layoutDynamicFields.addView(addSpinner(modes, ID_USN_MODE))

        addLabel("Годовой доход (₽):")
        binding.layoutDynamicFields.addView(
            addEditText("5000000", ID_INCOME, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )

        addLabel("Годовые расходы (₽) — только для УСН 15%:")
        val etExpenses = addEditText("2000000", ID_EXPENSES, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        etExpenses.visibility = View.GONE
        binding.layoutDynamicFields.addView(etExpenses)

        addLabel("Своя ставка (%) — только для режима «Своя ставка»:")
        val etCustomRate = addEditText("Например, 5", ID_CUSTOM_RATE, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        etCustomRate.visibility = View.GONE
        binding.layoutDynamicFields.addView(etCustomRate)

        addLabel("Годовые страховые взносы (₽):")
        binding.layoutDynamicFields.addView(
            addEditText("49500", ID_INSURANCE_PREMIUM, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )

        addLabel("Есть наёмные сотрудники?")
        val hasEmployeesOptions = arrayOf("Нет (можно уменьшить налог на 100% взносов)", "Да (максимум 50% от налога)")
        binding.layoutDynamicFields.addView(addSpinner(hasEmployeesOptions, ID_HAS_EMPLOYEES))

        // Динамическое управление видимостью полей
        val spinnerMode = binding.layoutDynamicFields.findViewById<Spinner>(ID_USN_MODE)
        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val mode = modes[position]
                etExpenses.visibility = if (mode.contains("15%")) View.VISIBLE else View.GONE
                etCustomRate.visibility = if (mode.contains("Своя")) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupPatentFields() {
        addLabel("Потенциальный годовой доход (₽):")
        binding.layoutDynamicFields.addView(
            addEditText("1000000", ID_POTENTIAL_INCOME, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )

        addLabel("Годовые страховые взносы (₽):")
        binding.layoutDynamicFields.addView(
            addEditText("49500", ID_INSURANCE_PREMIUM, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )

        addLabel("Есть наёмные сотрудники?")
        val hasEmployeesOptions = arrayOf("Нет (можно уменьшить налог на 100% взносов)", "Да (максимум 50% от налога)")
        binding.layoutDynamicFields.addView(addSpinner(hasEmployeesOptions, ID_HAS_EMPLOYEES))
    }

    // --- Вспомогательные функции UI ---
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
            adapter = ArrayAdapter(this@IpTaxCalculatorActivity, android.R.layout.simple_spinner_dropdown_item, items)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FFFF.toInt())
        }
    }

    // --- ЛОГИКА РАСЧЁТОВ ---
    private fun calculate() {
        when (binding.spinnerCalcType.selectedItem.toString()) {
            "Страховые взносы ИП" -> calculateInsurance()
            "УСН" -> calculateUsn()
            "Патент (ПСН)" -> calculatePatent()
        }
    }

    private fun calculateInsurance() {
        val income = binding.layoutDynamicFields.findViewById<EditText>(ID_INCOME).text.toString().toDoubleOrNull()
        if (income == null || income < 0) return showError("Введите корректный доход >= 0")

        val fixedPremium = FIXED_PREMIUM_2024
        val additionalPremium = if (income > PREMIUM_THRESHOLD) {
            val calculated = (income - PREMIUM_THRESHOLD) * 0.01
            minOf(calculated, ADDITIONAL_PREMIUM_MAX_2024)
        } else {
            0.0
        }
        val totalPremium = fixedPremium + additionalPremium

        val resultText = buildString {
            appendLine("= Страховые взносы ИП (2024):")
            appendLine("= Годовой доход: ${currencyFormat.format(income)}")
            appendLine("----------------------------------------")
            appendLine("= Фиксированные взносы: ${currencyFormat.format(fixedPremium)}")
            if (income > PREMIUM_THRESHOLD) {
                appendLine("= Доп. взнос (1% с превышения): ${currencyFormat.format(additionalPremium)}")
                appendLine("=   (с суммы ${currencyFormat.format(income - PREMIUM_THRESHOLD)})")
            } else {
                appendLine("= Доп. взнос: 0 ₽ (доход ≤ 300 000 ₽)")
            }
            appendLine("----------------------------------------")
            appendLine("= ИТОГО взносов за год: ${currencyFormat.format(totalPremium)}")
            appendLine("= В месяц (≈): ${currencyFormat.format(totalPremium / 12.0)}")
            appendLine("= В квартал (≈): ${currencyFormat.format(totalPremium / 4.0)}")
        }
        binding.tvResult.text = resultText.trimEnd()
    }

    private fun calculateUsn() {
        val income = binding.layoutDynamicFields.findViewById<EditText>(ID_INCOME).text.toString().toDoubleOrNull()
        if (income == null || income <= 0) return showError("Введите корректный доход > 0")

        val mode = binding.layoutDynamicFields.findViewById<Spinner>(ID_USN_MODE).selectedItem.toString()
        val insurancePremium = binding.layoutDynamicFields.findViewById<EditText>(ID_INSURANCE_PREMIUM).text.toString().toDoubleOrNull() ?: 0.0
        val hasEmployees = binding.layoutDynamicFields.findViewById<Spinner>(ID_HAS_EMPLOYEES).selectedItem.toString().contains("Да")

        var tax = 0.0
        var description = ""
        var expenses = 0.0

        when {
            mode.contains("6%") -> {
                description = "УСН 6% (Доходы)"
                tax = income * 0.06
            }
            mode.contains("15%") -> {
                description = "УСН 15% (Доходы - Расходы)"
                expenses = binding.layoutDynamicFields.findViewById<EditText>(ID_EXPENSES).text.toString().toDoubleOrNull() ?: 0.0
                if (expenses < 0) return showError("Расходы не могут быть отрицательными")
                val profit = income - expenses
                if (profit <= 0) return showError("Расходы превышают доходы")
                tax = profit * 0.15
                // Минимальный налог 1% с доходов
                val minTax = income * 0.01
                if (tax < minTax) {
                    tax = minTax
                    description += " (применён мин. налог 1%)"
                }
            }
            mode.contains("Своя") -> {
                val customRate = binding.layoutDynamicFields.findViewById<EditText>(ID_CUSTOM_RATE).text.toString().toDoubleOrNull()
                if (customRate == null || customRate < 0 || customRate > 100) return showError("Введите ставку от 0 до 100")
                description = "УСН (Своя ставка ${customRate}%)"
                tax = income * (customRate / 100.0)
            }
        }

        // Уменьшение налога на страховые взносы
        val maxDeduction = if (hasEmployees) tax * 0.5 else tax
        val deduction = minOf(insurancePremium, maxDeduction)
        val taxToPay = tax - deduction

        val resultText = buildString {
            appendLine("= $description:")
            appendLine("= Доход за год: ${currencyFormat.format(income)}")
            if (mode.contains("15%")) {
                appendLine("= Расходы за год: ${currencyFormat.format(expenses)}")
                appendLine("= Прибыль: ${currencyFormat.format(income - expenses)}")
            }
            appendLine("----------------------------------------")
            appendLine("= Начисленный налог: ${currencyFormat.format(tax)}")
            appendLine("= Страховые взносы: ${currencyFormat.format(insurancePremium)}")
            appendLine("= Уменьшение на взносы: ${currencyFormat.format(deduction)}")
            if (hasEmployees && insurancePremium > tax * 0.5) {
                appendLine("=   (лимит 50% из-за сотрудников)")
            }
            appendLine("----------------------------------------")
            appendLine("= ИТОГ к уплате за год: ${currencyFormat.format(taxToPay)}")
            appendLine("= В квартал (≈): ${currencyFormat.format(taxToPay / 4.0)}")
            appendLine("= В месяц (≈): ${currencyFormat.format(taxToPay / 12.0)}")
            appendLine("= Эффективная ставка: ${String.format(Locale.US, "%.2f", (taxToPay / income) * 100)}%")
        }
        binding.tvResult.text = resultText.trimEnd()
    }

    private fun calculatePatent() {
        val potentialIncome = binding.layoutDynamicFields.findViewById<EditText>(ID_POTENTIAL_INCOME).text.toString().toDoubleOrNull()
        if (potentialIncome == null || potentialIncome <= 0) return showError("Введите корректный потенциальный доход > 0")

        val insurancePremium = binding.layoutDynamicFields.findViewById<EditText>(ID_INSURANCE_PREMIUM).text.toString().toDoubleOrNull() ?: 0.0
        val hasEmployees = binding.layoutDynamicFields.findViewById<Spinner>(ID_HAS_EMPLOYEES).selectedItem.toString().contains("Да")

        // Патент = 6% от потенциального дохода
        val patentCost = potentialIncome * 0.06

        // Уменьшение на страховые взносы
        val maxDeduction = if (hasEmployees) patentCost * 0.5 else patentCost
        val deduction = minOf(insurancePremium, maxDeduction)
        val patentToPay = patentCost - deduction

        val resultText = buildString {
            appendLine("= Патент (ПСН):")
            appendLine("= Потенциальный доход: ${currencyFormat.format(potentialIncome)}")
            appendLine("----------------------------------------")
            appendLine("= Стоимость патента (6%): ${currencyFormat.format(patentCost)}")
            appendLine("= Страховые взносы: ${currencyFormat.format(insurancePremium)}")
            appendLine("= Уменьшение на взносы: ${currencyFormat.format(deduction)}")
            if (hasEmployees && insurancePremium > patentCost * 0.5) {
                appendLine("=   (лимит 50% из-за сотрудников)")
            }
            appendLine("----------------------------------------")
            appendLine("= ИТОГ к уплате за год: ${currencyFormat.format(patentToPay)}")
            appendLine("= В месяц (≈): ${currencyFormat.format(patentToPay / 12.0)}")
            appendLine("= Эффективная ставка: ${String.format(Locale.US, "%.2f", (patentToPay / potentialIncome) * 100)}%")
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
