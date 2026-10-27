package com.neovpn.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.neovpn.NeoApplication
import com.neovpn.R
import com.neovpn.handler.XrayController
import com.neovpn.handler.V2rayConfigManager
import com.neovpn.handler.MmkvManager
import com.neovpn.handler.SettingsManager
import com.neovpn.ui.MainActivity
import java.io.File
import java.io.FileOutputStream

class NeoVpnService : VpnService() {

    companion object {
        var isRunning = false
            private set
        var isConnecting = false
            private set

        const val ACTION_START = "com.neovpn.START_VPN"
        const val ACTION_STOP = "com.neovpn.STOP_VPN"
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        isConnecting = true

        // Start notification immediately (prevent ANR)
        startForeground(1, createNotification("Connecting..."))

        // Heavy work (Xray JNI, VPN establish, TProxy) in background thread
        Thread {
            try {
                val profile = MmkvManager.getSelectedServer()
                if (profile == null) {
                    Log.e(NeoApplication.TAG, "No server selected")
                    stopSelf()
                    return@Thread
                }

                // Start xray core
                XrayController.init(applicationContext)
                XrayController.setVpnService(this)
                if (!XrayController.start(profile, applicationContext)) {
                    Log.e(NeoApplication.TAG, "Failed to start xray core")
                    stopSelf()
                    return@Thread
                }

                val socksPort = SettingsManager.getSocksPort()
                val vpnDns = SettingsManager.getVpnDns()
                val mtu = SettingsManager.getVpnMtu()
                val hevLogLevel = SettingsManager.getHevLogLevel()

                // Write hev-socks5-tunnel config
                val configContent = """
tunnel:
  mtu: $mtu
  ipv4: 10.0.0.2
socks5:
  port: $socksPort
  address: 127.0.0.1
  udp: 'udp'
misc:
  tcp-read-write-timeout: 300000
  udp-read-write-timeout: 60000
  log-level: $hevLogLevel
"""
                val configFile = File(filesDir, "hev-socks5-tunnel.yaml")
                FileOutputStream(configFile).use { it.write(configContent.toByteArray()) }

                // Setup VPN interface
                val builder = Builder()
                    .setSession("NeoVPN")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(vpnDns)
                    .setMtu(mtu)

                // Exclude own app from VPN (prevent loop)
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.w(NeoApplication.TAG, "Could not disallow own package: ${e.message}")
                }

                // IPv6 support
                if (SettingsManager.isPreferIPv6()) {
                    builder.addRoute("::", 0)
                    builder.addDnsServer("2001:4860:4860::8888")
                }

                vpnInterface = builder.establish()

                val fd = vpnInterface?.fd ?: -1
                Log.d(NeoApplication.TAG, "VPN fd=$fd, socksPort=$socksPort, configFile=${configFile.absolutePath}")

                // Start hev-socks5-tunnel via JNI
                try {
                    com.v2ray.ang.service.TProxyService.start(configFile.absolutePath, fd)
                    Log.d(NeoApplication.TAG, "hev-socks5-tunnel started")
                } catch (e: Exception) {
                    Log.e(NeoApplication.TAG, "hev-socks5-tunnel failed: ${e.message}", e)
                }

                isConnecting = false
                isRunning = true

                startForeground(1, createNotification("Connected to ${profile.remarks}"))
                broadcastState("connected")

                Log.d(NeoApplication.TAG, "VPN started successfully")
            } catch (e: Exception) {
                Log.e(NeoApplication.TAG, "Failed to start VPN", e)
                stopVpn()
            }
        }.start()
    }

    private fun stopVpn() {
        try {
            try { com.v2ray.ang.service.TProxyService.stop() } catch (_: Exception) {}
            XrayController.stop()
            vpnInterface?.close()
            vpnInterface = null
            isRunning = false
            isConnecting = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            broadcastState("disconnected")
            Log.d(NeoApplication.TAG, "VPN stopped")
        } catch (e: Exception) {
            Log.e(NeoApplication.TAG, "Failed to stop VPN", e)
        }
    }

    private fun broadcastState(state: String) {
        try {
            val intent = Intent("com.neovpn.VPN_STATE_CHANGED")
            intent.setPackage(packageName)
            intent.putExtra("state", state)
            sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NeoApplication.CHANNEL_ID)
                .setContentTitle("NeoVPN")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_power)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("NeoVPN")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_power)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    override fun onRevoke() {
        super.onRevoke()
        stopVpn()
    }
}
