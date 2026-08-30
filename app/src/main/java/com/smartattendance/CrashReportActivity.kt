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
 * عمداً بدون Hilt/Compose است تا در وضعیت خرابی process هم امن بالا بیاید.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent.getStringExtra(EXTRA_TRACE) ?: "no trace"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val titleView = TextView(this).apply {
            text = "خطای برنامه — متن زیر را کپی و ارسال کن"
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }

        val traceView = TextView(this).apply {
            text = trace
            textSize = 11f
            setTextIsSelectable(true)
        }

        val copyButton = Button(this)
        copyButton.text = "کپی متن خطا"
        copyButton.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
            copyButton.text = "کپی شد ✔"
        }

        val closeButton = Button(this)
        closeButton.text = "بستن برنامه"
        closeButton.setOnClickListener {
            finishAffinity()
            exitProcess(0)
        }

        root.addView(titleView)
        root.addView(
            ScrollView(this).apply { addView(traceView) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        root.addView(copyButton)
        root.addView(closeButton)

        setContentView(root)
    }

    companion object {
        const val EXTRA_TRACE = "trace"
    }
}
