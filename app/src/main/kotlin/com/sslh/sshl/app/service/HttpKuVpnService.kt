package com.sslh.sshl.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.sslh.sshl.app.MainActivity

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import kotlin.concurrent.thread

class HttpKuVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.sslh.sshl.app.action.START"
        const val ACTION_STOP = "com.sslh.sshl.app.action.STOP"
        const val ACTION_RESTART = "com.sslh.sshl.app.action.RESTART"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "httpku_vpn_channel"
        var instance: HttpKuVpnService? = null
            private set
        val tunnelEngine: TunnelEngine get() = TunnelEngine.instance
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val engineListener = {
        updateNotification()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        registerNetworkMonitor()
        tunnelEngine.addListener(engineListener)
    }

    private fun registerNetworkMonitor() {
        try {
            connectivityManager = getSystemService(ConnectivityManager::class.java)
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    tunnelEngine?.onNetworkAvailable()
                }

                override fun onLost(network: Network) {
                    tunnelEngine?.onNetworkLost()
                }
            }

            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unregisterNetworkMonitor() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        networkCallback = null
    }

    private var startTimeMillis: Long = System.currentTimeMillis()
    private var timerHandler: android.os.Handler? = null
    private var timerRunnable: Runnable? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            tunnelEngine?.stopTunnel()
            stopVpn()
            return START_NOT_STICKY
        } else if (action == ACTION_RESTART) {
            tunnelEngine?.reconnectTunnel()
            startTimeMillis = System.currentTimeMillis()
            updateNotification()
            return START_STICKY
        }

        startTimeMillis = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        setupVpnInterface()
        startTimerUpdates()
        return START_STICKY
    }

    private fun startTimerUpdates() {
        timerHandler?.removeCallbacksAndMessages(null)
        timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    updateNotification()
                    timerHandler?.postDelayed(this, 1000)
                }
            }
        }
        timerHandler?.postDelayed(timerRunnable!!, 1000)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, createNotification())
    }

    @Volatile
    private var isRunning = false
    private var vpnThread: Thread? = null
    private var netExecutor: ExecutorService? = null
    private val tcpSessions = java.util.concurrent.ConcurrentHashMap<String, TcpSession>()

    private class TcpSession(
        val socket: java.net.Socket,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int
    ) {
        @Volatile
        var clientSeq: Long = 1L
        @Volatile
        var serverSeq: Long = 1000L
    }

    private fun setupVpnInterface() {
        try {
            vpnInterface?.close()
            val builder = Builder()
                .setSession("HttpKu VPN")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(1500)

            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {}

            vpnInterface = builder.establish()
            startPacketLoop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun protectSocket(socket: java.net.Socket): Boolean {
        return protect(socket)
    }

    fun protectDatagramSocket(socket: java.net.DatagramSocket): Boolean {
        return protect(socket)
    }

    private fun startPacketLoop() {
        isRunning = true
        netExecutor = Executors.newFixedThreadPool(8)
        vpnThread = kotlin.concurrent.thread(start = true, name = "HttpKuVpnPacketThread") {
            val pfd = vpnInterface ?: return@thread
            val inputStream = java.io.FileInputStream(pfd.fileDescriptor)
            val outputStream = java.io.FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteArray(32767)

            try {
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    val read = inputStream.read(buffer)
                    if (read < 20) {
                        Thread.sleep(10)
                        continue
                    }

                    val versionAndIhl = buffer[0].toInt() and 0xFF
                    val ipHeaderLen = (versionAndIhl and 0x0F) * 4
                    val protocol = buffer[9].toInt() and 0xFF

                    if (read >= ipHeaderLen + 8 && protocol == 17) { // UDP packet
                        val destPort = ((buffer[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (buffer[ipHeaderLen + 3].toInt() and 0xFF)
                        val udpHeaderLen = 8
                        val udpDataOffset = ipHeaderLen + udpHeaderLen
                        val udpDataLen = read - udpDataOffset

                        if (udpDataLen > 0) {
                            val packetCopy = ByteArray(read)
                            System.arraycopy(buffer, 0, packetCopy, 0, read)
                            netExecutor?.execute {
                                try {
                                    val udpPayload = ByteArray(udpDataLen)
                                    System.arraycopy(packetCopy, udpDataOffset, udpPayload, 0, udpDataLen)

                                    val destIpBytes = ByteArray(4)
                                    System.arraycopy(packetCopy, 16, destIpBytes, 0, 4)

                                    val customDns = tunnelEngine?.config?.dnsServer?.takeIf { it.isNotBlank() } ?: "1.1.1.1"
                                    val targetAddr = if (destPort == 53) java.net.InetAddress.getByName(customDns) else java.net.InetAddress.getByAddress(destIpBytes)
                                    val targetPort = if (destPort == 53) 53 else destPort

                                    val datagramSocket = java.net.DatagramSocket()
                                    protect(datagramSocket)
                                    datagramSocket.soTimeout = 10000

                                    val packet = java.net.DatagramPacket(udpPayload, udpPayload.size, targetAddr, targetPort)
                                    datagramSocket.send(packet)

                                    val recvBuf = ByteArray(4096)
                                    val recvPacket = java.net.DatagramPacket(recvBuf, recvBuf.size)
                                    datagramSocket.receive(recvPacket)
                                    datagramSocket.close()

                                    val respDnsLen = recvPacket.length
                                    val totalRespLen = ipHeaderLen + udpHeaderLen + respDnsLen
                                    val respPacket = ByteArray(totalRespLen)

                                    respPacket[0] = 0x45.toByte()
                                    respPacket[1] = 0.toByte()
                                    respPacket[2] = (totalRespLen ushr 8).toByte()
                                    respPacket[3] = (totalRespLen and 0xFF).toByte()
                                    respPacket[4] = packetCopy[4]
                                    respPacket[5] = packetCopy[5]
                                    respPacket[6] = 0x40.toByte()
                                    respPacket[7] = 0.toByte()
                                    respPacket[8] = 64.toByte()
                                    respPacket[9] = 17.toByte()

                                    System.arraycopy(packetCopy, 16, respPacket, 12, 4)
                                    System.arraycopy(packetCopy, 12, respPacket, 16, 4)

                                    val ipChecksum = calcIpChecksum(respPacket, 0, ipHeaderLen)
                                    respPacket[10] = (ipChecksum ushr 8).toByte()
                                    respPacket[11] = (ipChecksum and 0xFF).toByte()

                                    respPacket[ipHeaderLen] = packetCopy[ipHeaderLen + 2]
                                    respPacket[ipHeaderLen + 1] = packetCopy[ipHeaderLen + 3]
                                    respPacket[ipHeaderLen + 2] = packetCopy[ipHeaderLen]
                                    respPacket[ipHeaderLen + 3] = packetCopy[ipHeaderLen + 1]

                                    val udpLen = udpHeaderLen + respDnsLen
                                    respPacket[ipHeaderLen + 4] = (udpLen shr 8).toByte()
                                    respPacket[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()
                                    respPacket[ipHeaderLen + 6] = 0
                                    respPacket[ipHeaderLen + 7] = 0

                                    System.arraycopy(recvPacket.data, 0, respPacket, ipHeaderLen + udpHeaderLen, respDnsLen)

                                    synchronized(outputStream) {
                                        outputStream.write(respPacket)
                                        outputStream.flush()
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } else if (read >= ipHeaderLen + 20 && protocol == 6) { // TCP packet
                        val srcPort = ((buffer[ipHeaderLen].toInt() and 0xFF) shl 8) or (buffer[ipHeaderLen + 1].toInt() and 0xFF)
                        val destPort = ((buffer[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (buffer[ipHeaderLen + 3].toInt() and 0xFF)
                        val tcpHeaderLen = ((buffer[ipHeaderLen + 12].toInt() and 0xF0) ushr 4) * 4
                        val flags = buffer[ipHeaderLen + 13].toInt() and 0xFF

                        val srcIpBytes = ByteArray(4)
                        System.arraycopy(buffer, 12, srcIpBytes, 0, 4)
                        val dstIpBytes = ByteArray(4)
                        System.arraycopy(buffer, 16, dstIpBytes, 0, 4)

                        val srcIpStr = java.net.InetAddress.getByAddress(srcIpBytes).hostAddress ?: "10.0.0.2"
                        val dstIpStr = java.net.InetAddress.getByAddress(dstIpBytes).hostAddress ?: "0.0.0.0"

                        val payloadOffset = ipHeaderLen + tcpHeaderLen
                        val payloadLen = read - payloadOffset

                        val sessionKey = "$srcIpStr:$srcPort->$dstIpStr:$destPort"
                        val isSyn = (flags and 0x02) != 0
                        val isFin = (flags and 0x01) != 0
                        val isRst = (flags and 0x04) != 0

                        val clientIsn = ((buffer[ipHeaderLen + 4].toLong() and 0xFF) shl 24) or
                                ((buffer[ipHeaderLen + 5].toLong() and 0xFF) shl 16) or
                                ((buffer[ipHeaderLen + 6].toLong() and 0xFF) shl 8) or
                                (buffer[ipHeaderLen + 7].toLong() and 0xFF)

                        if (isSyn) {
                            netExecutor?.execute {
                                try {
                                    val destAddress = java.net.InetAddress.getByAddress(dstIpBytes)
                                    val useSocks = tunnelEngine?.status == com.sslh.sshl.app.model.TunnelStatus.CONNECTED && tunnelEngine?.jschSession?.isConnected == true
                                    val socket: java.net.Socket = if (useSocks) {
                                        // Route through SSH SOCKS 127.0.0.1:1080
                                        val s = java.net.Socket(java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", 1080)))
                                        protect(s)
                                        s.tcpNoDelay = true
                                        s.keepAlive = true
                                        s.soTimeout = 30000
                                        s
                                    } else {
                                        val s = java.net.Socket()
                                        protect(s)
                                        s.tcpNoDelay = true
                                        s.keepAlive = true
                                        s.soTimeout = 30000
                                        s
                                    }
                                    var connected = false
                                    try {
                                        if (useSocks) socket.connect(java.net.InetSocketAddress(destAddress, destPort), 15000)
                                        else socket.connect(java.net.InetSocketAddress(destAddress, destPort), 15000)
                                        connected = true
                                    } catch (_: Exception) {
                                        connected = false
                                    }

                                    if (!connected) {
                                        try { socket.close() } catch (_: Exception) {}
                                        val rstPacket = buildTcpPacket(
                                            srcIp = dstIpBytes, dstIp = srcIpBytes,
                                            srcPort = destPort, dstPort = srcPort,
                                            seqNum = 0L, ackNum = clientIsn + 1L,
                                            flags = 0x14
                                        )
                                        synchronized(outputStream) {
                                            outputStream.write(rstPacket)
                                            outputStream.flush()
                                        }
                                        return@execute
                                    }

                                    val session = TcpSession(socket, srcIpBytes, dstIpBytes, srcPort, destPort)
                                    session.clientSeq = clientIsn + 1L
                                    session.serverSeq = 1000L
                                    tcpSessions[sessionKey] = session

                                    val synAck = buildTcpPacket(
                                        srcIp = dstIpBytes, dstIp = srcIpBytes,
                                        srcPort = destPort, dstPort = srcPort,
                                        seqNum = session.serverSeq, ackNum = session.clientSeq,
                                        flags = 0x12
                                    )
                                    session.serverSeq += 1L

                                    synchronized(outputStream) {
                                        outputStream.write(synAck)
                                        outputStream.flush()
                                    }

                                    netExecutor?.execute {
                                        try {
                                            val input = socket.getInputStream()
                                            val resBuffer = ByteArray(4096)
                                            val maxChunk = 1360
                                            while (isRunning && !socket.isClosed) {
                                                val r = input.read(resBuffer)
                                                if (r <= 0) break
                                                var offset = 0
                                                while (offset < r) {
                                                    val chunkSize = Math.min(maxChunk, r - offset)
                                                    val chunk = ByteArray(chunkSize)
                                                    System.arraycopy(resBuffer, offset, chunk, 0, chunkSize)
                                                    val dataPacket = buildTcpPacket(
                                                        srcIp = dstIpBytes, dstIp = srcIpBytes,
                                                        srcPort = destPort, dstPort = srcPort,
                                                        seqNum = session.serverSeq, ackNum = session.clientSeq,
                                                        flags = 0x18,
                                                        payload = chunk
                                                    )
                                                    session.serverSeq += chunkSize
                                                    synchronized(outputStream) {
                                                        outputStream.write(dataPacket)
                                                        outputStream.flush()
                                                    }
                                                    offset += chunkSize
                                                }
                                            }

                                            val finPacket = buildTcpPacket(
                                                srcIp = dstIpBytes, dstIp = srcIpBytes,
                                                srcPort = destPort, dstPort = srcPort,
                                                seqNum = session.serverSeq, ackNum = session.clientSeq,
                                                flags = 0x11
                                            )
                                            session.serverSeq += 1L
                                            synchronized(outputStream) {
                                                outputStream.write(finPacket)
                                                outputStream.flush()
                                            }
                                        } catch (_: Exception) {} finally {
                                            tcpSessions.remove(sessionKey)
                                            try { socket.close() } catch (_: Exception) {}
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        } else if (isFin || isRst) {
                            val session = tcpSessions.remove(sessionKey)
                            if (session != null) {
                                if (isFin) {
                                    val finAck = buildTcpPacket(
                                        srcIp = dstIpBytes, dstIp = srcIpBytes,
                                        srcPort = destPort, dstPort = srcPort,
                                        seqNum = session.serverSeq, ackNum = clientIsn + 1L,
                                        flags = 0x11
                                    )
                                    synchronized(outputStream) {
                                        try {
                                            outputStream.write(finAck)
                                            outputStream.flush()
                                        } catch (_: Exception) {}
                                    }
                                }
                                try { session.socket.close() } catch (_: Exception) {}
                            }
                        } else if (payloadLen > 0) {
                            val session = tcpSessions[sessionKey]
                            if (session != null) {
                                val payload = ByteArray(payloadLen)
                                System.arraycopy(buffer, payloadOffset, payload, 0, payloadLen)
                                session.clientSeq = clientIsn + payloadLen
                                netExecutor?.execute {
                                    try {
                                        session.socket.getOutputStream().write(payload)
                                        session.socket.getOutputStream().flush()
                                        val ackPacket = buildTcpPacket(
                                            srcIp = dstIpBytes, dstIp = srcIpBytes,
                                            srcPort = destPort, dstPort = srcPort,
                                            seqNum = session.serverSeq, ackNum = session.clientSeq,
                                            flags = 0x10
                                        )
                                        synchronized(outputStream) {
                                            outputStream.write(ackPacket)
                                            outputStream.flush()
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        } else {
                            // Pure ACK - keepalive, no payload
                            val session = tcpSessions[sessionKey]
                            if (session != null) {
                                session.clientSeq = clientIsn
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Connection stopped or closed
            }
        }
    }

    private fun buildTcpPacket(
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        seqNum: Long, ackNum: Long,
        flags: Int, payload: ByteArray? = null
    ): ByteArray {
        val payloadLen = payload?.size ?: 0
        val ipHeaderLen = 20
        val tcpHeaderLen = 20
        val totalLen = ipHeaderLen + tcpHeaderLen + payloadLen
        val packet = ByteArray(totalLen)

        packet[0] = 0x45.toByte()
        packet[1] = 0.toByte()
        packet[2] = (totalLen ushr 8).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[4] = 0.toByte()
        packet[5] = 0.toByte()
        packet[6] = 0x40.toByte()
        packet[7] = 0.toByte()
        packet[8] = 64.toByte()
        packet[9] = 6.toByte()

        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        val ipChecksum = calcIpChecksum(packet, 0, ipHeaderLen)
        packet[10] = (ipChecksum ushr 8).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        packet[ipHeaderLen] = (srcPort ushr 8).toByte()
        packet[ipHeaderLen + 1] = (srcPort and 0xFF).toByte()
        packet[ipHeaderLen + 2] = (dstPort ushr 8).toByte()
        packet[ipHeaderLen + 3] = (dstPort and 0xFF).toByte()

        packet[ipHeaderLen + 4] = (seqNum ushr 24).toByte()
        packet[ipHeaderLen + 5] = (seqNum ushr 16).toByte()
        packet[ipHeaderLen + 6] = (seqNum ushr 8).toByte()
        packet[ipHeaderLen + 7] = (seqNum and 0xFF).toByte()

        packet[ipHeaderLen + 8] = (ackNum ushr 24).toByte()
        packet[ipHeaderLen + 9] = (ackNum ushr 16).toByte()
        packet[ipHeaderLen + 10] = (ackNum ushr 8).toByte()
        packet[ipHeaderLen + 11] = (ackNum and 0xFF).toByte()

        packet[ipHeaderLen + 12] = 0x50.toByte()
        packet[ipHeaderLen + 13] = flags.toByte()
        packet[ipHeaderLen + 14] = 0x40.toByte()
        packet[ipHeaderLen + 15] = 0x00.toByte()

        if (payload != null && payloadLen > 0) {
            System.arraycopy(payload, 0, packet, ipHeaderLen + tcpHeaderLen, payloadLen)
        }

        val tcpChecksum = calcTcpChecksum(srcIp, dstIp, packet, ipHeaderLen, tcpHeaderLen + payloadLen)
        packet[ipHeaderLen + 16] = (tcpChecksum ushr 8).toByte()
        packet[ipHeaderLen + 17] = (tcpChecksum and 0xFF).toByte()

        return packet
    }

    private fun calcTcpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        tcpPacket: ByteArray,
        tcpOffset: Int,
        tcpLength: Int
    ): Int {
        var sum = 0L

        for (i in 0..3 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
        }
        for (i in 0..3 step 2) {
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += 6
        sum += tcpLength

        var i = 0
        while (i < tcpLength - 1) {
            if (i != 16) {
                val word = ((tcpPacket[tcpOffset + i].toInt() and 0xFF) shl 8) or (tcpPacket[tcpOffset + i + 1].toInt() and 0xFF)
                sum += word
            }
            i += 2
        }
        if (i < tcpLength) {
            sum += (tcpPacket[tcpOffset + i].toInt() and 0xFF) shl 8
        }

        while (sum ushr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun calcIpChecksum(packet: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < offset + length) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }
        while (sum ushr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun stopVpn() {
        isRunning = false
        timerHandler?.removeCallbacksAndMessages(null)
        timerHandler = null
        timerRunnable = null
        for (session in tcpSessions.values) {
            try { session.socket.close() } catch (_: Exception) {}
        }
        tcpSessions.clear()
        try {
            netExecutor?.shutdownNow()
        } catch (_: Exception) {}
        netExecutor = null
        vpnThread?.interrupt()
        vpnThread = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        tunnelEngine.removeListener(engineListener)
        unregisterNetworkMonitor()
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HttpKu",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun formatUptime(elapsedMillis: Long): String {
        val totalSeconds = elapsedMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun createNotification(): android.app.Notification {
        val elapsed = System.currentTimeMillis() - startTimeMillis
        val uptimeStr = formatUptime(elapsed)

        val titleText = "HttpKu: ${tunnelEngine?.config?.remoteAddr?.takeIf { it.isNotBlank() } ?: "default"}"
        val statusText = when (tunnelEngine?.status) {
            com.sslh.sshl.app.model.TunnelStatus.CONNECTED -> "VPN connected • Tap to open"
            com.sslh.sshl.app.model.TunnelStatus.WAITING_FOR_NETWORK -> "Waiting for network..."
            com.sslh.sshl.app.model.TunnelStatus.CONNECTING -> "Connecting..."
            else -> "VPN connected"
        }

        val stopIntent = Intent(this, HttpKuVpnService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val restartIntent = Intent(this, HttpKuVpnService::class.java).apply { action = ACTION_RESTART }
        val restartPendingIntent = PendingIntent.getService(this, 2, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val remoteViews = android.widget.RemoteViews(packageName, com.sslh.sshl.app.R.layout.notification_vpn).apply {
            setTextViewText(com.sslh.sshl.app.R.id.tvNotifHeader, "HttpKu")
            setTextViewText(com.sslh.sshl.app.R.id.tvNotifUptime, uptimeStr)
            setTextViewText(com.sslh.sshl.app.R.id.tvNotifTitle, titleText)
            setTextViewText(com.sslh.sshl.app.R.id.tvNotifStatus, statusText)
            setOnClickPendingIntent(com.sslh.sshl.app.R.id.btnNotifStop, stopPendingIntent)
            setOnClickPendingIntent(com.sslh.sshl.app.R.id.btnNotifReconnect, restartPendingIntent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("HttpKu")
            .setContentText(statusText)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }
}
