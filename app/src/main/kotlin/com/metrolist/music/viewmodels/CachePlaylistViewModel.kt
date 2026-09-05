/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.Cache
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.di.DownloadCache
import com.metrolist.music.di.PlayerCache
import com.metrolist.music.extensions.filterExplicit
import com.metrolist.music.extensions.filterVideoSongs
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        @PlayerCache private val playerCache: Cache,
        @DownloadCache private val downloadCache: Cache,
    ) : ViewModel() {
        private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
        val cachedSongs: StateFlow<List<Song>> = _cachedSongs

        init {
            viewModelScope.launch {
                while (true) {
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

                    // The Cache Playlist is a view of the actual player cache, not a
                    // historical download/date flag. A song is shown as cached only when
                    // its complete media range is present in playerCache. Downloaded songs
                    // are deliberately excluded: Downloaded and streaming Cached are two
                    // separate library states even when both caches contain the same id.
                    val candidateIds = playerCache.keys.toSet()
                    val songs =
                        if (candidateIds.isNotEmpty()) {
                            database.getSongsByIds(candidateIds.toList())
                        } else {
                            emptyList()
                        }

                    val stillValid = mutableListOf<Song>()

                    for (song in songs) {
                        if (song.song.isDownloaded) continue

                        val contentLength = song.format?.contentLength ?: continue
                        if (playerCache.isCached(song.song.id, 0, contentLength)) {
                            stillValid += song
                        } else if (song.song.dateDownload != null) {
                            // Clean up the legacy marker when the LRU cache has evicted
                            // the complete file. This keeps old database state harmless.
                            database.query { update(song.song.copy(dateDownload = null, isCached = false)) }
                        }
                    }

                    _cachedSongs.value =
                        stillValid
                            .sortedByDescending { it.song.dateDownload }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)

                    delay(1000)
                }
            }
        }

        fun removeSongFromCache(songId: String) {
            playerCache.removeResource(songId)
            viewModelScope.launch {
                database.query {
                    getSongsByIds(listOf(songId)).firstOrNull()?.let { song ->
                        if (!song.song.isDownloaded) {
                            update(song.song.copy(dateDownload = null, isCached = false))
                        }
                    }
                }
            }
        }
    }
