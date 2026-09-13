package com.sslh.sshl.app.service

data class PayloadGeneratorOptions(
    val url: String = "id1.jagoanip.my.id",
    val method: String = "GET",
    val injectionMethod: String = "Normal",
    val keepAlive: Boolean = false,
    val userAgent: Boolean = false,
    val upgradeWebsocket: Boolean = true,
    val customHeader: Boolean = false,
    val referer: Boolean = false,
    val forwardedHost: Boolean = false,
    val backQuery: Boolean = false,
    val frontQuery: Boolean = false
)

object PayloadGenerator {

    fun parsePayload(
        payload: String,
        host: String,
        port: Int,
        statusLine: String = "HTTP/1.1 200 Connection established"
    ): String {
        val hostPort = if (port == 80 || port == 443) host else "$host:$port"
        val userAgentStr = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        var parsed = payload
        parsed = parsed.replace("[crlf]", "\r\n")
        parsed = parsed.replace("[cr]", "\r")
        parsed = parsed.replace("[lf]", "\n")
        parsed = parsed.replace("[protocol]", "HTTP/1.1")
        parsed = parsed.replace("[host]", host)
        parsed = parsed.replace("[port]", port.toString())
        parsed = parsed.replace("[host_port]", hostPort)
        parsed = parsed.replace("[ua]", userAgentStr)
        parsed = parsed.replace("[raw]", "CONNECT $hostPort HTTP/1.1\r\nHost: $hostPort\r\n")
        parsed = parsed.replace("[real_raw]", "CONNECT $hostPort HTTP/1.1\r\n")
        parsed = parsed.replace("[status]", statusLine)

        return parsed
    }

    fun generatePayload(options: PayloadGeneratorOptions): String {
        var host = options.url.trim()
        if (host.isEmpty()) host = "id1.jagoanip.my.id"

        val sb = StringBuilder()

        when (options.injectionMethod) {
            "Front Inject" -> {
                sb.append("${options.method} http://$host/ HTTP/1.1[crlf]")
                sb.append("Host: $host[crlf]")
            }
            "Back Inject" -> {
                sb.append("CONNECT [host_port] HTTP/1.1[crlf]")
                sb.append("Host: [host_port][crlf]")
                sb.append("${options.method} http://$host/ HTTP/1.1[crlf]")
            }
            else -> {
                // Normal
                sb.append("${options.method} / HTTP/1.1[crlf]")
                sb.append("Host: $host[crlf]")
            }
        }

        if (options.upgradeWebsocket) {
            sb.append("Upgrade: websocket[crlf]")
        }

        if (options.keepAlive) {
            sb.append("Connection: Keep-Alive[crlf]")
        }

        if (options.userAgent) {
            sb.append("User-Agent: [ua][crlf]")
        }

        if (options.referer) {
            sb.append("Referer: http://$host/[crlf]")
        }

        if (options.forwardedHost) {
            sb.append("X-Forwarded-Host: $host[crlf]")
        }

        sb.append("[crlf]")
        return sb.toString()
    }
}
