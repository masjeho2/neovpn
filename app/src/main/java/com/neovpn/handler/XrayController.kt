package com.neovpn.handler

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.neovpn.NeoApplication
import com.neovpn.dto.ProfileItem
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.io.FileOutputStream

object XrayController {

    private var coreController: CoreController? = null
    private var isRunning = false
    private var appContext: Context? = null
    private var vpnService: VpnService? = null

    fun init(context: Context) {
        appContext = context
        try {
            Seq.setContext(context)
            Libv2ray.initCoreEnv(context.filesDir.absolutePath, "")
            coreController = Libv2ray.newCoreController(CoreCallback())
            Log.d(NeoApplication.TAG, "Xray core initialized")
        } catch (e: Exception) {
            Log.e(NeoApplication.TAG, "Failed to init xray core", e)
        }
    }

    fun setVpnService(service: VpnService) {
        vpnService = service
    }

    fun start(profile: ProfileItem, context: Context): Boolean {
        if (isRunning) return false

        return try {
            val config = V2rayConfigManager.generateConfig(context, profile)
            Log.d(NeoApplication.TAG, "Xray config:\n$config")
            Log.d(NeoApplication.TAG, "Starting xray core")
            coreController?.startLoop(config)
            isRunning = true
            Log.d(NeoApplication.TAG, "Xray core started")
            true
        } catch (e: Exception) {
            Log.e(NeoApplication.TAG, "Failed to start xray core", e)
            false
        }
    }

    fun stop(): Boolean {
        if (!isRunning) return false

        return try {
            coreController?.stopLoop()
            isRunning = false
            Log.d(NeoApplication.TAG, "Xray core stopped")
            true
        } catch (e: Exception) {
            Log.e(NeoApplication.TAG, "Failed to stop xray core", e)
            false
        }
    }

    fun isRunning(): Boolean = isRunning

    fun queryStats(tag: String, direct: String): Long {
        return try {
            coreController?.queryStats(tag, direct) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun measureDelay(url: String): Long {
        return try {
            coreController?.measureDelay(url) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private class CoreCallback : CoreCallbackHandler {
        override fun startup(): Long {
            Log.d(NeoApplication.TAG, "Xray core startup callback")
            return 0
        }

        override fun shutdown(): Long {
            Log.d(NeoApplication.TAG, "Xray core shutdown callback")
            isRunning = false
            return 0
        }

        override fun onEmitStatus(p0: Long, p1: String?): Long {
            Log.d(NeoApplication.TAG, "Xray status [$p0]: $p1")
            return 0
        }
    }
}
