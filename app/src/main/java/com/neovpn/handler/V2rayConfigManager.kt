package com.neovpn.handler

import android.content.Context
import com.neovpn.dto.EConfigType
import com.neovpn.dto.ProfileItem
import org.json.JSONArray
import org.json.JSONObject

object V2rayConfigManager {

    fun generateConfig(context: Context, profile: ProfileItem): String {
        val config = JSONObject()

        config.put("log", JSONObject().apply {
            put("loglevel", SettingsManager.getCoreLogLevel())
            put("access", context.getExternalFilesDir(null)?.absolutePath + "/xray_access.log")
            put("error", context.getExternalFilesDir(null)?.absolutePath + "/xray_error.log")
        })

        config.put("inbounds", getInbounds())
        config.put("outbounds", getOutbounds(profile))
        config.put("dns", getDns())
        config.put("routing", getRouting(profile))

        return config.toString(2)
    }

    private fun getInbounds(): JSONArray {
        val socksPort = SettingsManager.getSocksPort()
        val httpPort = SettingsManager.getHttpPort()

        return JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "socks-in")
                put("protocol", "socks")
                put("listen", "127.0.0.1")
                put("port", socksPort)
                put("settings", JSONObject().apply {
                    put("auth", "noauth")
                    put("udp", true)
                })
            })
            put(JSONObject().apply {
                put("tag", "http-in")
                put("protocol", "http")
                put("listen", "127.0.0.1")
                put("port", httpPort)
                put("settings", JSONObject().apply {
                    put("allowTransparent", false)
                })
            })
            // DNS inbound for hev-socks5-tunnel
            put(JSONObject().apply {
                put("tag", "dns-in")
                put("protocol", "dokodemo-door")
                put("listen", "127.0.0.1")
                put("port", 10853)
                put("settings", JSONObject().apply {
                    put("network", "tcp,udp")
                    put("port", 53)
                })
            })
        }
    }

    private fun getOutbounds(profile: ProfileItem): JSONArray {
        return JSONArray().apply {
            put(convertProfileToOutbound(profile))
            put(JSONObject().apply {
                put("tag", "dns-out")
                put("protocol", "dns")
            })
            put(JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
            })
            put(JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
            })
        }
    }

    private fun getDns(): JSONObject {
        val remoteDns = SettingsManager.getRemoteDns()
        val domesticDns = SettingsManager.getDomesticDns()

        return JSONObject().apply {
            put("servers", JSONArray().apply {
                put(remoteDns)
                put(JSONObject().apply {
                    put("address", domesticDns)
                    put("port", 10853)
                    put("domains", JSONArray().apply { put("geosite:cn") })
                })
            })
        }
    }

    private fun getRouting(profile: ProfileItem): JSONObject {
        val isIp = profile.address.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))

        return JSONObject().apply {
            put("domainStrategy", "AsIs")
            put("rules", JSONArray().apply {
                // DNS routing
                put(JSONObject().apply {
                    put("type", "field")
                    put("inboundTag", JSONArray().apply { put("dns-in") })
                    put("outboundTag", "dns-out")
                    put("port", "53")
                })
                // Bypass private IPs
                if (SettingsManager.isBypassLan()) {
                    put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "direct")
                        put("ip", JSONArray().apply { put("geoip:private") })
                    })
                }
                // Bypass server address
                if (isIp) {
                    put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "direct")
                        put("ip", JSONArray().apply { put(profile.address) })
                    })
                } else {
                    put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "direct")
                        put("domain", JSONArray().apply { put(profile.address) })
                    })
                }
            })
        }
    }

    private fun convertProfileToOutbound(profile: ProfileItem): JSONObject {
        return when (profile.configType) {
            EConfigType.VMESS -> toVmessOutbound(profile)
            EConfigType.VLESS -> toVlessOutbound(profile)
            EConfigType.TROJAN -> toTrojanOutbound(profile)
            EConfigType.SHADOWSOCKS -> toShadowsocksOutbound(profile)
            else -> toVmessOutbound(profile)
        }
    }

    private fun toVmessOutbound(profile: ProfileItem): JSONObject {
        val streamSettings = JSONObject()

        if (profile.network != "tcp") {
            streamSettings.put("network", profile.network)
            when (profile.network) {
                "ws" -> streamSettings.put("wsSettings", JSONObject().apply {
                    put("path", profile.path)
                    if (profile.host.isNotEmpty()) {
                        put("headers", JSONObject().apply { put("Host", profile.host) })
                    }
                })
                "grpc" -> streamSettings.put("grpcSettings", JSONObject().apply {
                    put("serviceName", profile.path)
                    put("multiMode", profile.mode == "multi")
                    put("idle_timeout", 60)
                    put("health_check_timeout", 20)
                })
                "h2" -> streamSettings.put("httpSettings", JSONObject().apply {
                    put("path", profile.path)
                    put("host", JSONArray().apply { put(profile.host) })
                })
            }
        }

        if (profile.tls == "tls") {
            streamSettings.put("security", "tls")
            val tlsSettings = JSONObject()
            if (profile.sni.isNotEmpty()) tlsSettings.put("serverName", profile.sni)
            if (profile.fingerprint.isNotEmpty()) tlsSettings.put("fingerprint", profile.fingerprint)
            if (profile.alpn.isNotEmpty()) {
                tlsSettings.put("alpn", JSONArray().apply {
                    profile.alpn.split(",").forEach { put(it.trim()) }
                })
            }
            if (SettingsManager.isAllowInsecure()) tlsSettings.put("allowInsecure", true)
            streamSettings.put("tlsSettings", tlsSettings)
        }

        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vmess")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", profile.address)
                        put("port", profile.port)
                        put("users", JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", profile.id)
                                put("alterId", profile.alterId)
                                put("security", profile.security)
                            })
                        })
                    })
                })
            })
            put("streamSettings", streamSettings)
        }
    }

    private fun toVlessOutbound(profile: ProfileItem): JSONObject {
        val streamSettings = JSONObject()

        if (profile.network != "tcp") {
            streamSettings.put("network", profile.network)
            when (profile.network) {
                "ws" -> streamSettings.put("wsSettings", JSONObject().apply {
                    put("path", profile.path)
                    if (profile.host.isNotEmpty()) {
                        put("headers", JSONObject().apply { put("Host", profile.host) })
                    }
                })
                "grpc" -> streamSettings.put("grpcSettings", JSONObject().apply {
                    put("serviceName", profile.path)
                    put("multiMode", profile.mode == "multi")
                    put("idle_timeout", 60)
                    put("health_check_timeout", 20)
                })
            }
        }

        if (profile.tls.isNotEmpty()) {
            streamSettings.put("security", profile.tls)
            val tlsSettings = JSONObject()
            if (profile.sni.isNotEmpty()) tlsSettings.put("serverName", profile.sni)
            if (profile.fingerprint.isNotEmpty()) tlsSettings.put("fingerprint", profile.fingerprint)
            if (SettingsManager.isAllowInsecure()) tlsSettings.put("allowInsecure", true)
            streamSettings.put("tlsSettings", tlsSettings)
        }

        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", profile.address)
                        put("port", profile.port)
                        put("users", JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", profile.id)
                                put("encryption", "none")
                                put("flow", profile.flow)
                            })
                        })
                    })
                })
            })
            put("streamSettings", streamSettings)
        }
    }

    private fun toTrojanOutbound(profile: ProfileItem): JSONObject {
        val streamSettings = JSONObject()

        if (profile.network != "tcp") {
            streamSettings.put("network", profile.network)
            when (profile.network) {
                "ws" -> streamSettings.put("wsSettings", JSONObject().apply {
                    put("path", profile.path)
                    if (profile.host.isNotEmpty()) {
                        put("headers", JSONObject().apply { put("Host", profile.host) })
                    }
                })
                "grpc" -> streamSettings.put("grpcSettings", JSONObject().apply {
                    put("serviceName", profile.path)
                    put("multiMode", profile.mode == "multi")
                    put("idle_timeout", 60)
                    put("health_check_timeout", 20)
                })
            }
        }

        streamSettings.put("security", "tls")
        val tlsSettings = JSONObject()
        if (profile.sni.isNotEmpty()) tlsSettings.put("serverName", profile.sni)
        if (profile.fingerprint.isNotEmpty()) tlsSettings.put("fingerprint", profile.fingerprint)
        if (SettingsManager.isAllowInsecure()) tlsSettings.put("allowInsecure", true)
        streamSettings.put("tlsSettings", tlsSettings)

        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "trojan")
            put("settings", JSONObject().apply {
                put("servers", JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", profile.address)
                        put("port", profile.port)
                        put("password", profile.id)
                    })
                })
            })
            put("streamSettings", streamSettings)
        }
    }

    private fun toShadowsocksOutbound(profile: ProfileItem): JSONObject {
        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "shadowsocks")
            put("settings", JSONObject().apply {
                put("servers", JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", profile.address)
                        put("port", profile.port)
                        put("method", profile.protocol)
                        put("password", profile.id)
                    })
                })
            })
        }
    }

    fun getSocksPort(): Int = SettingsManager.getSocksPort()
    fun getHttpPort(): Int = SettingsManager.getHttpPort()
}
