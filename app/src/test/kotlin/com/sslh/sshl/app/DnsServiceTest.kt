package com.sslh.sshl.app

import com.sslh.sshl.app.service.DnsService
import org.junit.Assert.*
import org.junit.Test

class DnsServiceTest {

    @Test
    fun testBuildDnsQueryPacket() {
        val domain = "example.com"
        val packet = DnsService.buildDnsQueryPacket(domain)

        assertNotNull(packet)
        assertTrue(packet.size > 12)
        // Transaction ID check
        assertEquals(0x12.toByte(), packet[0])
        assertEquals(0x34.toByte(), packet[1])
    }

    @Test
    fun testEncodeDnsTunnelQuery() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val encoded = DnsService.encodeDnsTunnelQuery(payload, "tunnel.com")

        assertEquals("010203.tunnel.com", encoded)
    }

    @Test
    fun testParseDnsResponseIpShortBuffer() {
        val shortBuffer = byteArrayOf(0x01, 0x02)
        val ip = DnsService.parseDnsResponseIp(shortBuffer, shortBuffer.size)
        assertNull(ip)
    }
}
