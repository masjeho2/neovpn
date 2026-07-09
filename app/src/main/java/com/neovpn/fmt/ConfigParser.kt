package com.neovpn.fmt

import android.util.Base64
import com.neovpn.dto.EConfigType
import com.neovpn.dto.ProfileItem
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

object ConfigParser {

    fun parse(configStr: String): ProfileItem? {
        val str = configStr.trim()
        return when {
            str.startsWith(EConfigType.VMESS.protocolScheme) -> parseVmess(str)
            str.startsWith(EConfigType.VLESS.protocolScheme) -> parseVless(str)
            str.startsWith(EConfigType.TROJAN.protocolScheme) -> parseTrojan(str)
            str.startsWith(EConfigType.SHADOWSOCKS.protocolScheme) -> parseShadowsocks(str)
            str.startsWith(EConfigType.HYSTERIA2.protocolScheme) -> parseHysteria2(str)
            else -> null
        }
    }

    fun isConfigUri(text: String): Boolean {
        val schemes = listOf("vmess://", "vless://", "trojan://", "ss://", "hy2://", "hysteria2://")
        return schemes.any { text.trim().startsWith(it) }
    }

    fun parseBatch(configs: String): List<ProfileItem> {
        val results = mutableListOf<ProfileItem>()
        val lines = configs.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                parse(trimmed)?.let { results.add(it) }
            }
        }
        return results
    }

    fun parseBase64Content(content: String): List<ProfileItem> {
        return try {
            val decoded = String(Base64.decode(content, Base64.DEFAULT))
            parseBatch(decoded)
        } catch (e: Exception) {
            parseBatch(content)
        }
    }

    // ==================== VMess ====================

    private fun parseVmess(uri: String): ProfileItem? {
        return try {
            val base64Config = uri.removePrefix("vmess://")
            val jsonStr = String(Base64.decode(base64Config, Base64.DEFAULT))
            val json = JSONObject(jsonStr)

            ProfileItem(
                guid = UUID.randomUUID().toString(),
                remarks = json.optString("ps", "VMess Server"),
                configType = EConfigType.VMESS,
                address = json.optString("add", ""),
                port = json.optString("port", "443").toIntOrNull() ?: 443,
                id = json.optString("id", ""),
                alterId = json.optString("aid", "0").toIntOrNull() ?: 0,
                security = json.optString("scy", "auto"),
                network = json.optString("net", "tcp"),
                headerType = json.optString("type", ""),
                host = json.optString("host", ""),
                path = json.optString("path", "").ifEmpty { json.optString("serviceName", "") },
                tls = json.optString("tls", ""),
                sni = json.optString("sni", ""),
                alpn = json.optString("alpn", ""),
                fingerprint = json.optString("fp", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    // ==================== VLESS ====================

    private fun parseVless(uri: String): ProfileItem? {
        return try {
            val str = uri.removePrefix("vless://")
            val hashIndex = str.lastIndexOf('#')
            val remark = if (hashIndex > 0) URLDecoder.decode(str.substring(hashIndex + 1), "UTF-8") else "VLESS Server"
            val mainPart = if (hashIndex > 0) str.substring(0, hashIndex) else str

            val queryIndex = mainPart.indexOf('?')
            val userInfo = if (queryIndex > 0) mainPart.substring(0, queryIndex) else mainPart
            val queryString = if (queryIndex > 0) mainPart.substring(queryIndex + 1) else ""

            val atIndex = userInfo.indexOf('@')
            val id = if (atIndex > 0) userInfo.substring(0, atIndex) else userInfo
            val server = if (atIndex > 0) userInfo.substring(atIndex + 1) else ""
            val colonIndex = server.lastIndexOf(':')
            val address = if (colonIndex > 0) server.substring(0, colonIndex) else server
            val port = if (colonIndex > 0) server.substring(colonIndex + 1).toIntOrNull() ?: 443 else 443

            val params = parseQueryString(queryString)

            ProfileItem(
                guid = UUID.randomUUID().toString(),
                remarks = remark,
                configType = EConfigType.VLESS,
                address = address,
                port = port,
                id = id,
                security = params["security"] ?: "tls",
                network = params["type"] ?: "tcp",
                host = params["host"] ?: "",
                path = params["path"] ?: params["serviceName"] ?: "",
                tls = params["security"] ?: "tls",
                sni = params["sni"] ?: "",
                alpn = params["alpn"] ?: "",
                fingerprint = params["fp"] ?: "",
                flow = params["flow"] ?: "",
                allowInsecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true"
            )
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Trojan ====================

    private fun parseTrojan(uri: String): ProfileItem? {
        return try {
            val str = uri.removePrefix("trojan://")
            val hashIndex = str.lastIndexOf('#')
            val remark = if (hashIndex > 0) URLDecoder.decode(str.substring(hashIndex + 1), "UTF-8") else "Trojan Server"
            val mainPart = if (hashIndex > 0) str.substring(0, hashIndex) else str

            val queryIndex = mainPart.indexOf('?')
            val userInfo = if (queryIndex > 0) mainPart.substring(0, queryIndex) else mainPart
            val queryString = if (queryIndex > 0) mainPart.substring(queryIndex + 1) else ""

            val atIndex = userInfo.indexOf('@')
            val password = if (atIndex > 0) userInfo.substring(0, atIndex) else userInfo
            val server = if (atIndex > 0) userInfo.substring(atIndex + 1) else ""
            val colonIndex = server.lastIndexOf(':')
            val address = if (colonIndex > 0) server.substring(0, colonIndex) else server
            val port = if (colonIndex > 0) server.substring(colonIndex + 1).toIntOrNull() ?: 443 else 443

            val params = parseQueryString(queryString)

            ProfileItem(
                guid = UUID.randomUUID().toString(),
                remarks = remark,
                configType = EConfigType.TROJAN,
                address = address,
                port = port,
                id = password,
                security = "tls",
                network = params["type"] ?: "tcp",
                host = params["host"] ?: "",
                path = params["path"] ?: params["serviceName"] ?: "",
                tls = "tls",
                sni = params["sni"] ?: "",
                alpn = params["alpn"] ?: "",
                fingerprint = params["fp"] ?: "",
                allowInsecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true"
            )
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Shadowsocks ====================

    private fun parseShadowsocks(uri: String): ProfileItem? {
        return try {
            val str = uri.removePrefix("ss://")
            val hashIndex = str.lastIndexOf('#')
            val remark = if (hashIndex > 0) URLDecoder.decode(str.substring(hashIndex + 1), "UTF-8") else "SS Server"
            val mainPart = if (hashIndex > 0) str.substring(0, hashIndex) else str

            val atIndex = mainPart.indexOf('@')
            val userInfo: String
            val server: String
            if (atIndex > 0) {
                userInfo = mainPart.substring(0, atIndex)
                server = mainPart.substring(atIndex + 1)
            } else {
                val decoded = String(Base64.decode(mainPart, Base64.DEFAULT))
                val newAtIndex = decoded.indexOf('@')
                userInfo = decoded.substring(0, newAtIndex)
                server = decoded.substring(newAtIndex + 1)
            }

            val decodedUserInfo = try {
                String(Base64.decode(userInfo, Base64.DEFAULT))
            } catch (e: Exception) {
                userInfo
            }

            val colonIndex = decodedUserInfo.indexOf(':')
            val method = if (colonIndex > 0) decodedUserInfo.substring(0, colonIndex) else decodedUserInfo
            val password = if (colonIndex > 0) decodedUserInfo.substring(colonIndex + 1) else ""

            val serverColonIndex = server.lastIndexOf(':')
            val address = if (serverColonIndex > 0) server.substring(0, serverColonIndex) else server
            val port = if (serverColonIndex > 0) server.substring(serverColonIndex + 1).toIntOrNull() ?: 443 else 443

            ProfileItem(
                guid = UUID.randomUUID().toString(),
                remarks = remark,
                configType = EConfigType.SHADOWSOCKS,
                address = address,
                port = port,
                id = password,
                protocol = method,
                security = method
            )
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Hysteria2 ====================

    private fun parseHysteria2(uri: String): ProfileItem? {
        return try {
            val str = uri.removePrefix("hy2://")
            val hashIndex = str.lastIndexOf('#')
            val remark = if (hashIndex > 0) URLDecoder.decode(str.substring(hashIndex + 1), "UTF-8") else "Hysteria2 Server"
            val mainPart = if (hashIndex > 0) str.substring(0, hashIndex) else str

            val queryIndex = mainPart.indexOf('?')
            val userInfo = if (queryIndex > 0) mainPart.substring(0, queryIndex) else mainPart
            val queryString = if (queryIndex > 0) mainPart.substring(queryIndex + 1) else ""

            val atIndex = userInfo.indexOf('@')
            val password = if (atIndex > 0) userInfo.substring(0, atIndex) else userInfo
            val server = if (atIndex > 0) userInfo.substring(atIndex + 1) else ""
            val colonIndex = server.lastIndexOf(':')
            val address = if (colonIndex > 0) server.substring(0, colonIndex) else server
            val port = if (colonIndex > 0) server.substring(colonIndex + 1).toIntOrNull() ?: 443 else 443

            val params = parseQueryString(queryString)

            ProfileItem(
                guid = UUID.randomUUID().toString(),
                remarks = remark,
                configType = EConfigType.HYSTERIA2,
                address = address,
                port = port,
                id = password,
                security = "tls",
                sni = params["sni"] ?: "",
                fingerprint = params["insecure"] ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Helpers ====================

    private fun parseQueryString(query: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (query.isEmpty()) return params
        query.split("&").forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                params[URLDecoder.decode(parts[0], "UTF-8")] = URLDecoder.decode(parts[1], "UTF-8")
            }
        }
        return params
    }
}
