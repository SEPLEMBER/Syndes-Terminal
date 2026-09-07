package org.syndes.terminal

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.syndes.terminal.databinding.ActivityTravelTimeBinding

class TravelTimeActivity : AppCompatActivity() {

    private var _binding: ActivityTravelTimeBinding? = null
    private val binding get() = _binding!!

    // Типы транспорта с типичными скоростями (км/ч)
    private data class Transport(val name: String, val speed: Double?)
    
    private val transports = listOf(
        Transport("Пешком", 5.0),
        Transport("Бег", 10.0),
        Transport("Велосипед", 18.0),
        Transport("Самокат/Скейт", 12.0),
        Transport("Мопед/Скутер", 40.0),
        Transport("Мотоцикл", 80.0),
        Transport("Автомобиль (город)", 50.0),
        Transport("Автомобиль (трасса)", 90.0),
        Transport("Поезд", 100.0),
        Transport("Самолет", 800.0),
        Transport("Своя скорость", null)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        _binding = ActivityTravelTimeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        
        binding.btnCalculate.setOnClickListener {
            calculateTravelTime()
        }
    }

    private fun setupSpinner() {
        val transportNames = transports.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, transportNames)
        binding.spinnerTransport.adapter = adapter

        // Показываем/скрываем поле ввода своей скорости
        binding.spinnerTransport.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (transports[position].name == "Своя скорость") {
                    binding.layoutCustomSpeed.visibility = View.VISIBLE
                } else {
                    binding.layoutCustomSpeed.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun calculateTravelTime() {
        hideErrors()

        val distanceStr = binding.etDistance.text.toString()
        val selectedPosition = binding.spinnerTransport.selectedItemPosition
        val selectedTransport = transports[selectedPosition]

        // Валидация расстояния
        val distance = distanceStr.toDoubleOrNull()
        if (distance == null || distance <= 0) {
            showError(binding.errDistance, "Введите корректное расстояние > 0")
            return
        }

        // Получаем скорость
        val speed = if (selectedTransport.name == "Своя скорость") {
            val customSpeedStr = binding.etCustomSpeed.text.toString()
            val customSpeed = customSpeedStr.toDoubleOrNull()
            if (customSpeed == null || customSpeed <= 0) {
                showError(binding.errSpeed, "Введите корректную скорость > 0")
                return
            }
            customSpeed
        } else {
            selectedTransport.speed!!
        }

        // Расчет времени (часы)
        val timeInHours = distance / speed
        val hours = timeInHours.toInt()
        val minutes = ((timeInHours - hours) * 60).toInt()
        val seconds = (((timeInHours - hours) * 60 - minutes) * 60).toInt()

        // Формирование вывода
        val resultText = buildString {
            appendLine("= Расстояние: ${String.format("%.2f", distance)} км")
            appendLine("= Транспорт: ${selectedTransport.name}")
            appendLine("= Скорость: ${String.format("%.1f", speed)} км/ч")
            appendLine("= Время в пути:")
            appendLine("  ${formatTime(hours, minutes, seconds)}")
            appendLine("= Или: ${String.format("%.2f", timeInHours)} ч")
        }

        binding.tvResult.text = resultText.trimEnd()
    }

    private fun formatTime(hours: Int, minutes: Int, seconds: Int): String {
        return buildString {
            if (hours > 0) {
                append("$hours ${getPluralizedHours(hours)} ")
            }
            if (minutes > 0 || hours > 0) {
                append("$minutes ${getPluralizedMinutes(minutes)} ")
            }
            append("$seconds ${getPluralizedSeconds(seconds)}")
        }.trim()
    }

    private fun getPluralizedHours(value: Int): String {
        val mod100 = value % 100
        val mod10 = value % 10
        return when {
            mod100 in 11..14 -> "часов"
            mod10 == 1 -> "час"
            mod10 in 2..4 -> "часа"
            else -> "часов"
        }
    }

    private fun getPluralizedMinutes(value: Int): String {
        val mod100 = value % 100
        val mod10 = value % 10
        return when {
            mod100 in 11..14 -> "минут"
            mod10 == 1 -> "минута"
            mod10 in 2..4 -> "минуты"
            else -> "минут"
        }
    }

    private fun getPluralizedSeconds(value: Int): String {
        val mod100 = value % 100
        val mod10 = value % 10
        return when {
            mod100 in 11..14 -> "секунд"
            mod10 == 1 -> "секунда"
            mod10 in 2..4 -> "секунды"
            else -> "секунд"
        }
    }

    private fun showError(errorView: TextView, message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideErrors() {
        binding.errDistance.visibility = View.GONE
        binding.errSpeed.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
