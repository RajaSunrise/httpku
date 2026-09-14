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
        var tunnelEngine: TunnelEngine? = null
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        registerNetworkMonitor()
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
    private var dnsExecutor: ExecutorService? = null

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
        dnsExecutor = Executors.newFixedThreadPool(4)
        vpnThread = kotlin.concurrent.thread(start = true, name = "HttpKuVpnPacketThread") {
            val pfd = vpnInterface ?: return@thread
            val inputStream = java.io.FileInputStream(pfd.fileDescriptor)
            val outputStream = java.io.FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteArray(32767)

            try {
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    val read = inputStream.read(buffer)
                    if (read <= 0) {
                        Thread.sleep(10)
                        continue
                    }

                    // Process IP packet: intercept DNS UDP (port 53) & relay TCP/IP traffic
                    if (read >= 28 && buffer[9].toInt() == 17) { // UDP packet
                        val destPort = ((buffer[22].toInt() and 0xFF) shl 8) or (buffer[23].toInt() and 0xFF)
                        if (destPort == 53) {
                            val udpHeaderLen = 8
                            val ipHeaderLen = (buffer[0].toInt() and 0x0F) * 4
                            val dnsDataOffset = ipHeaderLen + udpHeaderLen
                            val dnsDataLen = read - dnsDataOffset

                            if (dnsDataLen > 0) {
                                val packetCopy = ByteArray(read)
                                System.arraycopy(buffer, 0, packetCopy, 0, read)
                                dnsExecutor?.execute {
                                    try {
                                        val dnsQuery = ByteArray(dnsDataLen)
                                        System.arraycopy(packetCopy, dnsDataOffset, dnsQuery, 0, dnsDataLen)

                                        val datagramSocket = java.net.DatagramSocket()
                                        protect(datagramSocket)
                                        datagramSocket.soTimeout = 3000

                                        val customDns = tunnelEngine?.config?.dnsServer?.takeIf { it.isNotBlank() } ?: "1.1.1.1"
                                        val dnsServerAddr = java.net.InetAddress.getByName(customDns)
                                        val packet = java.net.DatagramPacket(dnsQuery, dnsQuery.size, dnsServerAddr, 53)
                                        datagramSocket.send(packet)

                                        val recvBuf = ByteArray(4096)
                                        val recvPacket = java.net.DatagramPacket(recvBuf, recvBuf.size)
                                        datagramSocket.receive(recvPacket)
                                        datagramSocket.close()

                                        // Construct response IP/UDP packet back to TUN
                                        val respDnsLen = recvPacket.length
                                        val respPacket = ByteArray(ipHeaderLen + udpHeaderLen + respDnsLen)

                                        // Swap IP src and dst
                                        System.arraycopy(packetCopy, 0, respPacket, 0, ipHeaderLen)
                                        System.arraycopy(packetCopy, 12, respPacket, 16, 4) // dst -> src
                                        System.arraycopy(packetCopy, 16, respPacket, 12, 4) // src -> dst

                                        // UDP ports swap
                                        respPacket[ipHeaderLen] = packetCopy[22]
                                        respPacket[ipHeaderLen + 1] = packetCopy[23]
                                        respPacket[ipHeaderLen + 2] = packetCopy[20]
                                        respPacket[ipHeaderLen + 3] = packetCopy[21]

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
                        }
                    }
                }
            } catch (_: Exception) {
                // Connection stopped or closed
            }
        }
    }

    private fun stopVpn() {
        isRunning = false
        timerHandler?.removeCallbacksAndMessages(null)
        timerHandler = null
        timerRunnable = null
        try {
            dnsExecutor?.shutdownNow()
        } catch (_: Exception) {}
        dnsExecutor = null
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
        unregisterNetworkMonitor()
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HttpKu VPN Service",
                NotificationManager.IMPORTANCE_LOW
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

        val serverAddr = tunnelEngine?.config?.remoteAddr.takeIf { !it.isNullOrBlank() }
            ?: tunnelEngine?.config?.httpAddr.takeIf { !it.isNullOrBlank() }
            ?: "Server running"

        val statusText = when (tunnelEngine?.status) {
            com.sslh.sshl.app.model.TunnelStatus.CONNECTED -> "Server: $serverAddr | Time: $uptimeStr"
            com.sslh.sshl.app.model.TunnelStatus.WAITING_FOR_NETWORK -> "Waiting for network connection..."
            com.sslh.sshl.app.model.TunnelStatus.CONNECTING -> "Connecting to server..."
            else -> "HttpKu Tunnel Active | Time: $uptimeStr"
        }

        val stopIntent = Intent(this, HttpKuVpnService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val restartIntent = Intent(this, HttpKuVpnService::class.java).apply { action = ACTION_RESTART }
        val restartPendingIntent = PendingIntent.getService(this, 2, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HttpKu Tunnel")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_rotate, "RESTART", restartPendingIntent)
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
