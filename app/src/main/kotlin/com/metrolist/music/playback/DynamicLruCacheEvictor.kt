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
 * Least-recently-used [CacheEvictor] whose byte budget is read from [SongCacheConfig] on demand.
 *
 * Media3's own `LeastRecentlyUsedCacheEvictor` is `final` and takes an immutable `maxBytes`, which
 * is why the "max song cache size" slider used to have no effect until the process died: the
 * singleton `SimpleCache` kept the evictor that was built with whatever value happened to be stored
 * when the cache was first touched. This implementation keeps the exact same LRU ordering but asks
 * for the current limit on every eviction pass, so the slider applies immediately.
 *
 * Two extra safety properties compared to the stock evictor:
 *  - `maxBytes == Long.MAX_VALUE` (unlimited) short-circuits before any arithmetic, so
 *    `currentSize + requiredSpace` can never overflow into a bogus eviction;
 *  - the eviction loop is bounded and self-heals if `Cache.removeSpan` does not notify us back
 *    (stale/replaced span), instead of spinning forever on the same span.
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

    override fun onCacheInitialized() = Unit

    override fun onStartFile(
        cache: Cache,
        key: String,
        position: Long,
        length: Long,
    ) {
        if (length != C.LENGTH_UNSET) {
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

        // Never loop more times than there are candidate spans to drop.
        var budget = leastRecentlyUsed.size + 1
        while (currentSize + requiredSpace > maxBytes &&
            leastRecentlyUsed.isNotEmpty() &&
            budget-- > 0
        ) {
            val span = leastRecentlyUsed.first()
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
