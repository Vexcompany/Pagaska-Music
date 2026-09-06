/**
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.utils

/**
 * Runtime mirror of the "song cache" preferences.
 *
 * Both values are read from threads that must never block:
 *  - the byte budget is read by [com.metrolist.music.playback.DynamicLruCacheEvictor] on every
 *    span add / file start (SimpleCache's internal monitor is held at that point);
 *  - the on/off switch is read by MusicService's data source on every open(), on the ExoPlayer
 *    loading thread.
 *
 * The `SimpleCache` backing `files/exoplayer` is a process-wide singleton and its evictor cannot be
 * swapped after construction, so publishing the limit through this object is what makes the
 * Settings slider effective without an app restart.
 */
object SongCacheConfig {
    /** Slider semantics: `-1` = unlimited, `0` = disabled, anything else = megabytes. */
    const val UNLIMITED_MB = -1

    const val DISABLED_MB = 0

    /** Matches the default of `MaxSongCacheSizeKey` used by AppModule and StorageSettings. */
    const val DEFAULT_MB = 1024

    private const val UNSET = Long.MIN_VALUE

    @Volatile
    private var maxBytesValue: Long = UNSET

    /** True until [update] (or [updateIfUnset]) has been called at least once. */
    val isUnset: Boolean
        get() = maxBytesValue == UNSET

    /**
     * Byte budget for the streaming cache. Falls back to [DEFAULT_MB] when it is read before the
     * settings collectors had a chance to run.
     */
    val maxBytes: Long
        get() = maxBytesValue.takeIf { it != UNSET } ?: fromPreference(DEFAULT_MB)

    /** Converts the stored preference value (MB, `-1` unlimited, `0` disabled) to bytes. */
    fun fromPreference(sizeInMb: Int): Long =
        when {
            sizeInMb == UNLIMITED_MB -> Long.MAX_VALUE
            sizeInMb <= DISABLED_MB -> 0L
            else -> sizeInMb * 1024L * 1024L
        }

    fun update(sizeInMb: Int) {
        maxBytesValue = fromPreference(sizeInMb)
    }

    /** Seeds the budget once, from a blocking DataStore read, before the cache is first used. */
    fun updateIfUnset(sizeInMb: Int) {
        if (isUnset) update(sizeInMb)
    }
}
