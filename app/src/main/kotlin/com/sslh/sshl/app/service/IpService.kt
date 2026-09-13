package com.sslh.sshl.app.service

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object IpService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun getPublicIpV4(): String {
        val providers = listOf(
            "https://api.ipify.org",
            "https://ipv4.icanhazip.com",
            "https://ifconfig.me/ip"
        )

        for (url in providers) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()?.trim()
                        if (!body.isNullOrEmpty() && body.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""))) {
                            return body
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return "10.193.165.137" // Default fallback IP
    }
}
