package com.neovpn.dto

data class SubscriptionItem(
    var guid: String = "",
    var remarks: String = "",
    var url: String = "",
    var enabled: Boolean = true,
    var autoUpdate: Boolean = false,
    var lastUpdate: Long = 0,
    var serverCount: Int = 0,
    var userAgent: String = "v2rayNG"
)
