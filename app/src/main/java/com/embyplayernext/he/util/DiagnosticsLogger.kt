package com.embyplayernext.he.util

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsLogger(private val context: Context) {
    private val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
    private val file = File(dir, "emby_diag.txt")
    private val jsonSecret = Regex("""(?i)("(?:AccessToken|Token|Password|Pw)"\s*:\s*")[^"]*(")""")
    private val querySecret = Regex("""(?i)([?&](?:api_key|token|access_token|x-emby-token)=)[^&\s]+""")
    private val headerSecret = Regex("""(?i)(X-Emby-Token\s*[:=]\s*)[^,\s]+""")

    private fun redact(message: String): String = message
        .replace(jsonSecret, "$1<redacted>$2")
        .replace(querySecret, "$1<redacted>")
        .replace(headerSecret, "$1<redacted>")

    @Synchronized fun log(tag: String, message: String) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        file.appendText("$time [$tag] ${redact(message)}\n")
        if (file.length() > 2_000_000) {
            val tail = file.readText().takeLast(1_000_000)
            file.writeText(tail)
        }
    }
    fun clear() = runCatching { file.writeText("") }
    @Synchronized fun exportTextFile(): Result<String> = runCatching {
        if (!file.exists()) file.writeText("No diagnostics yet.\n")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "EmbyPlayerNext_diag_$stamp.txt"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EmbyPlayerNext")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "无法创建下载文件" }
            try {
                resolver.openOutputStream(uri, "w").use { out ->
                    requireNotNull(out) { "无法打开下载文件" }
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
            "下载/EmbyPlayerNext/$displayName"
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "diagnostics").apply { mkdirs() }
            val target = File(dir, displayName)
            file.copyTo(target, overwrite = true)
            target.absolutePath
        }
    }
}
