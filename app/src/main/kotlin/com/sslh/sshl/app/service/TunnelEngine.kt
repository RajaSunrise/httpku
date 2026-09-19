package com.sslh.sshl.app.service

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.sslh.sshl.app.model.LogEntry
import com.sslh.sshl.app.model.TunnelConfig
import com.sslh.sshl.app.model.TunnelStatus
import com.sslh.sshl.app.model.TunnelType
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

class TunnelEngine {
    companion object {
        val instance: TunnelEngine by lazy { TunnelEngine() }
        private const val MAX_LOG_SIZE = 500
        const val SOCKS_PORT = 1080
        const val HTTP_PROXY_PORT = 7900
    }

    var config: TunnelConfig = TunnelConfig()
        private set

    var status: TunnelStatus = TunnelStatus.DISCONNECTED
        private set

    val logs = mutableListOf<LogEntry>()
    private var proxyExecutor: ExecutorService? = null
    var detectedIp: String = "10.193.165.137"
        private set

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    var listener: (() -> Unit)?
        get() = listeners.firstOrNull()
        set(value) {
            if (value != null) {
                if (!listeners.contains(value)) {
                    listeners.add(value)
                }
            }
        }

    fun addListener(l: () -> Unit) {
        if (!listeners.contains(l)) {
            listeners.add(l)
        }
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    var jschSession: Session? = null
        private set
    private var socksServerSocket: ServerSocket? = null
    private var httpProxyServerSocket: ServerSocket? = null

    @Volatile
    private var isRunning = false
    @Volatile
    private var shouldAutoReconnect = false

    val isConnected: Boolean get() = status == TunnelStatus.CONNECTED
    val isConnecting: Boolean get() = status == TunnelStatus.CONNECTING
    val isWaitingForNetwork: Boolean get() = status == TunnelStatus.WAITING_FOR_NETWORK

    fun onNetworkLost() {
        if (status == TunnelStatus.CONNECTED || status == TunnelStatus.CONNECTING) {
            shouldAutoReconnect = true
            status = TunnelStatus.WAITING_FOR_NETWORK
            addLog("No network connection available. Waiting for network...", isError = true)
            notifyListener()
        }
    }

    fun measurePing(targetHost: String, targetPort: Int = 80, onResult: (Long?) -> Unit) {
        thread {
            val hostToPing = if (targetHost.isBlank()) config.remoteAddr.ifBlank { "1.1.1.1" } else targetHost
            val portToPing = if (targetPort <= 0) 80 else targetPort
            try {
                val startTime = System.currentTimeMillis()
                val socket = Socket()
                HttpKuVpnService.instance?.protectSocket(socket)
                socket.connect(InetSocketAddress(hostToPing, portToPing), 5000)
                val latency = System.currentTimeMillis() - startTime
                socket.close()
                addLog("Ping to $hostToPing:$portToPing = ${latency}ms", isHighlight = true)
                onResult(latency)
            } catch (e: Exception) {
                addLog("Ping to $hostToPing:$portToPing failed: ${e.message}", isError = true)
                onResult(null)
            }
        }
    }

    fun exportConfigJson(): String {
        return config.toJson()
    }

    fun importConfigJson(jsonStr: String): Boolean {
        return try {
            val newCfg = TunnelConfig.fromJson(jsonStr)
            updateConfig(newCfg)
            addLog("Imported config '${newCfg.name}' successfully", isSuccess = true)
            true
        } catch (e: Exception) {
            addLog("Failed to import config: ${e.message}", isError = true)
            false
        }
    }

    fun onNetworkAvailable() {
        if (status == TunnelStatus.WAITING_FOR_NETWORK || shouldAutoReconnect) {
            shouldAutoReconnect = false
            addLog("Network connection restored. Reconnecting...", isHighlight = true)
            reconnectTunnel()
        }
    }

    fun reconnectTunnel() {
        stopTunnelInternal(clearAutoReconnect = false)
        startTunnel()
    }

    fun updateConfig(newConfig: TunnelConfig) {
        config = newConfig
        notifyListener()
    }

    fun addLog(
        message: String,
        isError: Boolean = false,
        isSuccess: Boolean = false,
        isHighlight: Boolean = false
    ) {
        synchronized(logs) {
            if (logs.size >= MAX_LOG_SIZE) {
                logs.removeAt(0)
            }
            logs.add(LogEntry(message, isError = isError, isSuccess = isSuccess, isHighlight = isHighlight))
        }
        notifyListener()
    }

    fun clearLogs() {
        synchronized(logs) {
            logs.clear()
        }
        notifyListener()
    }

    private fun notifyListener() {
        for (l in listeners) {
            try {
                l.invoke()
            } catch (_: Exception) {}
        }
    }

    fun refreshDetectedIp() {
        thread {
            val ip = IpService.getPublicIpV4()
            detectedIp = ip
            notifyListener()
        }
    }

    fun startTunnel() {
        if (status != TunnelStatus.DISCONNECTED) return

        status = TunnelStatus.CONNECTING
        isRunning = true
        notifyListener()

        addLog("Starting tunnel...")
        addLog("Mode: ${config.type.name}")

        thread {
            try {
                when (config.type) {
                    TunnelType.DNS -> {
                        addLog("DNS Server: ${config.dnsServer}:${config.dnsPort}")
                        val resolved = DnsService.queryDns(config.remoteAddr, config.dnsServer, config.dnsPort)
                        if (resolved != null) {
                            addLog("DNS Resolved ${config.remoteAddr} -> $resolved")
                        } else {
                            addLog("DNS resolution warning for ${config.remoteAddr}")
                        }
                    }
                    TunnelType.HAPROXY -> {
                        addLog("HAProxy mode enabled: Prepending PROXY TCP4 header")
                    }
                    TunnelType.SOCKS -> {
                        addLog("SOCKS proxy mode enabled: ${config.httpAddr}:${config.httpPort}")
                    }
                    else -> {}
                }

                connectTunnelInternal()

                status = TunnelStatus.CONNECTED
                addLog("SSH Authenticated successfully.", isHighlight = true)
                addLog("SOCKS5 tunnel running at 127.0.0.1:$SOCKS_PORT", isHighlight = true)
                addLog("Local HTTP proxy running at 127.0.0.1:$HTTP_PROXY_PORT")
                addLog("VPN connected", isSuccess = true)

                if (config.detectIpv4) {
                    refreshDetectedIp()
                }

                notifyListener()
            } catch (e: Exception) {
                addLog("Connection failed: ${e.message}", isError = true)
                stopTunnelInternal(clearAutoReconnect = true)
            }
        }
    }

    private fun connectTunnelInternal() {
        if (config.remoteAddr.isNotBlank()) {
            JSch.setLogger(object : com.jcraft.jsch.Logger {
                override fun isEnabled(level: Int): Boolean = true
                override fun log(level: Int, message: String) {
                    val isErr = level == com.jcraft.jsch.Logger.ERROR || level == com.jcraft.jsch.Logger.FATAL
                    addLog("[SSH Engine] $message", isError = isErr)
                }
            })

            val jsch = JSch()
            val user = if (config.remoteUsername.isEmpty()) "root" else config.remoteUsername
            val session = jsch.getSession(user, config.remoteAddr, config.remotePort)
            session.setPassword(config.remotePassword)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive,publickey")
            session.setTimeout(15000)
            session.setServerAliveInterval(15000)
            session.setServerAliveCountMax(3)

            val needProxy = (config.type == TunnelType.HTTP || config.type == TunnelType.SSL ||
                    config.type == TunnelType.HAPROXY || config.type == TunnelType.SOCKS) && config.httpAddr.isNotBlank()

            if (needProxy) {
                session.setProxy(CustomPayloadProxy(config, ::addLog))
            } else {
                session.setSocketFactory(object : SocketFactory {
                    override fun createSocket(host: String, port: Int): Socket {
                        val s = Socket()
                        s.tcpNoDelay = true
                        s.keepAlive = true
                        s.soTimeout = 30000
                        HttpKuVpnService.instance?.protectSocket(s)
                        s.connect(InetSocketAddress(host, port), 15000)
                        return s
                    }

                    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
                    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
                })
            }

            addLog("Connecting SSH to ${config.remoteAddr}:${config.remotePort}...")
            session.connect(15000)
            jschSession = session
        } else {
            addLog("No remote address specified. Running in standalone proxy mode.")
        }

        startLocalSocksServer()
        startLocalHttpProxyServer()
    }

    private fun startLocalSocksServer() {
        try {
            socksServerSocket?.close()
        } catch (_: Exception) {}

        if (proxyExecutor == null || proxyExecutor!!.isShutdown) {
            proxyExecutor = Executors.newCachedThreadPool()
        }

        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), SOCKS_PORT))
        socksServerSocket = server

        proxyExecutor?.execute {
            while (isRunning && !server.isClosed) {
                try {
                    val client = server.accept()
                    client.tcpNoDelay = true
                    proxyExecutor?.execute {
                        handleSocksClient(client)
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val bytesRead = input.read(buffer, offset, buffer.size - offset)
            if (bytesRead == -1) throw java.io.EOFException("Premature EOF reading SOCKS request")
            offset += bytesRead
        }
    }

    private fun handleSocksClient(client: Socket) {
        try {
            client.soTimeout = 30000
            val input = client.getInputStream()
            val output = client.getOutputStream()

            val ver = input.read()
            if (ver != 5) {
                client.close()
                return
            }

            val numMethods = input.read()
            if (numMethods <= 0) {
                client.close()
                return
            }
            val methods = ByteArray(numMethods)
            readFully(input, methods)

            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            val reqVer = input.read()
            val cmd = input.read()
            val rsv = input.read()
            val atyp = input.read()

            if (reqVer != 5 || cmd != 1) {
                output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()
                client.close()
                return
            }

            val targetHost: String = when (atyp) {
                1 -> {
                    val addr = ByteArray(4)
                    readFully(input, addr)
                    InetAddress.getByAddress(addr).hostAddress ?: "127.0.0.1"
                }
                3 -> {
                    val len = input.read()
                    if (len <= 0) { client.close(); return }
                    val hostBytes = ByteArray(len)
                    readFully(input, hostBytes)
                    String(hostBytes, Charsets.US_ASCII)
                }
                4 -> {
                    val addr = ByteArray(16)
                    readFully(input, addr)
                    InetAddress.getByAddress(addr).hostAddress ?: "::1"
                }
                else -> {
                    client.close()
                    return
                }
            }

            val p1 = input.read()
            val p2 = input.read()
            val targetPort = (p1 shl 8) or p2

            val session = jschSession
            if (session != null && session.isConnected) {
                val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
                channel.setHost(targetHost)
                channel.setPort(targetPort)

                val channelIn = channel.inputStream
                val channelOut = channel.outputStream

                channel.connect(10000)

                output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()

                val t1 = thread {
                    try {
                        input.copyTo(channelOut)
                    } catch (_: Exception) {}
                }
                val t2 = thread {
                    try {
                        channelIn.copyTo(output)
                    } catch (_: Exception) {}
                }
                t1.join()
                t2.join()
                channel.disconnect()
            } else {
                // Direct connection fallback if SSH is not active
                val targetSocket = Socket()
                HttpKuVpnService.instance?.protectSocket(targetSocket)
                targetSocket.connect(InetSocketAddress(targetHost, targetPort), 10000)

                output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()

                val t1 = thread {
                    try { input.copyTo(targetSocket.getOutputStream()) } catch (_: Exception) {}
                }
                val t2 = thread {
                    try { targetSocket.getInputStream().copyTo(output) } catch (_: Exception) {}
                }
                t1.join()
                t2.join()
                targetSocket.close()
            }
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun startLocalHttpProxyServer() {
        try {
            httpProxyServerSocket?.close()
        } catch (_: Exception) {}

        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), HTTP_PROXY_PORT))
        httpProxyServerSocket = server

        proxyExecutor?.execute {
            while (isRunning && !server.isClosed) {
                try {
                    val client = server.accept()
                    client.tcpNoDelay = true
                    proxyExecutor?.execute {
                        handleHttpProxyClient(client)
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun handleHttpProxyClient(client: Socket) {
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()

            val session = jschSession
            if (session != null && session.isConnected) {
                val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
                val destHost = if (config.remoteAddr.isNotBlank()) config.remoteAddr else "127.0.0.1"
                channel.setHost(destHost)
                channel.setPort(config.remotePort)

                val channelIn = channel.inputStream
                val channelOut = channel.outputStream
                channel.connect(10000)

                val t1 = thread {
                    try { input.copyTo(channelOut) } catch (_: Exception) {}
                }
                val t2 = thread {
                    try { channelIn.copyTo(output) } catch (_: Exception) {}
                }
                t1.join()
                t2.join()
                channel.disconnect()
            } else {
                client.close()
            }
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    fun stopTunnel() {
        stopTunnelInternal(clearAutoReconnect = true)
    }

    private fun stopTunnelInternal(clearAutoReconnect: Boolean) {
        if (clearAutoReconnect) {
            shouldAutoReconnect = false
        }
        status = TunnelStatus.DISCONNECTING
        isRunning = false
        notifyListener()

        try {
            socksServerSocket?.close()
        } catch (_: Exception) {}
        socksServerSocket = null

        try {
            httpProxyServerSocket?.close()
        } catch (_: Exception) {}
        httpProxyServerSocket = null

        try {
            jschSession?.disconnect()
        } catch (_: Exception) {}
        jschSession = null

        try {
            proxyExecutor?.shutdownNow()
        } catch (_: Exception) {}
        proxyExecutor = null

        if (clearAutoReconnect) {
            status = TunnelStatus.DISCONNECTED
            addLog("VPN disconnected.")
        }
        notifyListener()
    }
}

/**
 * Custom JSch Proxy implementation to support Bug Host, Payload Injection, SSL/TLS with SNI, and HAProxy header.
 */
class CustomPayloadProxy(
    private val config: TunnelConfig,
    private val logAction: (String) -> Unit
) : Proxy {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
        val proxyHost = if (config.httpAddr.isNotBlank()) config.httpAddr else host
        val proxyPort = if (config.type == TunnelType.HTTP || config.type == TunnelType.SOCKS || config.type == TunnelType.HAPROXY) config.httpPort else config.remotePort

        logAction("Connecting to proxy/bug host $proxyHost:$proxyPort...")

        var rawSocket = Socket()
        rawSocket.tcpNoDelay = true
        rawSocket.keepAlive = true
        rawSocket.soTimeout = timeout

        HttpKuVpnService.instance?.protectSocket(rawSocket)
        rawSocket.connect(InetSocketAddress(proxyHost, proxyPort), timeout)

        if (config.type == TunnelType.SOCKS) {
            logAction("Performing SOCKS5 proxy handshake for $host:$port...")
            val outS = rawSocket.getOutputStream()
            val inS = rawSocket.getInputStream()
            outS.write(byteArrayOf(0x05, 0x01, 0x00))
            outS.flush()

            val sVer = inS.read()
            val sMethod = inS.read()
            if (sVer != 5 || sMethod != 0) {
                rawSocket.close()
                throw java.io.IOException("SOCKS5 proxy authentication failed or unsupported")
            }

            val hostBytes = host.toByteArray(Charsets.US_ASCII)
            val req = ByteArray(7 + hostBytes.size)
            req[0] = 0x05
            req[1] = 0x01
            req[2] = 0x00
            req[3] = 0x03
            req[4] = hostBytes.size.toByte()
            System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
            req[5 + hostBytes.size] = (port ushr 8).toByte()
            req[6 + hostBytes.size] = (port and 0xFF).toByte()
            outS.write(req)
            outS.flush()

            val respBuf = ByteArray(10)
            var readLen = 0
            while (readLen < 10) {
                val r = inS.read(respBuf, readLen, 10 - readLen)
                if (r < 0) break
                readLen += r
            }
            if (readLen < 4 || respBuf[1].toInt() != 0) {
                rawSocket.close()
                throw java.io.IOException("SOCKS5 proxy failed to connect to target $host:$port")
            }
            logAction("SOCKS5 proxy connected successfully.")
        }

        if (config.type == TunnelType.SSL) {
            val sslContext = SSLContext.getInstance("TLS")
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
            sslContext.init(null, trustAll, java.security.SecureRandom())
            val sslFactory: SSLSocketFactory = sslContext.socketFactory
            val sslSocket = sslFactory.createSocket(rawSocket, proxyHost, proxyPort, true) as SSLSocket

            val params = sslSocket.sslParameters
            params.serverNames = listOf(SNIHostName(proxyHost))
            sslSocket.sslParameters = params
            sslSocket.startHandshake()
            rawSocket = sslSocket
            logAction("TLS Handshake completed with SNI: $proxyHost")
        }

        val out = rawSocket.getOutputStream()
        val inStream = rawSocket.getInputStream()

        if (config.type == TunnelType.HAPROXY) {
            val proxyHeader = "PROXY TCP4 127.0.0.1 $host 12345 $port\r\n"
            out.write(proxyHeader.toByteArray(Charsets.US_ASCII))
            out.flush()
            logAction("Sent HAProxy PROXY header")
        }

        val shouldInjectPayload = (config.type == TunnelType.HTTP || config.type == TunnelType.SSL) && config.customPayload && config.payload.isNotEmpty()

        if (shouldInjectPayload) {
            val parsedPayload = PayloadGenerator.parsePayload(
                config.payload,
                host = host,
                port = port,
                statusLine = config.customHttpResponse
            )
            logAction("Injecting payload:\n$parsedPayload")

            if (parsedPayload.contains("[split]")) {
                val parts = parsedPayload.split("[split]")
                for (i in parts.indices) {
                    val part = parts[i]
                    if (part.isNotEmpty()) {
                        out.write(part.toByteArray(Charsets.US_ASCII))
                        out.flush()
                    }
                    if (i < parts.size - 1) {
                        Thread.sleep(50)
                    }
                }
            } else {
                out.write(parsedPayload.toByteArray(Charsets.US_ASCII))
                out.flush()
            }

            val firstLine = readLine(inStream)
            val displayLine = if (config.replaceHttpResponse && config.customHttpResponse.isNotBlank()) {
                config.customHttpResponse
            } else {
                firstLine
            }
            logAction("HTTP Proxy Response: $displayLine")
            if (firstLine.isNotBlank()) {
                while (true) {
                    val line = readLine(inStream)
                    if (line.isEmpty()) break
                }
            }
        } else if (config.type == TunnelType.HTTP && !config.customPayload) {
            val defaultConnect = "CONNECT $host:$port HTTP/1.1\r\nHost: $host:$port\r\n\r\n"
            out.write(defaultConnect.toByteArray(Charsets.US_ASCII))
            out.flush()
            val firstLine = readLine(inStream)
            val displayLine = if (config.replaceHttpResponse && config.customHttpResponse.isNotBlank()) {
                config.customHttpResponse
            } else {
                firstLine
            }
            logAction("HTTP Proxy Response: $displayLine")
            if (firstLine.isNotBlank()) {
                while (true) {
                    val line = readLine(inStream)
                    if (line.isEmpty()) break
                }
            }
        }

        this.socket = rawSocket
        this.inputStream = inStream
        this.outputStream = out
    }

    private fun readLine(inStream: InputStream): String {
        val sb = StringBuilder()
        var b: Int
        while (inStream.read().also { b = it } != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }

    override fun getInputStream(): InputStream = inputStream ?: throw IllegalStateException("Socket not connected")
    override fun getOutputStream(): OutputStream = outputStream ?: throw IllegalStateException("Socket not connected")
    override fun getSocket(): Socket = socket ?: throw IllegalStateException("Socket not connected")

    override fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
    }
}
