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
    
    // НОВЫЕ ID для редактируемых параметров
    private val ID_USN_RATE_6 = View.generateViewId()
    private val ID_USN_RATE_15 = View.generateViewId()
    private val ID_PATENT_RATE = View.generateViewId()
    private val ID_FIXED_PREMIUM = View.generateViewId()
    private val ID_ADDITIONAL_PERCENT = View.generateViewId()
    private val ID_ADDITIONAL_LIMIT = View.generateViewId()
    private val ID_PREMIUM_THRESHOLD = View.generateViewId()

    // Дефолтные значения 2024 года
    private val DEFAULT_FIXED_PREMIUM = 49_500.0
    private val DEFAULT_ADDITIONAL_PERCENT = 1.0
    private val DEFAULT_ADDITIONAL_LIMIT = 278_940.0
    private val DEFAULT_PREMIUM_THRESHOLD = 300_000.0
    private val DEFAULT_USN_RATE_6 = 6.0
    private val DEFAULT_USN_RATE_15 = 15.0
    private val DEFAULT_PATENT_RATE = 6.0

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

        addLabel("Параметры страховых взносов (можно изменить):")
        addLabel("Фиксированные взносы (₽):")
        binding.layoutDynamicFields.addView(
            addEditText(DEFAULT_FIXED_PREMIUM.toString(), ID_FIXED_PREMIUM, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )

        addLabel("Доп. взнос (% с превышения):")
        binding.layoutDynamicFields.addView(
            addEditText(DEFAULT_ADDITIONAL_PERCENT.toString(), ID_ADDITIONAL_PERCENT, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )

        addLabel("Порог для доп. взноса (₽):")
        binding.layoutDynamicFields.addView(
            addEditText(DEFAULT_PREMIUM_THRESHOLD.toString(), ID_PREMIUM_THRESHOLD, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )

        addLabel("Макс. доп. взнос (₽):")
        binding.layoutDynamicFields.addView(
            addEditText(DEFAULT_ADDITIONAL_LIMIT.toString(), ID_ADDITIONAL_LIMIT, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )
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

        // НОВОЕ: Редактируемые ставки УСН
        addLabel("Ставка УСН 6% (%):")
        binding.layoutDynamicFields.addView(
            addEditText(DEFAULT_USN_RATE_6.toString(), ID_USN_RATE_6, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )

        addLabel("Ставка УСН 15% (%):")
        binding.layoutDynamicFields.addView(
            addEditText(DEFAULT_USN_RATE_15.toString(), ID_USN_RATE_15, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )

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

        // НОВОЕ: Редактируемая ставка патента
        addLabel("Ставка патента (%):")
        binding.layoutDynamicFields.addView(
            addEditText(DEFAULT_PATENT_RATE.toString(), ID_PATENT_RATE, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
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

        // Читаем редактируемые параметры
        val fixedPremium = binding.layoutDynamicFields.findViewById<EditText>(ID_FIXED_PREMIUM).text.toString().toDoubleOrNull() ?: DEFAULT_FIXED_PREMIUM
        val additionalPercent = binding.layoutDynamicFields.findViewById<EditText>(ID_ADDITIONAL_PERCENT).text.toString().toDoubleOrNull() ?: DEFAULT_ADDITIONAL_PERCENT
        val premiumThreshold = binding.layoutDynamicFields.findViewById<EditText>(ID_PREMIUM_THRESHOLD).text.toString().toDoubleOrNull() ?: DEFAULT_PREMIUM_THRESHOLD
        val additionalLimit = binding.layoutDynamicFields.findViewById<EditText>(ID_ADDITIONAL_LIMIT).text.toString().toDoubleOrNull() ?: DEFAULT_ADDITIONAL_LIMIT

        val additionalPremium = if (income > premiumThreshold) {
            val calculated = (income - premiumThreshold) * (additionalPercent / 100.0)
            minOf(calculated, additionalLimit)
        } else {
            0.0
        }
        val totalPremium = fixedPremium + additionalPremium

        val resultText = buildString {
            appendLine("= Страховые взносы ИП:")
            appendLine("= Годовой доход: ${currencyFormat.format(income)}")
            appendLine("----------------------------------------")
            appendLine("= Фиксированные взносы: ${currencyFormat.format(fixedPremium)}")
            if (income > premiumThreshold) {
                appendLine("= Доп. взнос (${additionalPercent}% с превышения): ${currencyFormat.format(additionalPremium)}")
                appendLine("=   (с суммы ${currencyFormat.format(income - premiumThreshold)})")
            } else {
                appendLine("= Доп. взнос: 0 ₽ (доход ≤ ${currencyFormat.format(premiumThreshold)})")
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

        // Читаем редактируемые ставки
        val usnRate6 = binding.layoutDynamicFields.findViewById<EditText>(ID_USN_RATE_6).text.toString().toDoubleOrNull() ?: DEFAULT_USN_RATE_6
        val usnRate15 = binding.layoutDynamicFields.findViewById<EditText>(ID_USN_RATE_15).text.toString().toDoubleOrNull() ?: DEFAULT_USN_RATE_15

        var tax = 0.0
        var description = ""
        var expenses = 0.0

        when {
            mode.contains("6%") -> {
                description = "УСН ${usnRate6}% (Доходы)"
                tax = income * (usnRate6 / 100.0)
            }
            mode.contains("15%") -> {
                description = "УСН ${usnRate15}% (Доходы - Расходы)"
                expenses = binding.layoutDynamicFields.findViewById<EditText>(ID_EXPENSES).text.toString().toDoubleOrNull() ?: 0.0
                if (expenses < 0) return showError("Расходы не могут быть отрицательными")
                val profit = income - expenses
                if (profit <= 0) return showError("Расходы превышают доходы")
                tax = profit * (usnRate15 / 100.0)
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

        // Читаем редактируемую ставку патента
        val patentRate = binding.layoutDynamicFields.findViewById<EditText>(ID_PATENT_RATE).text.toString().toDoubleOrNull() ?: DEFAULT_PATENT_RATE

        val patentCost = potentialIncome * (patentRate / 100.0)

        // Уменьшение на страховые взносы
        val maxDeduction = if (hasEmployees) patentCost * 0.5 else patentCost
        val deduction = minOf(insurancePremium, maxDeduction)
        val patentToPay = patentCost - deduction

        val resultText = buildString {
            appendLine("= Патент (ПСН):")
            appendLine("= Потенциальный доход: ${currencyFormat.format(potentialIncome)}")
            appendLine("----------------------------------------")
            appendLine("= Стоимость патента (${patentRate}%): ${currencyFormat.format(patentCost)}")
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
