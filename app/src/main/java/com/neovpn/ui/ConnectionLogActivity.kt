package com.neovpn.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.neovpn.R
import java.io.File

class ConnectionLogActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var scrollView: ScrollView
    private val handler = Handler(Looper.getMainLooper())
    private val logBuilder = StringBuilder()
    private var updateRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection_log)

        tvLog = findViewById(R.id.tvLog)
        scrollView = findViewById(R.id.scrollView)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        addLog("=== NeoVPN Connection Log ===")
        addLog("")
        readXrayLogs()

        findViewById<android.widget.ImageButton>(R.id.btnRefresh).setOnClickListener {
            logBuilder.clear()
            tvLog.text = ""
            addLog("=== NeoVPN Connection Log ===")
            addLog("")
            readXrayLogs()
        }

        findViewById<android.widget.ImageButton>(R.id.btnClear).setOnClickListener {
            clearXrayLogs()
            logBuilder.clear()
            tvLog.text = ""
            addLog("=== NeoVPN Connection Log ===")
            addLog("")
            addLog("Log cleared")
        }
    }

    private fun readXrayLogs() {
        try {
            val externalDir = getExternalFilesDir(null)
            val accessLog = File(externalDir, "xray_access.log")
            val errorLog = File(externalDir, "xray_error.log")

            var hasLogs = false

            if (accessLog.exists() && accessLog.readText().isNotBlank()) {
                addLog("=== Access Log ===")
                addLog(accessLog.readText())
                hasLogs = true
            }

            if (errorLog.exists() && errorLog.readText().isNotBlank()) {
                addLog("=== Error Log ===")
                addLog(errorLog.readText())
                hasLogs = true
            }

            if (!hasLogs) {
                addLog("No xray logs found")
                addLog("")
                addLog("Tips:")
                addLog("1. Connect VPN first")
                addLog("2. Logs will appear here")
            }
        } catch (e: Exception) {
            addLog("Error reading logs: ${e.message}")
        }
    }

    private fun clearXrayLogs() {
        try {
            val externalDir = getExternalFilesDir(null)
            val accessLog = File(externalDir, "xray_access.log")
            val errorLog = File(externalDir, "xray_error.log")

            if (accessLog.exists()) accessLog.writeText("")
            if (errorLog.exists()) errorLog.writeText("")
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun addLog(message: String) {
        logBuilder.appendLine(message)
        tvLog.text = logBuilder.toString()
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
    }
}
