package com.neovpn.util

import android.content.Context
import androidx.annotation.ColorRes
import com.neovpn.R
import com.neovpn.dto.QuotaData
import com.neovpn.dto.QuotaState
import java.util.Locale

/**
 * Helper format kuota untuk tampilan di ServerAdapter.
 * Design goal: hasil singkat (1 baris), koma desimal tetap (Locale.US).
 */
object QuotaFormat {

    /** Format bytes → string manusia. */
    fun formatBytes(bytes: Long?, decimals: Int = 2): String {
        if (bytes == null || bytes <= 0) return "0 B"
        val k = 1024.0
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val i = minOf(units.size - 1, (Math.log(bytes.toDouble()) / Math.log(k)).toInt())
        val raw = bytes / Math.pow(k, i.toDouble())
        return String.format(Locale.US, "%.${decimals}f %s", raw, units[i])
    }

    /**
     * Render teks ringkas untuk `tv_quota` di header.
     * String diambil dari resources supaya UI bisa dilokalkan.
     */
    fun displayText(context: Context, state: QuotaState, data: QuotaData?): String {
        return when (state) {
            QuotaState.LOADING -> context.getString(R.string.quota_loading)
            QuotaState.STALE -> context.getString(R.string.quota_stale)
            QuotaState.UNSUPPORTED -> ""
            QuotaState.INACTIVE -> context.getString(R.string.quota_inactive)
            QuotaState.UNAVAILABLE -> context.getString(R.string.quota_unavailable)
            QuotaState.OK, QuotaState.UNLIMITED -> {
                if (data == null) return "—"
                if (data.isUnlimited) return context.getString(R.string.quota_unlimited)
                val used = formatBytes(data.usedBytes)
                val total = formatBytes(data.quotaBytes)
                val pct = data.percentUsed
                if (pct != null) {
                    context.getString(R.string.quota_format, used, total, pct)
                } else {
                    context.getString(R.string.quota_format_nopct, used, total)
                }
            }
        }
    }

    @ColorRes
    fun colorRes(state: QuotaState, data: QuotaData?): Int {
        return when (state) {
            QuotaState.INACTIVE -> R.color.status_disconnected
            QuotaState.UNAVAILABLE -> R.color.status_connecting
            QuotaState.LOADING, QuotaState.STALE, QuotaState.UNSUPPORTED -> R.color.text_hint
            QuotaState.UNLIMITED -> R.color.status_connected
            QuotaState.OK -> {
                val pct = data?.percentUsed ?: 0.0
                when {
                    pct >= 90.0 -> R.color.status_disconnected
                    pct >= 75.0 -> R.color.status_connecting
                    else -> R.color.text_secondary
                }
            }
        }
    }
}
