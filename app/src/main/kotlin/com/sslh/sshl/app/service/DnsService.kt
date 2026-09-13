package com.sslh.sshl.app.service

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.io.ByteArrayOutputStream

object DnsService {

    fun queryDns(hostname: String, dnsServer: String = "1.1.1.1", port: Int = 53, timeoutMs: Int = 3000): String? {
        return try {
            val socket = DatagramSocket()
            socket.soTimeout = timeoutMs

            val queryPacket = buildDnsQueryPacket(hostname)
            val serverAddr = InetAddress.getByName(dnsServer)

            val packet = DatagramPacket(queryPacket, queryPacket.size, serverAddr, port)
            socket.send(packet)

            val buf = ByteArray(512)
            val recvPacket = DatagramPacket(buf, buf.size)
            socket.receive(recvPacket)
            socket.close()

            parseDnsResponseIp(buf, recvPacket.length)
        } catch (e: Exception) {
            null
        }
    }

    fun buildDnsQueryPacket(domain: String): ByteArray {
        val baos = ByteArrayOutputStream()
        // Transaction ID (2 bytes)
        baos.write(0x12)
        baos.write(0x34)
        // Flags: Standard query (2 bytes)
        baos.write(0x01)
        baos.write(0x00)
        // Questions count: 1 (2 bytes)
        baos.write(0x00)
        baos.write(0x01)
        // Answer RRs: 0, Authority RRs: 0, Additional RRs: 0
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)

        // Query Name
        for (part in domain.split(".")) {
            baos.write(part.length)
            baos.write(part.toByteArray(Charsets.US_ASCII))
        }
        baos.write(0x00) // End of domain name

        // Type A (1) (2 bytes)
        baos.write(0x00)
        baos.write(0x01)
        // Class IN (1) (2 bytes)
        baos.write(0x00)
        baos.write(0x01)

        return baos.toByteArray()
    }

    fun parseDnsResponseIp(data: ByteArray, length: Int): String? {
        if (length < 12) return null
        val anCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (anCount == 0) return null

        // Skip header and question section
        var pos = 12
        while (pos < length && data[pos] != 0.toByte()) {
            pos += (data[pos].toInt() and 0xFF) + 1
        }
        pos += 5 // Skip 0x00 + Type (2) + Class (2)

        // Check Answer section
        if (pos + 12 <= length) {
            // Compression pointer or name
            if ((data[pos].toInt() and 0xC0) == 0xC0) {
                pos += 2
            } else {
                while (pos < length && data[pos] != 0.toByte()) {
                    pos += (data[pos].toInt() and 0xFF) + 1
                }
                pos++
            }
            pos += 8 // Type(2), Class(2), TTL(4)
            val dataLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2
            if (dataLen == 4 && pos + 4 <= length) {
                val ip1 = data[pos].toInt() and 0xFF
                val ip2 = data[pos + 1].toInt() and 0xFF
                val ip3 = data[pos + 2].toInt() and 0xFF
                val ip4 = data[pos + 3].toInt() and 0xFF
                return "$ip1.$ip2.$ip3.$ip4"
            }
        }
        return null
    }

    fun encodeDnsTunnelQuery(payload: ByteArray, domain: String): String {
        val base32 = payload.joinToString("") { "%02x".format(it) }
        return "$base32.$domain"
    }
}
