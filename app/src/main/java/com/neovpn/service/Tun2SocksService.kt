package com.neovpn.service

import android.util.Log
import com.neovpn.NeoApplication
import java.io.File
import java.io.FileOutputStream

object Tun2SocksService {

    private const val TAG = "Tun2Socks"
    private var context: android.content.Context? = null

    fun init(appContext: android.content.Context) {
        context = appContext
    }

    fun start(tunFd: Int, socksPort: Int = 10808) {
        try {
            val configContent = buildConfig(socksPort)
            val configFile = File(context?.filesDir, "hev-socks5-tunnel.yaml")
            FileOutputStream(configFile).use { it.write(configContent.toByteArray()) }

            Log.d(TAG, "Starting hev-socks5-tunnel via TProxyService")
            com.v2ray.ang.service.TProxyService.start(configFile.absolutePath, tunFd)
            Log.d(TAG, "hev-socks5-tunnel started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hev-socks5-tunnel", e)
        }
    }

    fun stop() {
        try {
            com.v2ray.ang.service.TProxyService.stop()
            Log.d(TAG, "hev-socks5-tunnel stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop hev-socks5-tunnel", e)
        }
    }

    private fun buildConfig(socksPort: Int): String {
        return buildString {
            appendLine("tunnel:")
            appendLine("  mtu: 1500")
            appendLine("  ipv4: 10.0.0.2")
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: 127.0.0.1")
            appendLine("  udp: 'udp'")
            appendLine("misc:")
            appendLine("  tcp-read-write-timeout: 300000")
            appendLine("  udp-read-write-timeout: 60000")
            appendLine("  log-level: warn")
        }
    }
}
