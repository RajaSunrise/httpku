package com.sslh.sshl.app

import com.sslh.sshl.app.model.TunnelConfig
import com.sslh.sshl.app.model.TunnelStatus
import com.sslh.sshl.app.model.TunnelType
import com.sslh.sshl.app.service.TunnelEngine
import org.junit.Assert.*
import org.junit.Test

class TunnelEngineTest {

    @Test
    fun testInitialEngineState() {
        val engine = TunnelEngine()
        assertEquals(TunnelStatus.DISCONNECTED, engine.status)
        assertFalse(engine.isConnected)
        assertFalse(engine.isConnecting)
        assertTrue(engine.logs.isEmpty())
    }

    @Test
    fun testUpdateConfig() {
        val engine = TunnelEngine()
        val newConfig = TunnelConfig(remoteAddr = "10.0.0.1", type = TunnelType.DNS)
        engine.updateConfig(newConfig)

        assertEquals("10.0.0.1", engine.config.remoteAddr)
        assertEquals(TunnelType.DNS, engine.config.type)
    }

    @Test
    fun testAddAndClearLogs() {
        val engine = TunnelEngine()
        engine.addLog("Test log message 1")
        engine.addLog("Test log message 2", isError = true)

        assertEquals(2, engine.logs.size)
        assertEquals("Test log message 1", engine.logs[0].message)
        assertTrue(engine.logs[1].isError)

        engine.clearLogs()
        assertTrue(engine.logs.isEmpty())
    }

    @Test
    fun testStartAndStopTunnel() {
        val engine = TunnelEngine()
        engine.startTunnel()

        // Wait brief moment for thread execution
        Thread.sleep(300)

        assertTrue(engine.logs.size > 0)

        engine.stopTunnel()
        assertEquals(TunnelStatus.DISCONNECTED, engine.status)
    }
}
