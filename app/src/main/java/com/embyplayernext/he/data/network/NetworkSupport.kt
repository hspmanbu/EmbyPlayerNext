package com.embyplayernext.he.data.network

import com.embyplayernext.he.data.model.EmbyServerConfig
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 高性能网络支持单例：
 * 1. 挂载内存带 TTL 的 FastDns，优先解析 IPv4，彻底规避国内网络与电视盒子的 IPv6 5~10 秒黑洞超时；
 * 2. 解除 OkHttp 默认 maxRequestsPerHost=5 的严重并发排队瓶颈，提高到 32~48；
 * 3. 区分 API、流媒体与海报图片专属长连接池，极大减少 TCP/TLS 频繁握手；
 * 4. 支持自签名 HTTPS 证书信任。
 */
object NetworkSupport {
    private val clients = ConcurrentHashMap<String, OkHttpClient>()
    private val mediaClients = ConcurrentHashMap<String, OkHttpClient>()
    private val imageClients = ConcurrentHashMap<String, OkHttpClient>()

    private val apiConnectionPool = ConnectionPool(16, 60, TimeUnit.SECONDS)
    private val mediaConnectionPool = ConnectionPool(16, 120, TimeUnit.SECONDS)
    private val imageConnectionPool = ConnectionPool(32, 60, TimeUnit.SECONDS)

    class FastDns(private val preferIpv4: Boolean = true) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val now = System.currentTimeMillis()
            val cached = dnsCache[hostname]
            if (cached != null && now < cached.first && cached.second.isNotEmpty()) {
                return cached.second
            }
            return try {
                val resolved = Dns.SYSTEM.lookup(hostname)
                val sorted = if (preferIpv4) {
                    resolved.sortedBy { if (it is Inet4Address) 0 else 1 }
                } else {
                    resolved
                }
                dnsCache[hostname] = Pair(now + DNS_TTL_MS, sorted)
                sorted
            } catch (e: Exception) {
                val fallback = dnsCache[hostname]?.second
                if (!fallback.isNullOrEmpty()) {
                    fallback
                } else {
                    throw e
                }
            }
        }

        companion object {
            private const val DNS_TTL_MS = 10 * 60 * 1000L // 10 分钟缓存
            private val dnsCache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

            fun clearCache() {
                dnsCache.clear()
            }

            fun cachedCount(): Int = dnsCache.size
        }
    }

    fun client(config: EmbyServerConfig, timeoutSeconds: Int = 20): OkHttpClient {
        return apiClient(config, timeoutSeconds)
    }

    fun apiClient(config: EmbyServerConfig, timeoutSeconds: Int = 20): OkHttpClient {
        val timeout = timeoutSeconds.coerceIn(2, 60)
        val key = "api:$timeout:${config.allowInsecureHttps}:${config.preferIpv4Dns}"
        return clients.getOrPut(key) { buildApiClient(config, timeout) }
    }

    fun mediaClient(config: EmbyServerConfig, connectTimeoutSeconds: Int = 15): OkHttpClient {
        val timeout = connectTimeoutSeconds.coerceIn(2, 60)
        val key = "media:$timeout:${config.allowInsecureHttps}:${config.preferIpv4Dns}"
        return mediaClients.getOrPut(key) { buildMediaClient(config, timeout) }
    }

    fun imageClient(config: EmbyServerConfig): OkHttpClient {
        val key = "image:${config.allowInsecureHttps}:${config.preferIpv4Dns}"
        return imageClients.getOrPut(key) { buildImageClient(config) }
    }

    private fun buildApiClient(config: EmbyServerConfig, connectTimeoutSeconds: Int): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 32
        }
        val b = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .dns(FastDns(preferIpv4 = config.preferIpv4Dns))
            .connectTimeout(connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(apiConnectionPool)
        configureSsl(b, config)
        return b.build()
    }

    private fun buildMediaClient(config: EmbyServerConfig, connectTimeoutSeconds: Int): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        val b = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .dns(FastDns(preferIpv4 = config.preferIpv4Dns))
            .connectTimeout(connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(mediaConnectionPool)
        configureSsl(b, config)
        return b.build()
    }

    private fun buildImageClient(config: EmbyServerConfig): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 32
        }
        val b = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .dns(FastDns(preferIpv4 = config.preferIpv4Dns))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(imageConnectionPool)
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
        imageClients.clear()
        FastDns.clearCache()
    }
}
