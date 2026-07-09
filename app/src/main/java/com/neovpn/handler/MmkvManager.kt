package com.neovpn.handler

import com.neovpn.dto.ProfileItem
import com.neovpn.dto.SubscriptionItem
import com.tencent.mmkv.MMKV
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object MmkvManager {

    private val mainStorage by lazy { MMKV.mmkvWithID("MAIN", MMKV.MULTI_PROCESS_MODE) }
    private val profileStorage by lazy { MMKV.mmkvWithID("PROFILE", MMKV.MULTI_PROCESS_MODE) }
    private val subStorage by lazy { MMKV.mmkvWithID("SUB", MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID("SETTING", MMKV.MULTI_PROCESS_MODE) }

    // ==================== Server List ====================

    fun getServerList(): MutableList<String> {
        val json = mainStorage?.decodeString("server_list") ?: return mutableListOf()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveServerList(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        mainStorage?.encode("server_list", arr.toString())
    }

    fun addServer(config: ProfileItem): String {
        if (config.guid.isEmpty()) {
            config.guid = UUID.randomUUID().toString()
        }
        encodeServerConfig(config.guid, config)
        val list = getServerList()
        list.add(config.guid)
        saveServerList(list)
        return config.guid
    }

    fun removeServer(guid: String) {
        profileStorage?.remove(guid)
        val list = getServerList().filter { it != guid }
        saveServerList(list)
    }

    fun removeServersBySubId(subId: String) {
        val list = getServerList()
        val toRemove = mutableListOf<String>()
        for (guid in list) {
            val config = decodeServerConfig(guid)
            if (config != null && config.subid == subId) {
                profileStorage?.remove(guid)
                toRemove.add(guid)
            }
        }
        val newList = list.filter { it !in toRemove }
        saveServerList(newList)
    }

    fun getServersBySubId(subId: String): List<ProfileItem> {
        val list = getServerList()
        return list.mapNotNull { decodeServerConfig(it) }
            .filter { it.subid == subId }
    }

    fun updateServer(config: ProfileItem) {
        encodeServerConfig(config.guid, config)
    }

    // ==================== Server Config ====================

    fun encodeServerConfig(guid: String, config: ProfileItem) {
        val json = JSONObject().apply {
            put("guid", config.guid)
            put("remarks", config.remarks)
            put("configType", config.configType.name)
            put("subid", config.subid)
            put("address", config.address)
            put("port", config.port)
            put("id", config.id)
            put("alterId", config.alterId)
            put("security", config.security)
            put("network", config.network)
            put("mode", config.mode)
            put("headerType", config.headerType)
            put("host", config.host)
            put("path", config.path)
            put("tls", config.tls)
            put("sni", config.sni)
            put("alpn", config.alpn)
            put("allowInsecure", config.allowInsecure)
            put("fingerprint", config.fingerprint)
            put("flow", config.flow)
            put("encryption", config.encryption)
            put("protocol", config.protocol)
            put("protocolSettings", config.protocolSettings)
            put("lastLatency", config.lastLatency)
        }
        profileStorage?.encode(guid, json.toString())
    }

    fun decodeServerConfig(guid: String): ProfileItem? {
        val json = profileStorage?.decodeString(guid) ?: return null
        return try {
            val obj = JSONObject(json)
            ProfileItem(
                guid = obj.optString("guid", guid),
                remarks = obj.optString("remarks", ""),
                configType = try { com.neovpn.dto.EConfigType.valueOf(obj.optString("configType", "VMESS")) } catch (e: Exception) { com.neovpn.dto.EConfigType.VMESS },
                subid = obj.optString("subid", ""),
                address = obj.optString("address", ""),
                port = obj.optInt("port", 443),
                id = obj.optString("id", ""),
                alterId = obj.optInt("alterId", 0),
                security = obj.optString("security", "auto"),
                network = obj.optString("network", "tcp"),
                headerType = obj.optString("headerType", ""),
                mode = obj.optString("mode", ""),
                host = obj.optString("host", ""),
                path = obj.optString("path", ""),
                tls = obj.optString("tls", ""),
                sni = obj.optString("sni", ""),
                alpn = obj.optString("alpn", ""),
                allowInsecure = obj.optBoolean("allowInsecure", false),
                fingerprint = obj.optString("fingerprint", ""),
                flow = obj.optString("flow", ""),
                encryption = obj.optString("encryption", ""),
                protocol = obj.optString("protocol", ""),
                protocolSettings = obj.optString("protocolSettings", ""),
                lastLatency = obj.optInt("lastLatency", 0)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getSelectedServer(): ProfileItem? {
        val guid = settingsStorage?.decodeString("selected_server") ?: return null
        return decodeServerConfig(guid)
    }

    fun setSelectedServer(guid: String) {
        settingsStorage?.encode("selected_server", guid)
    }

    // ==================== Subscription ====================

    fun getSubscriptionList(): MutableList<Pair<String, SubscriptionItem>> {
        val json = subStorage?.decodeString("sub_list") ?: return mutableListOf()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                val guid = obj.getString("guid")
                val item = SubscriptionItem(
                    guid = guid,
                    remarks = obj.optString("remarks", ""),
                    url = obj.optString("url", ""),
                    enabled = obj.optBoolean("enabled", true),
                    autoUpdate = obj.optBoolean("autoUpdate", false),
                    lastUpdate = obj.optLong("lastUpdate", 0),
                    serverCount = obj.optInt("serverCount", 0)
                )
                Pair(guid, item)
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveSubscription(sub: SubscriptionItem) {
        if (sub.guid.isEmpty()) {
            sub.guid = UUID.randomUUID().toString()
        }
        val list = getSubscriptionList().toMutableList()
        val index = list.indexOfFirst { it.first == sub.guid }
        val pair = Pair(sub.guid, sub)
        if (index >= 0) {
            list[index] = pair
        } else {
            list.add(pair)
        }
        val arr = JSONArray()
        list.forEach { (guid, item) ->
            arr.put(JSONObject().apply {
                put("guid", guid)
                put("remarks", item.remarks)
                put("url", item.url)
                put("enabled", item.enabled)
                put("autoUpdate", item.autoUpdate)
                put("lastUpdate", item.lastUpdate)
                put("serverCount", item.serverCount)
            })
        }
        subStorage?.encode("sub_list", arr.toString())
    }

    fun removeSubscription(guid: String) {
        val list = getSubscriptionList().filter { it.first != guid }
        val arr = JSONArray()
        list.forEach { (guid, item) ->
            arr.put(JSONObject().apply {
                put("guid", guid)
                put("remarks", item.remarks)
                put("url", item.url)
                put("enabled", item.enabled)
                put("autoUpdate", item.autoUpdate)
                put("lastUpdate", item.lastUpdate)
                put("serverCount", item.serverCount)
            })
        }
        subStorage?.encode("sub_list", arr.toString())
    }

    // ==================== Settings ====================

    fun setSetting(key: String, value: Any) {
        when (value) {
            is String -> settingsStorage?.encode(key, value)
            is Boolean -> settingsStorage?.encode(key, value)
            is Int -> settingsStorage?.encode(key, value)
            is Long -> settingsStorage?.encode(key, value)
        }
    }

    fun getSettingString(key: String, default: String = ""): String {
        return settingsStorage?.decodeString(key) ?: default
    }

    fun getSettingBool(key: String, default: Boolean = false): Boolean {
        return settingsStorage?.decodeBool(key) ?: default
    }

    fun getSettingInt(key: String, default: Int = 0): Int {
        return settingsStorage?.decodeInt(key) ?: default
    }
}
