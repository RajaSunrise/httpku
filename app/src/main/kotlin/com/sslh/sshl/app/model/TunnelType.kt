package com.sslh.sshl.app.model

enum class TunnelType(val displayName: String) {
    DIRECT("direct"),
    HTTP("http"),
    SSL("ssl"),
    SOCKS("socks"),
    DNS("dns"),
    HAPROXY("haproxy");

    companion object {
        fun fromString(value: String): TunnelType {
            return values().firstOrNull { it.displayName.equals(value, ignoreCase = true) } ?: HTTP
        }
    }
}
