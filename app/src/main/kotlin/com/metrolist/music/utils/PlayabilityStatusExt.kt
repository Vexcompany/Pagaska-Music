package com.metrolist.music.utils

import com.metrolist.innertube.models.response.PlayerResponse

/**
 * LOGIN_REQUIRED is not sufficient evidence of an age gate by itself. YouTube also uses it for
 * generic account/token gates; the legacy age-gate reason distinguishes the age-restricted case.
 */
internal fun PlayerResponse.PlayabilityStatus?.isAgeRestrictedForPlayback(): Boolean {
    val playabilityStatus = this ?: return false
    return when (playabilityStatus.status) {
        "AGE_CHECK_REQUIRED",
        "AGE_VERIFICATION_REQUIRED",
        "CONTENT_CHECK_REQUIRED",
        -> true

        "LOGIN_REQUIRED" -> (playabilityStatus.desktopLegacyAgeGateReason ?: 0) > 0
        else -> false
    }
}
