package com.neovpn.dto

data class ProfileItem(
    var guid: String = "",
    var remarks: String = "",
    var configType: EConfigType = EConfigType.VMESS,
    var subid: String = "",
    var address: String = "",
    var port: Int = 443,
    var id: String = "",
    var alterId: Int = 0,
    var security: String = "auto",
    var network: String = "tcp",
    var headerType: String = "",
    var host: String = "",
    var mode: String = "",
    var path: String = "",
    var tls: String = "",
    var sni: String = "",
    var alpn: String = "",
    var allowInsecure: Boolean = false,
    var fingerprint: String = "",
    var flow: String = "",
    var encryption: String = "",
    var protocol: String = "",
    var protocolSettings: String = "",
    // Subscription
    var subRemarks: String = "",
    var subUrl: String = "",
    // Stats
    // Stats
    var lastLatency: Int = 0,
    var lastSpeed: Long = 0,
    // Selected state
    var isSelected: Boolean = false
) {
    fun toUri(): String {
        return when (configType) {
            EConfigType.VMESS -> toVmessUri()
            EConfigType.VLESS -> toVlessUri()
            EConfigType.TROJAN -> toTrojanUri()
            EConfigType.SHADOWSOCKS -> toShadowsocksUri()
            else -> ""
        }
    }

    private fun toVmessUri(): String {
        val config = org.json.JSONObject().apply {
            put("v", "2")
            put("ps", remarks)
            put("add", address)
            put("port", port.toString())
            put("id", id)
            put("aid", alterId.toString())
            put("scy", security)
            put("net", network)
            put("type", headerType)
            put("host", host)
            put("path", path)
            put("tls", tls)
            put("sni", sni)
            put("alpn", alpn)
            put("fp", fingerprint)
        }
        return "vmess://" + android.util.Base64.encodeToString(
            config.toString().toByteArray(),
            android.util.Base64.NO_WRAP
        )
    }

    private fun toVlessUri(): String {
        val params = mutableListOf<String>()
        if (security.isNotEmpty()) params.add("security=$security")
        if (sni.isNotEmpty()) params.add("sni=$sni")
        if (fingerprint.isNotEmpty()) params.add("fp=$fingerprint")
        if (alpn.isNotEmpty()) params.add("alpn=$alpn")
        if (flow.isNotEmpty()) params.add("flow=$flow")
        if (network != "tcp") params.add("type=$network")
        if (host.isNotEmpty()) params.add("host=$host")
        if (path.isNotEmpty()) params.add("path=$path")
        val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return "vless://$id@$address:$port$query#${remarks}"
    }

    private fun toTrojanUri(): String {
        val params = mutableListOf<String>()
        if (sni.isNotEmpty()) params.add("sni=$sni")
        if (alpn.isNotEmpty()) params.add("alpn=$alpn")
        if (fingerprint.isNotEmpty()) params.add("fp=$fingerprint")
        if (network != "tcp") params.add("type=$network")
        if (host.isNotEmpty()) params.add("host=$host")
        if (path.isNotEmpty()) params.add("path=$path")
        val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return "trojan://$id@$address:$port$query#${remarks}"
    }

    private fun toShadowsocksUri(): String {
        val userInfo = android.util.Base64.encodeToString(
            "$protocol:$id".toByteArray(),
            android.util.Base64.NO_WRAP
        )
        return "ss://$userInfo@$address:$port#${remarks}"
    }
}
