package org.syndes.terminal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SyPLComActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Подключаем XML-разметку
        setContentView(R.layout.activity_sy_pl_com)
    }
}
