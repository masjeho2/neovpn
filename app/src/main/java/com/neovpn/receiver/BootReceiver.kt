package com.neovpn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neovpn.handler.MmkvManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val autoConnect = MmkvManager.getSettingBool("pref_auto_connect_boot", false)
            if (autoConnect) {
                val serviceIntent = Intent(context, com.neovpn.service.NeoVpnService::class.java)
                serviceIntent.action = com.neovpn.service.NeoVpnService.ACTION_START
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
