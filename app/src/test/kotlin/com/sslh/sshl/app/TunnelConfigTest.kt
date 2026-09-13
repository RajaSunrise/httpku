package com.sslh.sshl.app

import com.sslh.sshl.app.model.TunnelConfig
import com.sslh.sshl.app.model.TunnelType
import org.junit.Assert.*
import org.junit.Test

class TunnelConfigTest {

    @Test
    fun testDefaultConfig() {
        val config = TunnelConfig()
        assertEquals("default", config.name)
        assertEquals("id1.jagoanip.my.id", config.remoteAddr)
        assertEquals(443, config.remotePort)
        assertEquals(TunnelType.HTTP, config.type)
        assertEquals("bisnis.udemy.com", config.httpAddr)
        assertEquals(8080, config.httpPort)
        assertEquals("1.1.1.1", config.dnsServer)
        assertTrue(config.detectIpv4)
    }

    @Test
    fun testJsonSerialization() {
        val config = TunnelConfig(
            name = "test_config",
            remoteAddr = "1.2.3.4",
            remotePort = 22,
            type = TunnelType.DNS,
            dnsServer = "8.8.8.8"
        )
        val json = config.toJson()
        val deserialized = TunnelConfig.fromJson(json)

        assertEquals("test_config", deserialized.name)
        assertEquals("1.2.3.4", deserialized.remoteAddr)
        assertEquals(22, deserialized.remotePort)
        assertEquals(TunnelType.DNS, deserialized.type)
        assertEquals("8.8.8.8", deserialized.dnsServer)
    }

    @Test
    fun testTunnelTypeFromString() {
        assertEquals(TunnelType.DIRECT, TunnelType.fromString("direct"))
        assertEquals(TunnelType.HTTP, TunnelType.fromString("http"))
        assertEquals(TunnelType.SSL, TunnelType.fromString("ssl"))
        assertEquals(TunnelType.SOCKS, TunnelType.fromString("socks"))
        assertEquals(TunnelType.DNS, TunnelType.fromString("dns"))
        assertEquals(TunnelType.HAPROXY, TunnelType.fromString("haproxy"))
        assertEquals(TunnelType.HTTP, TunnelType.fromString("unknown"))
    }
}
