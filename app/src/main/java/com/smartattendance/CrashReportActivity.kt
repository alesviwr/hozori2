package com.smartattendance

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.system.exitProcess

/**
 * صفحهٔ نمایش متن کامل کرش برای دیباگ میدانی.
 * غیر از Hilt استفاده می‌کند تا در وضعیت خرابی process هم امن بالا بیاید.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent.getStringExtra(EXTRA_TRACE) ?: "no trace"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "خطای برنامه — متن زیر را کپی و ارسال کن"
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }

        val text = TextView(this).apply {
            text = trace
            textSize = 11f
            setTextIsSelectable(true)
        }

        val copy = Button(this).apply {
            text = "کپی متن خطا"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
                this@apply.text = "کپی شد ✔"
            }
        }

        val close = Button(this).apply {
            text = "بستن برنامه"
            setOnClickListener { finishAffinity(); exitProcess(0) }
        }

        root.addView(title)
        root.addView(
            ScrollView(this).apply { addView(text) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        root.addView(copy)
        root.addView(close)

        setContentView(root)
    }

    companion object {
        const val EXTRA_TRACE = "trace"
    }
}
