package com.metrolist.music.utils

import com.metrolist.innertube.models.response.PlayerResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayabilityStatusExtTest {
    @Test
    fun `generic login required is not treated as an age gate`() {
        val status = PlayerResponse.PlayabilityStatus(
            status = "LOGIN_REQUIRED",
            reason = "Please sign in",
        )

        assertFalse(status.isAgeRestrictedForPlayback())
    }

    @Test
    fun `login required with legacy age gate reason is age restricted`() {
        val status = PlayerResponse.PlayabilityStatus(
            status = "LOGIN_REQUIRED",
            reason = "Please sign in to confirm your age",
            desktopLegacyAgeGateReason = 1,
        )

        assertTrue(status.isAgeRestrictedForPlayback())
    }

    @Test
    fun `explicit age statuses remain age restricted`() {
        listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED",
        ).forEach { status ->
            assertTrue(
                PlayerResponse.PlayabilityStatus(status = status, reason = null)
                    .isAgeRestrictedForPlayback(),
            )
        }
    }
}
