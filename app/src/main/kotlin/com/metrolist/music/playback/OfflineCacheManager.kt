/**
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.playback

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.SongCacheConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Coordinates the database flag and the physical player-cache resource used for automatic Offline
 * Cache. Explicit downloads remain in the separate download cache and are intentionally outside
 * this manager's quota.
 */
class OfflineCacheManager(
    private val database: MusicDatabase,
    private val playerCache: Cache,
    private val applicationScope: CoroutineScope,
) {
    init {
        OfflineCacheRegistry.setCacheActivityListener { span ->
            applicationScope.launch(Dispatchers.IO) {
                promoteFromCacheActivity(span)
            }
        }

        // Rebuild the protected registry as soon as the singleton is created. This repairs stale
        // isCached flags left behind by crashes, manual cache wipes, or older builds before any
        // playback attempts to use the protected-cache path.
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                reconcile { mediaId ->
                    database.song(mediaId).firstOrNull()?.format?.contentLength
                }
            }.onFailure { error ->
                Timber.tag(TAG).w(error, "Offline Cache startup reconciliation failed")
            }
        }
    }

    private suspend fun promoteFromCacheActivity(span: CacheSpan) {
        val mediaId = span.key
        val contentLength = database.song(mediaId).firstOrNull()?.format?.contentLength ?: return
        if (contentLength <= 0L || span.position + span.length < contentLength) return
        promoteIfEligible(mediaId, contentLength)
    }

    suspend fun reconcile(contentLengthProvider: suspend (String) -> Long?) = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        val cursor = db.query("SELECT id FROM song WHERE isCached = 1")
        val protectedIds = mutableListOf<String>()
        try {
            val idIndex = cursor.getColumnIndexOrThrow("id")
            while (cursor.moveToNext()) {
                val mediaId = cursor.getString(idIndex)
                val length = contentLengthProvider(mediaId)
                if (length != null && length > 0L && playerCache.isCached(mediaId, 0L, length)) {
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

        // Register protection before the final physical-cache check and DB write. The evictor can
        // run concurrently with this coroutine, so protection must become visible first.
        OfflineCacheRegistry.protect(mediaId)
        try {
            if (!playerCache.isCached(mediaId, 0L, contentLength)) {
                OfflineCacheRegistry.unprotect(mediaId)
                return@withContext false
            }

            database.openHelper.writableDatabase.execSQL(
                "UPDATE song SET isCached = 1 WHERE id = ? AND isDownloaded = 0",
                arrayOf(mediaId),
            )
            true
        } catch (t: Throwable) {
            OfflineCacheRegistry.unprotect(mediaId)
            throw t
        }
    }

    /**
     * Detaches a song from automatic Offline Cache when an explicit Download becomes authoritative.
     * This prevents two physical copies of the same song from surviving in playerCache and
     * downloadCache. The download itself remains untouched.
     */
    suspend fun detachForExplicitDownload(mediaId: String) = withContext(Dispatchers.IO) {
        val shouldRemove = OfflineCacheRegistry.isProtected(mediaId) ||
            database.songEntity(mediaId)?.isCached == true
        if (!shouldRemove) return@withContext

        OfflineCacheRegistry.unprotect(mediaId)
        playerCache.removeResource(mediaId)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE song SET isCached = 0 WHERE id = ?",
            arrayOf(mediaId),
        )
    }

    suspend fun remove(mediaId: String) = withContext(Dispatchers.IO) {
        OfflineCacheRegistry.unprotect(mediaId)
        playerCache.removeResource(mediaId)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE song SET isCached = 0 WHERE id = ? AND isDownloaded = 0",
            arrayOf(mediaId),
        )
    }

    fun protectedCacheBytes(): Long =
        OfflineCacheRegistry.snapshot().sumOf { mediaId ->
            playerCache.getCachedSpans(mediaId).sumOf { it.length }
        }

    private companion object {
        const val TAG = "OfflineCacheManager"
    }
}
