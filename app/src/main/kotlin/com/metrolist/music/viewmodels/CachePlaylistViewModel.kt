/**
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0
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
import com.metrolist.music.playback.OfflineCacheRegistry
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

                    // "Tersimpan di cache" is now the Offline Available view. Explicit downloads
                    // remain physically stored in downloadCache, while automatic Offline Cache
                    // entries live in playerCache. The UI combines both without duplicating bytes.
                    val candidateIds =
                        playerCache.keys.toMutableSet().apply {
                            addAll(downloadCache.keys)
                        }
                    val songs =
                        if (candidateIds.isNotEmpty()) {
                            database.getSongsByIds(candidateIds.toList())
                        } else {
                            emptyList()
                        }

                    val offlineSongs = mutableListOf<Pair<Song, Long>>()

                    for (song in songs) {
                        val contentLength = song.format?.contentLength ?: continue
                        val id = song.song.id

                        if (song.song.isDownloaded) {
                            if (downloadCache.isCached(id, 0L, contentLength)) {
                                // Explicit Download is already offline-ready. Do not touch the
                                // automatic player-cache quota or copy the resource.
                                val lastTouch =
                                    downloadCache.getCachedSpans(id).maxOfOrNull { it.lastTouchTimestamp } ?: 0L
                                offlineSongs += song to lastTouch
                            }
                        } else if (song.song.isCached && playerCache.isCached(id, 0L, contentLength)) {
                            val lastTouch =
                                playerCache.getCachedSpans(id).maxOfOrNull { it.lastTouchTimestamp } ?: 0L
                            offlineSongs += song to lastTouch
                        } else if (song.song.isCached) {
                            // The database says Offline Cache, but the complete physical resource
                            // is gone. Remove only the automatic-cache marker; explicit downloads
                            // have their own source of truth and are never affected here.
                            OfflineCacheRegistry.unprotect(id)
                            database.query {
                                update(song.song.copy(isCached = false))
                            }
                        }
                    }

                    _cachedSongs.value =
                        offlineSongs
                            .sortedByDescending { it.second }
                            .map { it.first }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)

                    delay(1000)
                }
            }
        }

        fun removeSongFromCache(songId: String) {
            viewModelScope.launch {
                val song = database.getSongsByIds(listOf(songId)).firstOrNull() ?: return@launch

                // Explicit Downloads are authoritative and must not be deleted through the
                // automatic Offline Cache control. They have their own download management path.
                if (song.song.isDownloaded) return@launch

                OfflineCacheRegistry.unprotect(songId)
                playerCache.removeResource(songId)
                database.query {
                    update(song.song.copy(isCached = false))
                }
            }
        }
    }
