package com.neovpn.util

import android.net.TrafficStats
import android.os.Handler
import android.os.Looper

class SpeedMonitor(
    private val onUpdate: (uploadSpeed: Long, downloadSpeed: Long, totalUp: Long, totalDown: Long) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastUpBytes = 0L
    private var lastDownBytes = 0L
    private var isRunning = false

    private val runnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val totalUp = TrafficStats.getTotalTxBytes()
            val totalDown = TrafficStats.getTotalRxBytes()

            val upSpeed = if (lastUpBytes > 0) (totalUp - lastUpBytes) else 0L
            val downSpeed = if (lastDownBytes > 0) (totalDown - lastDownBytes) else 0L

            lastUpBytes = totalUp
            lastDownBytes = totalDown

            onUpdate(upSpeed, downSpeed, totalUp, totalDown)
            handler.postDelayed(this, 1000)
        }
    }

    fun start() {
        isRunning = true
        lastUpBytes = TrafficStats.getTotalTxBytes()
        lastDownBytes = TrafficStats.getTotalRxBytes()
        handler.post(runnable)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(runnable)
        onUpdate(0, 0, 0, 0)
    }

    companion object {
        fun formatSpeed(bytesPerSec: Long): String {
            return when {
                bytesPerSec < 1024 -> "$bytesPerSec B/s"
                bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024} KB/s"
                else -> "${bytesPerSec / (1024 * 1024)} MB/s"
            }
        }

        fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
    }
}
