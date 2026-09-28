@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.embyplayernext.he.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache

object PlayerCache {
    @Volatile private var cache: SimpleCache? = null
    fun get(context: Context, mb: Int): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            context.getDir("media_cache", Context.MODE_PRIVATE),
            LeastRecentlyUsedCacheEvictor(mb.coerceAtLeast(0).toLong() * 1024L * 1024L),
            StandaloneDatabaseProvider(context)
        ).also { cache = it }
    }

    @Synchronized fun release() {
        runCatching { cache?.release() }
        cache = null
    }
}
