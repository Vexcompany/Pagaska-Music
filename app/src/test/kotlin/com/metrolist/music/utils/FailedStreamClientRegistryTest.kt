package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FailedStreamClientRegistryTest {
    @Test
    fun `failed clients are isolated per video and expire`() {
        val registry = FailedStreamClientRegistry(ttlMs = 1_000L)

        registry.markFailed("video-a", "WEB_REMIX@1", nowMs = 100L)
        registry.markFailed("video-a", "TVHTML5@7", nowMs = 200L)
        registry.markFailed("video-b", "ANDROID_VR@1", nowMs = 200L)

        assertEquals(
            setOf("WEB_REMIX@1", "TVHTML5@7"),
            registry.failedClientIds("video-a", nowMs = 500L),
        )
        assertEquals(
            setOf("ANDROID_VR@1"),
            registry.failedClientIds("video-b", nowMs = 500L),
        )
        assertEquals(emptySet<String>(), registry.failedClientIds("video-a", nowMs = 1_200L))
    }

    @Test
    fun `failure after expiry starts a fresh client set`() {
        val registry = FailedStreamClientRegistry(ttlMs = 1_000L)

        registry.markFailed("video-a", "WEB_REMIX@1", nowMs = 100L)
        registry.markFailed("video-a", "TVHTML5@7", nowMs = 1_200L)

        assertEquals(
            setOf("TVHTML5@7"),
            registry.failedClientIds("video-a", nowMs = 1_300L),
        )
    }
}
