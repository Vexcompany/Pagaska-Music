package com.metrolist.music.playback.queues

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeQueueTest {
    @Test
    fun `radio endpoint uses the plain video id seed`() {
        val endpoint = YouTubeQueue.radioEndpoint("seed-video-id")

        assertEquals("seed-video-id", endpoint.videoId)
        assertNull(endpoint.playlistId)
    }
}
