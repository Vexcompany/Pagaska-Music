package com.metrolist.music.utils

import androidx.media3.common.PlaybackException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private const val MAX_PLAYBACK_ERROR_CAUSE_DEPTH = 8

internal fun Throwable.playbackCauseChain(maxDepth: Int = MAX_PLAYBACK_ERROR_CAUSE_DEPTH): List<Throwable> {
    val seen = java.util.Collections.newSetFromMap(
        java.util.IdentityHashMap<Throwable, Boolean>(),
    )
    val result = ArrayList<Throwable>(maxDepth)
    var current: Throwable? = this
    while (current != null && result.size < maxDepth && seen.add(current)) {
        result += current
        current = current.cause
    }
    return result
}

/**
 * Distinguishes transport failures from stream/client failures.
 *
 * Media3 may wrap a DNS, socket, or TLS failure inside ERROR_CODE_IO_UNSPECIFIED.
 * Those errors should wait for connectivity/retry normally instead of poisoning
 * the per-video stream-client exclusion list.
 */
internal fun PlaybackException.isNetworkRelatedPlaybackError(): Boolean {
    val causes = playbackCauseChain()
    return causes.any { cause ->
        cause is ConnectException ||
            cause is UnknownHostException ||
            cause is SocketException ||
            cause is InterruptedIOException ||
            cause is SSLException
    } || causes
        .filterIsInstance<PlaybackException>()
        .any { cause ->
            cause.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                cause.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        }
}

/**
 * True only for failures that are useful signals to try another YouTube stream client.
 * Known transport failures are deliberately excluded.
 */
internal fun PlaybackException.isStreamClientPlaybackError(): Boolean {
    if (isNetworkRelatedPlaybackError()) return false

    return playbackCauseChain()
        .filterIsInstance<PlaybackException>()
        .any { cause ->
            cause.errorCode in setOf(
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            )
        }
}
