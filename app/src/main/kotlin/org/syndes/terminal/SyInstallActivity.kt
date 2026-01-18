package org.syndes.terminal

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SyInstallActivity : AppCompatActivity() {

    data class AppInfo(val displayName: String, val packageName: String, val url: String)

    private val apps = listOf(
        AppInfo("Terminal", "org.syndes.terminal", "https://example.com"),
        AppInfo("Syndes components", "org.syndes.kotlincomponents", "https://example.com"),
        AppInfo("Material files", "me.zhanghai.android.files", "https://f-droid.org/en/packages/me.zhanghai.android.files/"),
        AppInfo("KISS Launcher", "fr.neamar.kiss", "https://f-droid.org/en/packages/fr.neamar.kiss/"),
        AppInfo("Automations", "com.jens.automation2", "https://f-droid.org/en/packages/com.jens.automation2/"),
        AppInfo("Aves", "deckers.thibault.aves.libre", "https://f-droid.org/en/packages/deckers.thibault.aves.libre/"),
        AppInfo("Clock", "com.best.deskclock", "https://f-droid.org/en/packages/com.best.deskclock/"),
        AppInfo("Termux", "com.termux", "https://f-droid.org/en/packages/com.termux/"),
        AppInfo("Package manager", "com.smartpack.packagemanager", "https://f-droid.org/en/packages/com.smartpack.packagemanager/"),
        AppInfo("F-Droid", "org.fdroid.fdroid", "https://f-droid.org/en/packages/org.fdroid.fdroid/"),
        AppInfo("IzzyOnDroid", "in.sunilpaulmathew.izzyondroid", "https://f-droid.org/en/packages/in.sunilpaulmathew.izzyondroid/"),
        AppInfo("Tasks", "org.tasks", "https://f-droid.org/en/packages/org.tasks/"),
        AppInfo("Calculator", "org.solovyev.android.calculator", "https://f-droid.org/en/packages/org.solovyev.android.calculator/"),
        // Google Play: check package com.android.vending; if installed, show "Installed. 👍"
        AppInfo("Google Play", "com.android.vending", "https://play.google.com/store"),
        AppInfo("WebView", "com.google.android.webview", "https://play.google.com/store/apps/details?id=com.google.android.webview"),
        AppInfo("Keepass", "com.kunzisoft.keepass.libre", "https://f-droid.org/en/packages/com.kunzisoft.keepass.libre/"),
        AppInfo("Fennec (F-Droid)", "org.mozilla.fennec_fdroid", "https://f-droid.org/en/packages/org.mozilla.fennec_fdroid/"),
        AppInfo("Firefox", "org.mozilla.firefox", "https://play.google.com/store/apps/details?id=org.mozilla.firefox")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use the XML layout (see activity_syinstall.xml below)
        setContentView(R.layout.activity_syinstall)

        val container = findViewById<LinearLayout>(R.id.sy_container)

        apps.forEach { app ->
            val row = createAppRow(app)
            container.addView(row)
        }
    }

    private fun createAppRow(app: AppInfo): View {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density + 0.5f).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, dp(6), 0, dp(6))
            layoutParams = params
            // subtle background with alpha (still avoids drawables)
            setBackgroundColor(0x0AFFFFFF) // very faint overlay on black for separation
        }

        val title = TextView(this).apply {
            text = app.displayName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            setTextColor(0xFFFF00B8.toInt()) // #FF00B8 (kept bright pink)
            contentDescription = "${app.displayName} - ${app.packageName}"
        }

        val sub = TextView(this).apply {
            text = app.packageName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFFFFFFFF.toInt())
        }

        val action = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(8), 0, 0)
            gravity = Gravity.END
            // style like a link
            paint.isUnderlineText = true
            setTextColor(0xFFFF00B8.toInt()) // same pink for action links
        }

        val installed = isPackageInstalledSafe(app.packageName)

        if (app.displayName == "Google Play" && installed) {
            // Special requirement: if Play store installed, show exactly "Installed. 👍"
            action.text = "Installed. 👍"
            action.isClickable = false
        } else {
            if (installed) {
                action.text = "Open"
                action.setOnClickListener {
                    openApp(app.packageName)
                }
            } else {
                action.text = "Install"
                action.setOnClickListener {
                    openUrl(app.url)
                }
            }
        }

        // status small text at left (Installed / Not installed)
        val status = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val st = if (installed) "Installed" else "Not installed"
            text = st
            setTextColor(if (installed) 0xFF00FF7F.toInt() else 0xFFFFA07A.toInt()) // greenish / salmon
        }

        // horizontal layout for status + action
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = dp(6)
            layoutParams = p
            addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        row.addView(title)
        row.addView(sub)
        row.addView(bottom)

        return row
    }

    private fun isPackageInstalledSafe(pkg: String): Boolean {
        val packageName = pkg.trim()
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openApp(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg.trim())
        if (launch != null) {
            startActivity(launch)
        } else {
            // no launch intent, fall back to open package settings
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${pkg.trim()}")
                }
                startActivity(intent)
            } catch (ex: ActivityNotFoundException) {
                Toast.makeText(this, "Can't open app.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, "No browser to open link.", Toast.LENGTH_SHORT).show()
        }
    }
}
