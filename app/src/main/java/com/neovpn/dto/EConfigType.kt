package com.neovpn.dto

enum class EConfigType(val protocolScheme: String, val desc: String) {
    VLESS("vless://", "VLESS"),
    VMESS("vmess://", "VMess"),
    TROJAN("trojan://", "Trojan"),
    SHADOWSOCKS("ss://", "Shadowsocks"),
    HYSTERIA2("hy2://", "Hysteria2"),
    WIREGUARD("wireguard://", "WireGuard"),
    HTTP("http://", "HTTP"),
    SOCKS("socks://", "SOCKS"),
    CUSTOM("custom://", "Custom");

    companion object {
        fun fromScheme(scheme: String): EConfigType? {
            return entries.find { it.protocolScheme == scheme }
        }
    }
}
