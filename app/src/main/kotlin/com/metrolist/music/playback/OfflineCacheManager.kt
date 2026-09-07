/**
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.playback

import androidx.media3.datasource.cache.Cache
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.SongCacheConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Coordinates the database flag and the physical player-cache resource used for automatic Offline
 * Cache. Explicit downloads remain in [downloadCache] and are intentionally outside this manager.
 */
class OfflineCacheManager(
    private val database: MusicDatabase,
    private val playerCache: Cache,
) {
    /**
     * Restores the in-memory protection set from the database and clears stale flags whose complete
     * physical resource is no longer present. This is intentionally conservative: bytes that are
     * merely present in playerCache are not promoted unless the DB says they are Offline Cache.
     */
    suspend fun reconcile(contentLengthProvider: suspend (String) -> Long?) = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        val cursor = db.query("SELECT id, isCached FROM song WHERE isCached = 1")
        val protectedIds = mutableListOf<String>()
        try {
            val idIndex = cursor.getColumnIndexOrThrow("id")
            while (cursor.moveToNext()) {
                val mediaId = cursor.getString(idIndex)
                val length = contentLengthProvider(mediaId)
                if (length != null && playerCache.isCached(mediaId, 0L, length)) {
                    protectedIds += mediaId
                } else {
                    db.execSQL("UPDATE song SET isCached = 0 WHERE id = ?", arrayOf(mediaId))
                }
            }
        } finally {
            cursor.close()
        }
        OfflineCacheRegistry.replaceAll(protectedIds)
    }

    /**
     * Promotes a completely buffered song to persistent Offline Cache.
     *
     * The registry is updated before the DB flag so the evictor cannot remove the resource in the
     * small window between the physical-cache check and the database transaction.
     */
    suspend fun promoteIfEligible(
        mediaId: String,
        contentLength: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        if (contentLength <= 0L) return@withContext false
        if (database.songEntity(mediaId)?.isDownloaded == true) return@withContext false
        if (!playerCache.isCached(mediaId, 0L, contentLength)) return@withContext false
        if (OfflineCacheRegistry.isProtected(mediaId)) return@withContext true

        val maxBytes = SongCacheConfig.maxBytes
        if (maxBytes != Long.MAX_VALUE && protectedCacheBytes() + contentLength > maxBytes) {
            Timber.tag(TAG).i("Offline Cache capacity full; keeping %s as temporary cache", mediaId)
            return@withContext false
        }

        OfflineCacheRegistry.protect(mediaId)
        try {
            // Re-check after registering protection. The cache may have changed while the span
            // ownership was being established.
            if (!playerCache.isCached(mediaId, 0L, contentLength)) {
                OfflineCacheRegistry.unprotect(mediaId)
                return@withContext false
            }

            database.openHelper.writableDatabase.execSQL(
                "UPDATE song SET isCached = 1, dateDownload = ? WHERE id = ? AND isDownloaded = 0",
                arrayOf(LocalDateTime.now().toString(), mediaId),
            )
            true
        } catch (t: Throwable) {
            OfflineCacheRegistry.unprotect(mediaId)
            throw t
        }
    }

    suspend fun remove(mediaId: String) = withContext(Dispatchers.IO) {
        OfflineCacheRegistry.unprotect(mediaId)
        playerCache.removeResource(mediaId)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE song SET isCached = 0, dateDownload = NULL WHERE id = ? AND isDownloaded = 0",
            arrayOf(mediaId),
        )
    }

    fun protectedCacheBytes(): Long =
        OfflineCacheRegistrySnapshot.ids().sumOf { mediaId ->
            playerCache.getCachedSpans(mediaId).sumOf { it.length }
        }

    private companion object {
        const val TAG = "OfflineCacheManager"
    }
}

private object OfflineCacheRegistrySnapshot {
    fun ids(): Set<String> =
        // Registry currently exposes only membership operations to keep eviction reads cheap.
        // The manager's SQL reconciliation is the source of truth for a future management screen.
        emptySet()
}
