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
    private val mediaClients = ConcurrentHashMap<String, OkHttpClient>()

    fun client(config: EmbyServerConfig, timeoutSeconds: Int = 20): OkHttpClient {
        return apiClient(config, timeoutSeconds)
    }

    fun apiClient(config: EmbyServerConfig, timeoutSeconds: Int = 20): OkHttpClient {
        val timeout = timeoutSeconds.coerceIn(2, 120)
        val key = "api:$timeout:${config.allowInsecureHttps}"
        return clients.getOrPut(key) { buildApiClient(config, timeout) }
    }

    fun mediaClient(config: EmbyServerConfig, connectTimeoutSeconds: Int = 15): OkHttpClient {
        val timeout = connectTimeoutSeconds.coerceIn(2, 60)
        val key = "media:$timeout:${config.allowInsecureHttps}"
        return mediaClients.getOrPut(key) { buildMediaClient(config, timeout) }
    }

    private fun buildApiClient(config: EmbyServerConfig, timeoutSeconds: Int): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(true)
            .followSslRedirects(true)
        configureSsl(b, config)
        return b.build()
    }

    private fun buildMediaClient(config: EmbyServerConfig, connectTimeoutSeconds: Int): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        configureSsl(b, config)
        return b.build()
    }

    private fun configureSsl(b: OkHttpClient.Builder, config: EmbyServerConfig) {
        if (config.allowInsecureHttps) {
            val trust = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), SecureRandom()) }
            b.sslSocketFactory(ssl.socketFactory, trust).hostnameVerifier { _, _ -> true }
        }
    }

    fun clearCachedClients() {
        clients.clear()
        mediaClients.clear()
    }
}
