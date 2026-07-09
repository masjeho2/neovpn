package com.neovpn.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object ErrorHandler {

    fun showError(context: Context, title: String, message: String, onDismiss: (() -> Unit)? = null) {
        Log.e("NeoVPN", "$title: $message")
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> onDismiss?.invoke() }
            .setCancelable(false)
            .show()
    }

    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    fun handleVpnError(context: Context, error: Throwable) {
        val title: String
        val message: String

        when {
            error.message?.contains(" permission") == true -> {
                title = "Permission Error"
                message = "VPN permission is required to connect."
            }
            error.message?.contains("address") == true -> {
                title = "Server Error"
                message = "Invalid server address. Please check your configuration."
            }
            error.message?.contains("tls") == true || error.message?.contains("certificate") == true -> {
                title = "TLS Error"
                message = "TLS handshake failed. Check SNI and certificate settings."
            }
            error.message?.contains("timeout") == true -> {
                title = "Connection Timeout"
                message = "Could not connect to server. Check your internet connection and server address."
            }
            error.message?.contains("refused") == true -> {
                title = "Connection Refused"
                message = "Server refused the connection. Check server status."
            }
            error.message?.contains("DNS") == true || error.message?.contains("resolve") == true -> {
                title = "DNS Error"
                message = "Could not resolve server address. Check your DNS settings."
            }
            else -> {
                title = "Connection Error"
                message = error.message ?: "Unknown error occurred."
            }
        }

        showError(context, title, message)
    }

    fun handleImportError(context: Context, config: String) {
        when {
            config.isBlank() -> showToast(context, "Clipboard is empty")
            !config.contains("://") -> showError(context, "Import Error", "Invalid config format. Expected URI format (vmess://, vless://, etc.)")
            else -> showError(context, "Import Error", "Could not parse config. Please check the format.")
        }
    }
}
