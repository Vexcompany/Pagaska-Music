package com.metrolist.music.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * Temporarily excludes a stream client for one video after its resolved URL fails in playback.
 * Failures are scoped per video and expire so a transient YouTube/CDN change does not blacklist
 * a client for the rest of the app session.
 */
internal class FailedStreamClientRegistry(
    private val ttlMs: Long = DEFAULT_TTL_MS,
) {
    private data class Entry(
        val clientIds: Set<String>,
        val failedAtMs: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun markFailed(
        videoId: String,
        clientId: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (videoId.isBlank() || clientId.isBlank()) return

        entries.compute(videoId) { _, previous ->
            val active = previous?.takeIf { nowMs - it.failedAtMs in 0L until ttlMs }
            Entry(active?.clientIds.orEmpty() + clientId, nowMs)
        }
    }

    fun failedClientIds(
        videoId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Set<String> {
        val entry = entries[videoId] ?: return emptySet()
        if (nowMs - entry.failedAtMs !in 0L until ttlMs) {
            entries.remove(videoId, entry)
            return emptySet()
        }
        return entry.clientIds
    }

    fun clear(videoId: String) {
        entries.remove(videoId)
    }

    fun clear() {
        entries.clear()
    }

    private companion object {
        const val DEFAULT_TTL_MS = 5 * 60 * 1000L
    }
}
