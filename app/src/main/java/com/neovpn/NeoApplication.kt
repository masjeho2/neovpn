package com.neovpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.tencent.mmkv.MMKV

class NeoApplication : Application() {

    companion object {
        const val TAG = "NeoVPN"
        const val CHANNEL_ID = "neovpn_service"
        const val CHANNEL_NAME = "NeoVPN Service"
    }

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows VPN connection status"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
