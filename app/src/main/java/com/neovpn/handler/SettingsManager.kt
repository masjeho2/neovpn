package com.neovpn.handler

import android.content.Context
import com.neovpn.NeoApplication

object SettingsManager {

    private fun bool(key: String, def: Boolean = false) = MmkvManager.getSettingBool(key, def)
    private fun int(key: String, def: Int = 0) = MmkvManager.getSettingInt(key, def)
    private fun str(key: String, def: String = "") = MmkvManager.getSettingString(key, def)

    // General
    fun isSniffingEnabled() = bool("pref_sniffing", true)
    fun isAutoConnectBoot() = bool("pref_auto_connect_boot", false)

    // VPN
    fun isPreferIPv6() = bool("pref_prefer_ipv6", false)
    fun isBypassLan() = bool("pref_bypass_lan", false)
    fun isLocalDnsEnabled() = bool("pref_local_dns", true)
    fun getVpnMtu(): Int {
        val v = str("pref_vpn_mtu", "1500")
        return v.toIntOrNull() ?: 1500
    }
    fun getVpnDns() = str("pref_vpn_dns", "8.8.8.8")

    // Tunnel
    fun useHevTunnel() = bool("pref_use_hev_tunnel", true)
    fun getHevLogLevel() = str("pref_hev_log_level", "warn")

    // Core
    fun getSocksPort(): Int {
        val v = str("pref_socks_port", "10808")
        return v.toIntOrNull() ?: 10808
    }
    fun getHttpPort(): Int {
        val v = str("pref_http_port", "10809")
        return v.toIntOrNull() ?: 10809
    }
    fun getRemoteDns() = str("pref_remote_dns", "8.8.8.8")
    fun getDomesticDns() = str("pref_domestic_dns", "localhost")
    fun getCoreLogLevel() = str("pref_core_log_level", "warning")

    // Subscription
    fun isAutoUpdateSub() = bool("pref_auto_update_sub", false)
    fun getAutoUpdateInterval() = int("pref_auto_update_interval", 1440)

    // UI
    fun isShowSpeed() = bool("pref_show_speed", true)
    fun isConfirmRemove() = bool("pref_confirm_remove", true)
    fun getTheme() = str("pref_theme", "dark")

    // Advanced
    fun getDelayTestUrl() = str("pref_delay_test_url", "http://www.gstatic.com/generate_204")

    // Allow Insecure (always ON default)
    fun isAllowInsecure() = bool("pref_allow_insecure", true)
}
