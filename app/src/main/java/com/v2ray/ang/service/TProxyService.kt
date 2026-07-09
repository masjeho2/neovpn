package com.v2ray.ang.service

import android.util.Log
import java.io.File
import java.io.FileOutputStream

object TProxyService {

    private const val TAG = "TProxyService"

    @JvmStatic
    private external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    private external fun TProxyStopService()

    @JvmStatic
    private external fun TProxyGetStats(): LongArray?

    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    fun start(configPath: String, fd: Int) {
        try {
            Log.d(TAG, "Starting hev-socks5-tunnel: config=$configPath, fd=$fd")
            TProxyStartService(configPath, fd)
            Log.d(TAG, "hev-socks5-tunnel started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hev-socks5-tunnel", e)
        }
    }

    fun stop() {
        try {
            TProxyStopService()
            Log.d(TAG, "hev-socks5-tunnel stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop hev-socks5-tunnel", e)
        }
    }
}
