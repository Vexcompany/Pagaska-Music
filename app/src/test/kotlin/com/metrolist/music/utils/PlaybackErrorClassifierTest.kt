package com.metrolist.music.utils

import androidx.media3.common.PlaybackException
import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorClassifierTest {
    @Test
    fun `nested dns failure prevents stream client rotation for unspecified error`() {
        val causes = IOException("request failed", UnknownHostException("dns lookup failed"))
            .playbackCauseChain()

        assertTrue(causes.hasNetworkTransportCause())
        assertFalse(
            isStreamClientFailure(
                causes,
                listOf(PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
            ),
        )
    }

    @Test
    fun `unspecified source failure without network cause can rotate client`() {
        assertFalse(emptyList<Throwable>().hasNetworkTransportCause())
        assertTrue(
            isStreamClientFailure(
                emptyList(),
                listOf(PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
            ),
        )
    }

    @Test
    fun `malformed media container can rotate client`() {
        assertTrue(
            isStreamClientFailure(
                emptyList(),
                listOf(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED),
            ),
        )
    }

    @Test
    fun `network playback error code is not treated as stream client failure`() {
        assertFalse(
            isStreamClientFailure(
                emptyList(),
                listOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT),
            ),
        )
    }
}
