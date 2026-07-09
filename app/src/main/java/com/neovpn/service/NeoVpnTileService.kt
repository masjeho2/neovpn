package com.neovpn.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class NeoVpnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = NeoVpnService.isRunning

        if (isRunning) {
            // Stop VPN
            val intent = android.content.Intent(this, NeoVpnService::class.java).apply {
                action = NeoVpnService.ACTION_STOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            // Start VPN
            val intent = android.content.Intent(this, NeoVpnService::class.java).apply {
                action = NeoVpnService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        // Delay update tile
        android.os.Handler(mainLooper).postDelayed({
            updateTile()
        }, 1000)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRunning = NeoVpnService.isRunning

        if (isRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "NeoVPN ON"
            tile.contentDescription = "NeoVPN aktif, ketuk untuk matikan"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "NeoVPN OFF"
            tile.contentDescription = "NeoVPN mati, ketuk untuk nyalakan"
        }

        tile.updateTile()
    }
}
