package com.embyplayernext.he

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.embyplayernext.he.data.network.NetworkSupport
import com.embyplayernext.he.data.prefs.AppPreferences
import com.embyplayernext.he.playback.PlayerCache

class EmbyApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val prefs = AppPreferences(this)
        val config = prefs.config.value
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice == true

        val builder = ImageLoader.Builder(this)
            .okHttpClient {
                NetworkSupport.imageClient(prefs.config.value)
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRam) 0.15 else 0.25)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .apply {
                if (isLowRam) {
                    bitmapConfig(Bitmap.Config.RGB_565)
                    allowRgb565(true)
                }
            }

        if (config.imageCacheMb > 0) {
            builder.diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(config.imageCacheMb.toLong() * 1024L * 1024L)
                    .build()
            }
        }

        return builder.build()
    }

    companion object {
        fun getImageCacheSize(context: Context): Long {
            val dir = context.cacheDir.resolve("image_cache")
            return PlayerCache.calculateFolderSize(dir)
        }

        @OptIn(coil.annotation.ExperimentalCoilApi::class)
        fun clearImageCache(context: Context) {
            val dir = context.cacheDir.resolve("image_cache")
            runCatching {
                coil.Coil.imageLoader(context).diskCache?.clear()
                coil.Coil.imageLoader(context).memoryCache?.clear()
                dir.deleteRecursively()
                dir.mkdirs()
            }
        }
    }
}
