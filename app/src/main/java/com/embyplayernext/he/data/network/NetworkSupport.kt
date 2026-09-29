package com.embyplayernext.he.data.network

import com.embyplayernext.he.data.model.EmbyServerConfig
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetworkSupport {
    private val clients = ConcurrentHashMap<String, OkHttpClient>()

    fun client(config: EmbyServerConfig, timeoutSeconds: Int = 20): OkHttpClient {
        val timeout = timeoutSeconds.coerceIn(2, 120)
        val key = "$timeout:${config.allowInsecureHttps}"
        return clients.getOrPut(key) { buildClient(config, timeout) }
    }

    private fun buildClient(config: EmbyServerConfig, timeoutSeconds: Int): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        if (config.allowInsecureHttps) {
            val trust = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), SecureRandom()) }
            b.sslSocketFactory(ssl.socketFactory, trust).hostnameVerifier { _, _ -> true }
        }
        return b.build()
    }

    fun clearCachedClients() { clients.clear() }
}
