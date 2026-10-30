package com.metrolist.music.playback.queues

import com.metrolist.innertube.models.SongItem
import com.metrolist.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeQueueTest {
    @Test
    fun `radio endpoint uses the plain video id seed`() {
        val endpoint = YouTubeQueue.radioEndpoint("seed-video-id")

        assertEquals("seed-video-id", endpoint.videoId)
        assertNull(endpoint.playlistId)
    }

    @Test
    fun `missing selected index inserts the preload seed instead of skipping first recommendation`() {
        val recommendation = song("recommendation")
        val (items, index) = listOf(recommendation).ensurePreloadItem(metadata("seed"), null)

        assertEquals(listOf("seed", "recommendation"), items.map { it.id })
        assertEquals(0, index)
    }

    @Test
    fun `missing selected index locates an existing preload seed`() {
        val recommendation = song("recommendation")
        val seed = song("seed")
        val (items, index) = listOf(recommendation, seed).ensurePreloadItem(metadata("seed"), null)

        assertEquals(listOf("recommendation", "seed"), items.map { it.id })
        assertEquals(1, index)
    }

    @Test
    fun `preloaded seed index takes precedence over a different selected marker`() {
        val items = listOf(song("seed"), song("recommendation"))
        val (result, index) = items.ensurePreloadItem(metadata("seed"), 1)

        assertEquals(items, result)
        assertEquals(0, index)
    }

    @Test
    fun `selected index is preserved when there is no preload item`() {
        val items = listOf(song("seed"), song("recommendation"))
        val (result, index) = items.ensurePreloadItem(null, 1)

        assertEquals(items, result)
        assertEquals(1, index)
    }

    @Test
    fun `radio continuation pages omit ids already loaded and duplicates within the page`() {
        val seenIds = mutableSetOf("seed", "already-loaded")
        val page = listOf(
            song("seed"),
            song("already-loaded"),
            song("new-song"),
            song("new-song"),
            song("another-new-song"),
        )

        val result = page.onlyUnseenRadioItems(seenIds)

        assertEquals(listOf("new-song", "another-new-song"), result.map { it.id })
        assertEquals(setOf("seed", "already-loaded", "new-song", "another-new-song"), seenIds)
    }

    private fun song(id: String) = SongItem(
        id = id,
        title = id,
        artists = emptyList(),
        thumbnail = "https://example.com/$id.jpg",
    )

    private fun metadata(id: String) = MediaMetadata(
        id = id,
        title = id,
        artists = listOf(MediaMetadata.Artist(id = null, name = "Artist")),
        duration = 180,
    )
    @Test
    fun radio_continuation_loader_skips_duplicate_only_pages_with_a_bound() {
        assertTrue(shouldSkipEmptyRadioPage(emptyList(), hasNextPage = true, skippedPages = 0))
        assertTrue(shouldSkipEmptyRadioPage(emptyList(), hasNextPage = true, skippedPages = 2))
        assertFalse(shouldSkipEmptyRadioPage(emptyList(), hasNextPage = true, skippedPages = 3))
        assertFalse(shouldSkipEmptyRadioPage(emptyList(), hasNextPage = false, skippedPages = 0))
        assertFalse(shouldSkipEmptyRadioPage(listOf(song("new-song")), hasNextPage = true, skippedPages = 0))
    }

}
