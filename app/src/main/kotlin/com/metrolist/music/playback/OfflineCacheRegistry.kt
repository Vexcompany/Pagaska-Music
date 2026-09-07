/**
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.playback

import androidx.media3.datasource.cache.CacheSpan
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local index of media ids whose player-cache bytes are protected as Offline Cache.
 *
 * This deliberately does not own any bytes or database state. [OfflineCacheManager] is the
 * authority for promotion/removal; this registry only gives the CacheEvictor a non-blocking,
 * allocation-light answer while SimpleCache's internal lock is held.
 */
object OfflineCacheRegistry {
    private val protectedMediaIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var cacheActivityListener: ((CacheSpan) -> Unit)? = null

    fun protect(mediaId: String) {
        protectedMediaIds.add(mediaId)
    }

    fun unprotect(mediaId: String) {
        protectedMediaIds.remove(mediaId)
    }

    fun isProtected(mediaId: String): Boolean = protectedMediaIds.contains(mediaId)

    fun snapshot(): Set<String> = protectedMediaIds.toSet()

    fun replaceAll(mediaIds: Collection<String>) {
        protectedMediaIds.clear()
        protectedMediaIds.addAll(mediaIds)
    }

    /**
     * Registers a lightweight callback used by the cache evictor to report cache activity.
     * The callback must not perform blocking work because it may run while SimpleCache holds its
     * internal lock. OfflineCacheManager dispatches the actual reconciliation to its application
     * scope.
     */
    fun setCacheActivityListener(listener: ((CacheSpan) -> Unit)?) {
        cacheActivityListener = listener
    }

    fun notifyCacheActivity(span: CacheSpan) {
        cacheActivityListener?.invoke(span)
    }

    fun clear() {
        protectedMediaIds.clear()
        cacheActivityListener = null
    }
}
