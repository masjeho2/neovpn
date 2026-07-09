package com.neovpn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neovpn.handler.MmkvManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val autoConnect = MmkvManager.getSettingBool("auto_connect", false)
            if (autoConnect) {
                val serviceIntent = Intent(context, com.neovpn.service.NeoVpnService::class.java)
                serviceIntent.action = com.neovpn.service.NeoVpnService.ACTION_START
                context.startService(serviceIntent)
            }
        }
    }
}
