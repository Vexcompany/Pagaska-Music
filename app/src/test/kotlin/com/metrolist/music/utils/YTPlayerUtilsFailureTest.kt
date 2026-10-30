package com.metrolist.music.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YTPlayerUtilsFailureTest {
    @After
    fun tearDown() {
        YTPlayerUtils.clearStreamClientFailures()
    }

    @Test
    fun failedClientsAccumulatePerVideoAndExpire() {
        YTPlayerUtils.markStreamClientFailed("song-a", "WEB_REMIX", nowMs = 1_000L)
        YTPlayerUtils.markStreamClientFailed("song-a", "TVHTML5", nowMs = 2_000L)

        assertEquals(
            setOf("WEB_REMIX", "TVHTML5"),
            YTPlayerUtils.failedStreamClients("song-a", nowMs = 2_000L),
        )
        assertTrue(YTPlayerUtils.failedStreamClients("song-b", nowMs = 2_000L).isEmpty())
        assertTrue(YTPlayerUtils.failedStreamClients("song-a", nowMs = 302_000L).isEmpty())
    }

    @Test
    fun cipherRecoveryClearsFailedClientState() {
        YTPlayerUtils.markStreamClientFailed("song-a", "WEB_REMIX", nowMs = 1_000L)

        YTPlayerUtils.clearStreamClientFailures()

        assertTrue(YTPlayerUtils.failedStreamClients("song-a", nowMs = 2_000L).isEmpty())
    }
}
