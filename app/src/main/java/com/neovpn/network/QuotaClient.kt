package com.neovpn.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.neovpn.dto.QuotaData
import com.neovpn.dto.QuotaState
import com.neovpn.util.QuotaFormat
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Client ringan untuk endpoint `/quota` di server xray-singbox-mongodb.
 *
 * - Convert subscription URL (`https://host/<UUID_SECRET>/<username>/sub`)
 *   → endpoint URL (`…/<UUID_SECRET>/<username>/quota`).
 * - Cache hasil ke MMKV dengan key per-userId, TTL 60 detik.
 * - Silent fail: kalau URL subscription tidak sesuai pola, kembalikan
 *   [Result.unsupported] (UI harus hide tv_quota, bukan crash).
 *
 * Backed by: OkHttp 4.12.0 (sudah di Gradle), Gson 2.11.0, MMKV 1.3.9.
 */
object QuotaClient {

    private const val TAG = "QuotaClient"

    /** Cache TTL: 60 detik, sesuai interval refresh jobs.js → checkAllUserQuotas(). */
    private const val CACHE_TTL_MS = 60_000L

    private const val HEADER_CACHE_NS = "quota"        // namespace MMKV
    private const val HEADER_KEY_LAST = "_last_fetch"   // key untuk timestamp cache

    /** Cache storage terpisah agar tidak polusi MAIN/SUB storage. */
    private val cacheStorage: MMKV? by lazy {
        MMKV.mmkvWithID(HEADER_CACHE_NS, MMKV.MULTI_PROCESS_MODE)
    }

    /** Shared client OkHttp. Timeout 6 detik — quota endpoint harus cepat. */
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private val gson by lazy { Gson() }

    /**
     * Hasil fetch kuota. Selalu ada `state`; `data` null kalau state bukan OK/UNLIMITED/STALE.
     * `userId` selalu ada kalau subscription URL valid — dipakai cache key.
     */
    data class Result(
        val state: QuotaState,
        val data: QuotaData? = null,
        val userId: String? = null,
    ) {
        val isUsable: Boolean
            get() = state == QuotaState.OK ||
                    state == QuotaState.UNLIMITED ||
                    state == QuotaState.STALE ||
                    state == QuotaState.INACTIVE ||     // kita tampilkan 'Akun tidak aktif' dari cache kalau ada
                    state == QuotaState.UNSUPPORTED
    }

    /**
     * Fetch kuota untuk subscription URL tertentu.
     *
     * Algoritma:
     * 1. Parse subscription URL → cek apakah sesuai pola. Kalau tidak → [Result.unsupported].
     * 2. Cek cache MMKV (`data + timestamp`). Kalau age < 60s → pakai cache, kembalikan [stale? no, fresh].
     * 3. Kalau cache miss / expired → hit network. Sukses 200 → simpan cache + return. Gagal →
     *    kalau cache ada → kembalikan cache sebagai [stale]. Kalau tidak ada → [unavailable].
     */
    suspend fun fetchQuota(context: Context, subscriptionUrl: String): Result = withContext(Dispatchers.IO) {
        // 1. Parse subscription URL
        val parsed = parseQuotaUrl(subscriptionUrl)
        if (parsed == null) {
            return@withContext Result(state = QuotaState.UNSUPPORTED)
        }
        val (quotaUrl, userId) = parsed

        // 2. Cek cache
        val cached = readCache(userId)
        if (cached != null) {
            val ageMs = System.currentTimeMillis() - cached.timestampMs
            if (ageMs < CACHE_TTL_MS) {
                // Fresh enough → pakai cache
                Log.d(TAG, "cache HIT (age=${ageMs}ms) user=$userId")
                return@withContext toResult(cached.data, fresh = true)
            }
        }

        // 3. Hit network
        val networkResult = hitNetwork(quotaUrl)
        when {
            networkResult != null -> {
                writeCache(userId, networkResult)
                Log.d(TAG, "network OK user=$userId pct=${networkResult.percentUsed}")
                toResult(networkResult, fresh = true)
            }
            cached != null -> {
                Log.d(TAG, "network FAIL, pakai cache STALE user=$userId")
                toResult(cached.data, fresh = false)
            }
            else -> {
                Log.d(TAG, "network FAIL, no cache user=$userId")
                Result(state = QuotaState.UNAVAILABLE)
            }
        }
    }

    /** Ubah QuotaData + freshness jadi Result dengan state yang sesuai. */
    private fun toResult(data: QuotaData, fresh: Boolean): Result {
        val state = when {
            data.isUnlimited -> QuotaState.UNLIMITED
            fresh -> QuotaState.OK
            else -> QuotaState.STALE
        }
        return Result(state = state, data = data, userId = data.userId)
    }

    /**
     * Parse subscription URL → (quotaUrl, userId).
     *
     * Mendukung 2 format:
     * - `<scheme>://<host>/<UUID_SECRET>/<username>/sub` (3 segmen)
     * - `<scheme>://<host>/<UUID_SECRET>/<username>` (2 segmen, tanpa `/sub`)
     *
     * Kembalikan null kalau tidak sesuai → caller akan treat sebagai UNSUPPORTED.
     */
    internal fun parseQuotaUrl(subscriptionUrl: String): Pair<String, String>? {
        if (subscriptionUrl.isBlank()) return null
        val uri = runCatching { Uri.parse(subscriptionUrl) }.getOrNull() ?: return null
        val segments = uri.pathSegments ?: return null
        val size = segments.size

        // Format A: …/<UUID>/<username>/sub (3 segmen)
        // Format B: …/<UUID>/<username> (2 segmen)
        val uuid: String
        val username: String
        when {
            size == 3 && segments[2].lowercase() == "sub" -> {
                uuid = segments[0]
                username = segments[1]
            }
            size == 2 -> {
                uuid = segments[0]
                username = segments[1]
            }
            else -> return null
        }

        if (!isUuidShaped(uuid)) return null
        if (!isUsernameShaped(username)) return null

        // Bentuk ulang path → /<UUID>/<username>/quota
        val newPath = "/$uuid/$username/quota"
        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        val quotaUrl = "${uri.scheme}://${uri.host ?: return null}$portPart$newPath"
        Log.d(TAG, "parseQuotaUrl: $quotaUrl userId=$username")
        return quotaUrl to username
    }

    private fun isUuidShaped(s: String): Boolean {
        // longgar: terima hex+dash, panjang 32-64. Tidak validasi format UUID rigid
        // agar tidak gampang patah kalau ada variasi panjang secret.
        return s.length in 32..64 && s.all { it.isLetterOrDigit() || it == '-' }
    }

    private fun isUsernameShaped(s: String): Boolean =
        s.length in 3..32 && s.all {
            (it in 'a'..'z') || it.isDigit() || it == '.' || it == '_' || it == '-'
        }

    /** Hit endpoint /quota. Return QuotaData on 200, null otherwise. */
    private fun hitNetwork(quotaUrl: String): QuotaData? {
        return try {
            val req = Request.Builder()
                .url(quotaUrl)
                .header("Accept", "application/json")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                Log.d(TAG, "hitNetwork $quotaUrl → HTTP ${resp.code} body[0..120]=${body.take(120)}")
                when {
                    resp.code == 200 -> {
                        runCatching { gson.fromJson(body, QuotaData::class.java) }.getOrNull()
                    }
                    resp.code == 404 -> null  // user invalid → caller bisa pakai cache (kalau ada)
                    else -> null
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "network error: ${t.message}")
            null
        }
    }

    // ====================== MMKV Cache ======================

    private data class CachedQuota(val data: QuotaData, val timestampMs: Long)

    private fun readCache(userId: String): CachedQuota? {
        val storage = cacheStorage ?: return null
        val tsKey = "$userId${HEADER_KEY_LAST}"
        val ts = storage.decodeLong(tsKey, 0L)
        if (ts <= 0L) return null
        val json = storage.decodeString(userId, null) ?: return null
        val data = runCatching { gson.fromJson(json, QuotaData::class.java) }.getOrNull()
            ?: return null
        return CachedQuota(data, ts)
    }

    private fun writeCache(userId: String, data: QuotaData) {
        val storage = cacheStorage ?: return
        storage.encode(userId, gson.toJson(data))
        storage.encode("$userId${HEADER_KEY_LAST}", System.currentTimeMillis())
    }

    /**
     * Helper preview: ubah result jadi string siap-pakai di UI (untuk logging / future use).
     */
    fun preview(result: Result): String =
        QuotaFormat.displayText(result.state, result.data)
}
