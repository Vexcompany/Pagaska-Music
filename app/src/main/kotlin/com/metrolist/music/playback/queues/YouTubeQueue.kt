/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private var retryCount = 0
    private val maxRetries = 3
    private val isRadioRequest =
        endpoint.playlistId?.startsWith("RDAMVM") == true ||
            (endpoint.videoId != null && endpoint.playlistId == null)
    private val loadedRadioIds = mutableSetOf<String>()

    private class EmptyRadioQueueException : IllegalStateException()

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            var lastException: Throwable? = null

            // Keep a plain videoId endpoint plain. Radio callers use YouTube's normal,
            // personalized next/related queue instead of switching to an RDAMVM Music Mix.
            if (continuation == null) {
                loadedRadioIds.clear()
                if (isRadioRequest) {
                    endpoint.videoId?.let(loadedRadioIds::add)
                    preloadItem?.id?.let(loadedRadioIds::add)
                }
            }

            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()

                    var items = nextResult.items
                    val relEndpoint = nextResult.relatedEndpoint

                    if (isRadioRequest && continuation == null && items.size <= 1) {
                        if (endpoint.playlistId?.startsWith("RDAMVM") == true) {
                            throw EmptyRadioQueueException()
                        } else if (relEndpoint != null) {
                            val relatedPage = YouTube.related(relEndpoint).getOrNull()
                            if (relatedPage != null && relatedPage.songs.isNotEmpty()) {
                                val existingIds = items.mapTo(mutableSetOf()) { it.id }
                                val relatedSongs = relatedPage.songs
                                    .filter { it.id != endpoint.videoId && it.id !in existingIds }
                                    .distinctBy { it.id }
                                items = items + relatedSongs
                            }
                        }
                    }

                    val selectedItemId =
                        nextResult.currentIndex?.let { items.getOrNull(it)?.id }
                    val normalizedItems =
                        if (isRadioRequest) {
                            items.onlyUnseenRadioItems(mutableSetOf())
                        } else {
                            items
                        }
                    val selectedIndex =
                        if (isRadioRequest) {
                            selectedItemId?.let { id ->
                                normalizedItems.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                            }
                        } else {
                            nextResult.currentIndex
                        }
                    val (queueItems, mediaItemIndex) =
                        normalizedItems.ensurePreloadItem(preloadItem, selectedIndex)
                    if (isRadioRequest) {
                        loadedRadioIds.addAll(queueItems.map { it.id })
                    }

                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    return@withContext Queue.Status(
                        title = nextResult.title,
                        items = queueItems.map { it.toMediaItem() },
                        mediaItemIndex = mediaItemIndex,
                    )
                } catch (e: Exception) {
                    lastException = e
                    if (
                        e is EmptyRadioQueueException &&
                        endpoint.playlistId?.startsWith("RDAMVM") == true &&
                        endpoint.videoId != null
                    ) {
                        endpoint = WatchEndpoint(videoId = endpoint.videoId)
                        // It will loop again and try with just videoId
                    }
                }
            }
            throw lastException ?: Exception("Failed to get initial status")
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            var lastException: Throwable? = null
            var skippedEmptyRadioPages = 0

            for (attempt in 0..maxRetries) {
                try {
                    while (true) {
                        val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                        endpoint = nextResult.endpoint
                        continuation = nextResult.continuation
                        retryCount = 0
                        val pageItems =
                            if (isRadioRequest) {
                                nextResult.items.onlyUnseenRadioItems(loadedRadioIds)
                            } else {
                                nextResult.items
                            }

                        if (
                            isRadioRequest &&
                            shouldSkipEmptyRadioPage(
                                items = pageItems,
                                hasNextPage = continuation != null,
                                skippedPages = skippedEmptyRadioPages,
                            )
                        ) {
                            skippedEmptyRadioPages++
                            continue
                        }

                        return@withContext pageItems.map { it.toMediaItem() }
                    }
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    if (retryCount >= maxRetries) {
                        continuation = null // Stop trying to load more; do not restart the initial page.
                        break
                    }
                }
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    companion object {
        /**
         * Shared seed endpoint for explicit and seamless radio playback.
         * Keeping it as a plain videoId makes both entry points use the same
         * YouTube next/related recommendation source.
         */
        fun radioEndpoint(videoId: String): WatchEndpoint =
            WatchEndpoint(videoId = videoId)

        fun radio(song: MediaMetadata): YouTubeQueue =
            YouTubeQueue(
                endpoint = radioEndpoint(song.id),
                preloadItem = song,
            )
    }
}


/**
 * Keep the actual preloaded song aligned with Queue.Status.mediaItemIndex.
 *
 * MusicService already plays [preloadItem] before adding the returned queue.
 * The index must therefore point to that exact song, not an unrelated selected
 * marker; if YouTube omits the seed, insert it so the first recommendation is
 * never skipped as though it were the preloaded song.
 */
internal fun List<SongItem>.ensurePreloadItem(
    preloadItem: MediaMetadata?,
    selectedIndex: Int?,
): Pair<List<SongItem>, Int> {
    val preload = preloadItem ?: return this to (selectedIndex ?: 0)
    val existingIndex = indexOfFirst { it.id == preload.id }
    if (existingIndex >= 0) return this to existingIndex

    return (listOf(preload.toYTItem()) + this) to 0
}



/** Skip a bounded number of duplicate-only radio pages while continuation is still available. */
internal fun shouldSkipEmptyRadioPage(
    items: List<SongItem>,
    hasNextPage: Boolean,
    skippedPages: Int,
): Boolean =
    items.isEmpty() && hasNextPage && skippedPages < MAX_EMPTY_RADIO_PAGES_TO_SKIP

private const val MAX_EMPTY_RADIO_PAGES_TO_SKIP = 3

/** Keep only the first occurrence of each radio item across the loaded pages. */
internal fun List<SongItem>.onlyUnseenRadioItems(seenIds: MutableSet<String>): List<SongItem> =
    filter { seenIds.add(it.id) }
