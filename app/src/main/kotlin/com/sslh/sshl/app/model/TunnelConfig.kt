package com.sslh.sshl.app.model

import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TunnelConfig(
    val id: String = System.currentTimeMillis().toString(),
    val name: String = "default",
    val created: String = getCurrentDateString(),
    val lastModified: String = getCurrentDateString(),
    val expiration: String = "indeterminate",
    val remoteAddr: String = "id1.jagoanip.my.id",
    val remotePort: Int = 443,
    val remoteUsername: String = "",
    val remotePassword: String = "",
    val type: TunnelType = TunnelType.HTTP,
    val httpAddr: String = "bisnis.udemy.com",
    val httpPort: Int = 8080,
    val dnsServer: String = "1.1.1.1",
    val dnsPort: Int = 53,
    val dnsDomain: String = "tunnel.dns.com",
    val proxyAuthorization: Boolean = false,
    val replaceHttpResponse: Boolean = true,
    val customHttpResponse: String = "HTTP/1.1 200 Connection established",
    val customPayload: Boolean = true,
    val payload: String = "GET / HTTP/1.1[crlf]Host: id1.jagoanip.my.id[crlf]Upgrade: websocket[crlf][crlf]",
    val detectIpv4: Boolean = true
) {
    fun toJson(): String {
        return Gson().toJson(this)
    }

    companion object {
        private fun getCurrentDateString(): String {
            val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
            return sdf.format(Date())
        }

        fun fromJson(json: String): TunnelConfig {
            return try {
                Gson().fromJson(json, TunnelConfig::class.java)
            } catch (e: Exception) {
                TunnelConfig()
            }
        }
    }
}
