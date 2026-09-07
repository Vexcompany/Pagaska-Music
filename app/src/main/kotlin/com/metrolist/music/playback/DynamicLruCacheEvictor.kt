/**
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.playback

import androidx.media3.common.C
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import com.metrolist.music.utils.SongCacheConfig
import java.util.TreeSet

/**
 * Dynamic LRU evictor for the streaming/player cache.
 *
 * Offline Cache entries are protected by [OfflineCacheRegistry] and are never selected as LRU
 * eviction candidates. Temporary streaming spans remain normal LRU entries and are evicted first.
 */
class DynamicLruCacheEvictor : CacheEvictor {
    private val leastRecentlyUsed =
        TreeSet<CacheSpan> { lhs, rhs ->
            val delta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
            when {
                delta != 0L -> if (delta < 0) -1 else 1
                else -> lhs.compareTo(rhs)
            }
        }

    private var currentSize: Long = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized(cache: Cache) {
        // SimpleCache calls this after rebuilding its index from disk. The evictor must reconstruct
        // its in-memory accounting here; otherwise a process restart resets currentSize to zero and
        // the configured capacity can be exceeded by already-existing spans.
        leastRecentlyUsed.clear()
        currentSize = 0L
        cache.keys.forEach { key ->
            cache.getCachedSpans(key).forEach { span ->
                leastRecentlyUsed.add(span)
                currentSize += span.length
            }
        }

        // If the persisted cache is already over the configured budget, reclaim temporary spans
        // immediately. Offline protection is populated by OfflineCacheManager during startup and
        // any protected spans are therefore skipped here as well when the registry is already warm.
        evictCache(cache, 0L)
    }

    override fun onStartFile(
        cache: Cache,
        key: String,
        position: Long,
        length: Long,
    ) {
        if (length != C.LENGTH_UNSET.toLong()) {
            evictCache(cache, length)
        }
    }

    override fun onSpanAdded(
        cache: Cache,
        span: CacheSpan,
    ) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evictCache(cache, 0L)
    }

    override fun onSpanRemoved(
        cache: Cache,
        span: CacheSpan,
    ) {
        if (leastRecentlyUsed.remove(span)) {
            currentSize = (currentSize - span.length).coerceAtLeast(0L)
        }
    }

    override fun onSpanTouched(
        cache: Cache,
        oldSpan: CacheSpan,
        newSpan: CacheSpan,
    ) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    private fun evictCache(
        cache: Cache,
        requiredSpace: Long,
    ) {
        val maxBytes = SongCacheConfig.maxBytes
        if (maxBytes == Long.MAX_VALUE) return

        // Never loop more times than there are candidate spans to inspect.
        var budget = leastRecentlyUsed.size + 1
        while (currentSize + requiredSpace > maxBytes &&
            leastRecentlyUsed.isNotEmpty() &&
            budget-- > 0
        ) {
            val span = leastRecentlyUsed.firstOrNull { !OfflineCacheRegistry.isProtected(it.key) }
                ?: return

            val sizeBefore = currentSize
            cache.removeSpan(span)
            if (currentSize == sizeBefore) {
                // removeSpan() did not call back into onSpanRemoved (span was already gone or got
                // replaced under us). Drop it locally so the loop makes progress.
                leastRecentlyUsed.remove(span)
                currentSize = (currentSize - span.length).coerceAtLeast(0L)
            }
        }
    }
}
