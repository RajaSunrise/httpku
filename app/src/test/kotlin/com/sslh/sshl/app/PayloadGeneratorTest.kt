package com.sslh.sshl.app

import com.sslh.sshl.app.service.PayloadGenerator
import com.sslh.sshl.app.service.PayloadGeneratorOptions
import org.junit.Assert.*
import org.junit.Test

class PayloadGeneratorTest {

    @Test
    fun testParsePayloadTags() {
        val rawPayload = "GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf][crlf]"
        val parsed = PayloadGenerator.parsePayload(rawPayload, host = "example.com", port = 443)

        assertTrue(parsed.contains("\r\n"))
        assertTrue(parsed.contains("Host: example.com"))
        assertFalse(parsed.contains("[crlf]"))
        assertFalse(parsed.contains("[host]"))
    }

    @Test
    fun testGeneratePayloadNormalMode() {
        val options = PayloadGeneratorOptions(
            url = "example.com",
            method = "GET",
            injectionMethod = "Normal",
            upgradeWebsocket = true,
            keepAlive = true,
            userAgent = true
        )
        val generated = PayloadGenerator.generatePayload(options)

        assertTrue(generated.contains("GET / HTTP/1.1[crlf]"))
        assertTrue(generated.contains("Host: example.com[crlf]"))
        assertTrue(generated.contains("Upgrade: websocket[crlf]"))
        assertTrue(generated.contains("Connection: Keep-Alive[crlf]"))
        assertTrue(generated.contains("User-Agent: [ua][crlf]"))
    }

    @Test
    fun testGeneratePayloadFrontInjectMode() {
        val options = PayloadGeneratorOptions(
            url = "example.com",
            method = "POST",
            injectionMethod = "Front Inject"
        )
        val generated = PayloadGenerator.generatePayload(options)

        assertTrue(generated.contains("POST http://example.com/ HTTP/1.1[crlf]"))
    }

    @Test
    fun testGeneratePayloadBackInjectMode() {
        val options = PayloadGeneratorOptions(
            url = "example.com",
            method = "GET",
            injectionMethod = "Back Inject"
        )
        val generated = PayloadGenerator.generatePayload(options)

        assertTrue(generated.contains("CONNECT [host_port] HTTP/1.1[crlf]"))
        assertTrue(generated.contains("GET http://example.com/ HTTP/1.1[crlf]"))
    }
}
