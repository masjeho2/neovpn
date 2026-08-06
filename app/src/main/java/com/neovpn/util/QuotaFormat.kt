package com.neovpn.util

import com.neovpn.dto.QuotaData
import com.neovpn.dto.QuotaState
import java.util.Locale

/**
 * Helper format kuota untuk tampilan di ServerAdapter.
 * Design goal: hasil singkat (1 baris), bahasa Indonesia, koma desimal (id-ID style).
 */
object QuotaFormat {

    /** Format bytes → string manusia. Mengikuti format README_QUOTA_API.md. */
    fun formatBytes(bytes: Long?, decimals: Int = 2): String {
        if (bytes == null || bytes <= 0) return "0 B"
        val k = 1024.0
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val i = minOf(units.size - 1, (Math.log(bytes.toDouble()) / Math.log(k)).toInt())
        val raw = bytes / Math.pow(k, i.toDouble())
        // Locale.US → koma desimal konsisten (tidak ikut locale device, biar tidak pecah di beberapa device)
        return String.format(Locale.US, "%.${decimals}f %s", raw, units[i])
    }

    /**
     * Render teks ringkas untuk `tv_quota` di header.
     *
     * Contoh hasil:
     *   OK       → "99,88 GB / 100 GB (0,1%)"
     *   UNLIMITED→ "Tanpa batas"
     *   INACTIVE → "Akun tidak aktif"
     *   UNAVAILABLE → "Kuota tidak tersedia"
     *   STALE    → "Menyegarkan…"  (kalau ada cache sebelumnya, tapi expired)
     *   LOADING  → "Memuat…"
     *   UNSUPPORTED → ""  (sembunyikan via tv_quota.visibility = GONE)
     */
    fun displayText(state: QuotaState, data: QuotaData?): String {
        return when (state) {
            QuotaState.LOADING    -> "Memuat…"
            QuotaState.STALE      -> "Menyegarkan…"
            QuotaState.UNSUPPORTED -> ""  // tampilkan di-hidden, bukan tulisan
            QuotaState.INACTIVE   -> "Akun tidak aktif"
            QuotaState.UNAVAILABLE -> "Kuota tidak tersedia"
            QuotaState.OK, QuotaState.UNLIMITED -> {
                if (data == null) return "—"
                if (data.isUnlimited) return "Tanpa batas"
                val used = formatBytes(data.usedBytes)
                val total = formatBytes(data.quotaBytes)
                val pct = data.percentUsed
                return if (pct != null) {
                    String.format(Locale.US, "%s / %s (%.1f%%)", used, total, pct)
                } else {
                    "$used / $total"
                }
            }
        }
    }
}
