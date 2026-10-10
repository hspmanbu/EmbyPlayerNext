package com.embyplayernext.he.data.network

import com.embyplayernext.he.data.model.EmbyServerConfig
import com.embyplayernext.he.data.prefs.AppPreferences
import com.embyplayernext.he.util.DiagnosticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class DynamicPortResolver(
    private val prefs: AppPreferences,
    private val logger: DiagnosticsLogger,
) {
    fun matchesDynamicPort(serverUrl: String, config: EmbyServerConfig): Boolean {
        if (config.dynamicPortFetchUrl.isBlank() || config.dynamicPortServiceName.isBlank()) return false
        val domain = config.dynamicPortTargetDomain.trim()
        if (domain.isNotBlank()) {
            return serverUrl.contains(domain, ignoreCase = true)
        }
        return config.dynamicPortEnabled || config.dynamicPortFetchUrl.isNotBlank()
    }

    suspend fun fetchPort(
        fetchUrl: String,
        serviceName: String,
        timeoutSeconds: Int = 5,
        allowInsecureHttps: Boolean = false,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            require(fetchUrl.isNotBlank()) { "请填写端口抓取页面地址" }
            require(serviceName.isNotBlank()) { "请填写服务名称" }
            logger.log("DynamicPort", "fetch=$fetchUrl, service=$serviceName")
            val dummyConfig = EmbyServerConfig(
                dynamicPortTimeoutSeconds = timeoutSeconds,
                allowInsecureHttps = allowInsecureHttps,
            )
            val req = Request.Builder().url(fetchUrl).header("User-Agent", "Mozilla/5.0 Android EmbyPlayer/2.0").build()
            val client = NetworkSupport.apiClient(dummyConfig, timeoutSeconds.coerceAtLeast(2))
            val text = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("端口页面 HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
            val port = parsePortFromContent(text, serviceName)
                ?: error("未找到服务 $serviceName 对应端口")
            logger.log("DynamicPort", "parsed port: $port")
            port
        }
    }

    suspend fun resolveAndSwitch(force: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        val c = prefs.config.value
        if (!force && !c.dynamicPortEnabled) return@withContext Result.failure(IllegalStateException("动态端口未启用"))
        if (!force && c.dynamicPortTargetDomain.isNotBlank() && c.serverUrl.isNotBlank() && !c.serverUrl.contains(c.dynamicPortTargetDomain, true)) {
            return@withContext Result.failure(IllegalStateException("当前服务器地址不匹配动态端口目标域名"))
        }
        val portResult = fetchPort(
            fetchUrl = c.dynamicPortFetchUrl,
            serviceName = c.dynamicPortServiceName,
            timeoutSeconds = c.dynamicPortTimeoutSeconds,
            allowInsecureHttps = c.allowInsecureHttps,
        )
        portResult.onSuccess { port ->
            if (c.serverUrl.isNotBlank()) {
                val switched = replacePort(c.serverUrl, port)
                prefs.updateServerPort(switched)
                logger.log("DynamicPort", "resolved=$port, server=$switched")
            } else {
                logger.log("DynamicPort", "resolved=$port (服务器地址未配置，仅保存端口)")
            }
        }
    }

    fun parsePortFromContent(content: String, serviceName: String): Int? {
        fun valid(s: String): Int? = s.toIntOrNull()?.takeIf { it in 1..65535 }
        val rows = Regex("(?is)<tr[^>]*>(.*?)</tr>").findAll(content).map { it.groupValues[1] }
        for (row in rows) {
            val plain = row.replace(Regex("(?is)<[^>]+>"), " ").replace("&nbsp;", " ")
            if (serviceName.isBlank() || plain.contains(serviceName, true)) {
                Regex("(?<!\\d)(\\d{2,5})(?!\\d)").findAll(plain).mapNotNull { valid(it.groupValues[1]) }.lastOrNull()?.let { return it }
            }
        }
        if (serviceName.isNotBlank()) {
            val idx = content.indexOf(serviceName, ignoreCase = true)
            if (idx >= 0) {
                val window = content.substring(idx, (idx + 600).coerceAtMost(content.length))
                Regex("(?<!\\d)(\\d{2,5})(?!\\d)").findAll(window).mapNotNull { valid(it.groupValues[1]) }.firstOrNull()?.let { return it }
            }
        }
        val trimmed = content.trim()
        valid(trimmed)?.let { return it }
        return Regex("(?i)(?:port|端口)\\D{0,20}(\\d{2,5})").find(content)?.groupValues?.getOrNull(1)?.let(::valid)
    }

    fun replacePort(serverUrl: String, port: Int): String {
        val normalized = if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) serverUrl else "http://$serverUrl"
        val http = normalized.toHttpUrlOrNull() ?: error("无效服务器地址: $serverUrl")
        return http.newBuilder().port(port).build().toString().trimEnd('/')
    }
}
