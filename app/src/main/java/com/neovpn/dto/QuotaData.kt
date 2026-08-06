package com.neovpn.dto

import com.google.gson.annotations.SerializedName

/**
 * Response dari endpoint /quota di xray-singbox-mongodb.
 *
 * Sumber data: koleksi `users` MongoDB, dibaca tiap 1 menit oleh
 * jobs.js → checkAllUserQuotas(). Endpoint tidak butuh hit Xray Stats API
 * per request, jadi latency biasanya < 500 ms.
 *
 * Lihat README_QUOTA_API.md di server untuk field reference lengkap.
 */
data class QuotaData(
    @SerializedName("userId")          val userId: String = "",
    @SerializedName("quotaBytes")      val quotaBytes: Long = 0L,
    @SerializedName("usedBytes")       val usedBytes: Long = 0L,
    @SerializedName("remainingBytes")  val remainingBytesRaw: Any? = null,  // bisa Long atau null
    @SerializedName("percentUsed")     val percentUsedRaw: Any? = null,     // bisa Double atau null
    @SerializedName("expiresAt")       val expiresAt: String? = null,
    @SerializedName("lastChecked")     val lastChecked: String? = null,
) {
    /** Sisa kuota sebagai Long, atau null kalau tanpa batas (quotaBytes == 0). */
    val remainingBytes: Long?
        get() = when (val r = remainingBytesRaw) {
            null -> null
            is Number -> r.toLong()
            else -> null
        }

    /** Persentase penggunaan sebagai Double, atau null kalau tanpa batas / tidak valid. */
    val percentUsed: Double?
        get() = when (val p = percentUsedRaw) {
            is Number -> p.toDouble()
            else -> null
        }

    val isUnlimited: Boolean
        get() = quotaBytes <= 0L
}

/**
 * State hasil fetch kuota. Dipakai Adapter untuk pilih string & warna.
 */
enum class QuotaState {
    LOADING,      // pertama kali, cache miss, request jalan
    OK,           // 200 + data valid
    UNLIMITED,    // 200 + quotaBytes == 0
    INACTIVE,     // 404 — user tidak valid / banned / expired
    UNAVAILABLE,  // 503 / timeout / network error (no fallback)
    UNSUPPORTED,  // subscription URL tidak sesuai pola /quota — silent skip
    STALE         // pakai cache yang sudah expired (mis. > 60s)
}
