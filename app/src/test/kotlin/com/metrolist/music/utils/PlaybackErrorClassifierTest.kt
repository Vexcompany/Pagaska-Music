package com.metrolist.music.utils

import androidx.media3.common.PlaybackException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorClassifierTest {
    @Test
    fun `nested dns failure is treated as network not stream client failure`() {
        val error = PlaybackException(
            "source request failed",
            UnknownHostException("dns lookup failed"),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )

        assertTrue(error.isNetworkRelatedPlaybackError())
        assertFalse(error.isStreamClientPlaybackError())
    }

    @Test
    fun `unspecified source failure without network cause can rotate client`() {
        val error = PlaybackException(
            "unspecified source failure",
            null,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )

        assertFalse(error.isNetworkRelatedPlaybackError())
        assertTrue(error.isStreamClientPlaybackError())
    }

    @Test
    fun `malformed media container can rotate client`() {
        val error = PlaybackException(
            "malformed container",
            null,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        )

        assertTrue(error.isStreamClientPlaybackError())
    }
}
