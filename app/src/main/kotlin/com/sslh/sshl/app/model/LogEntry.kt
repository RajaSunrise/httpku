package com.sslh.sshl.app.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isSuccess: Boolean = false,
    val isHighlight: Boolean = false
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("h:mm:ss a", Locale.US)
            return sdf.format(Date(timestamp))
        }
}

enum class TunnelStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    WAITING_FOR_NETWORK
}
