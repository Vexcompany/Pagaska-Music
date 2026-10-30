package com.metrolist.music.utils

import androidx.media3.common.PlaybackException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private const val MAX_PLAYBACK_ERROR_CAUSE_DEPTH = 8

private val networkPlaybackErrorCodes =
    setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    )

private val streamClientPlaybackErrorCodes =
    setOf(
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    )

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

internal fun List<Throwable>.hasNetworkTransportCause(): Boolean =
    any { cause ->
        cause is ConnectException ||
            cause is UnknownHostException ||
            cause is SocketException ||
            cause is InterruptedIOException ||
            cause is SSLException
    }

/**
 * Pure classification core so the transport-vs-client decision can be unit-tested
 * without constructing Media3 PlaybackException (which depends on Android clock APIs).
 */
internal fun isStreamClientFailure(
    causes: List<Throwable>,
    errorCodes: List<Int>,
): Boolean {
    if (causes.hasNetworkTransportCause()) return false
    if (errorCodes.any { it in networkPlaybackErrorCodes }) return false
    return errorCodes.any { it in streamClientPlaybackErrorCodes }
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
    return causes.hasNetworkTransportCause() ||
        causes
            .filterIsInstance<PlaybackException>()
            .any { it.errorCode in networkPlaybackErrorCodes }
}

/**
 * True only for failures that are useful signals to try another YouTube stream client.
 * Known transport failures are deliberately excluded.
 */
internal fun PlaybackException.isStreamClientPlaybackError(): Boolean {
    val causes = playbackCauseChain()
    val errorCodes = causes.filterIsInstance<PlaybackException>().map { it.errorCode }
    return isStreamClientFailure(causes, errorCodes)
}
