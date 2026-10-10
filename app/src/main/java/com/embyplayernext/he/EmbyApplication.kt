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

class EmbyApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val prefs = AppPreferences(this)
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice == true

        return ImageLoader.Builder(this)
            .okHttpClient {
                NetworkSupport.imageClient(prefs.config.value)
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRam) 0.15 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024L * 1024L)
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
            .build()
    }
}
