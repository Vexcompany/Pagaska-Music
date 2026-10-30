package com.metrolist.innertube.pages

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.MusicResponsiveListItemRenderer
import com.metrolist.innertube.models.MusicTwoRowItemRenderer
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.utils.parseTime

data class RelatedPage(
    val songs: List<SongItem>,
    val albums: List<AlbumItem>,
    val artists: List<ArtistItem>,
    val playlists: List<PlaylistItem>,
) {
    companion object {
        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            // Extract library tokens using the new method that properly handles multiple toggle items
            val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

            return SongItem(
                id = renderer.videoId ?: return null,
                title =
                    renderer.flexColumns
                        .firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.runs
                        ?.firstOrNull()
                        ?.text ?: return null,
                artists = PageHelper.extractArtists(
                    renderer.flexColumns
                        .getOrNull(1)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.runs,
                ),
                album =
                    renderer.flexColumns
                        .getOrNull(2)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.runs
                        ?.firstOrNull()
                        ?.let { run ->
                            run.navigationEndpoint?.browseEndpoint?.browseId?.let { browseId ->
                                Album(name = run.text, id = browseId)
                            }
                        },
                duration = renderer.fixedColumns?.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.text?.parseTime(),
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                explicit =
                    renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null,
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
                isEpisode = renderer.isEpisode
            )
        }

        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
            val songEndpoint =
                renderer.navigationEndpoint.anyWatchEndpoint?.takeIf { it.videoId != null }
                    ?: renderer.thumbnailOverlay
                        ?.musicItemThumbnailOverlayRenderer
                        ?.content
                        ?.musicPlayButtonRenderer
                        ?.playNavigationEndpoint
                        ?.anyWatchEndpoint
                        ?.takeIf { it.videoId != null }

            return when {
                songEndpoint != null -> {
                    val endpoint = songEndpoint
                    val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(
                        renderer.menu?.menuRenderer?.items,
                    )

                    SongItem(
                        id = endpoint.videoId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = PageHelper.extractArtists(renderer.subtitle?.runs),
                        album = renderer.subtitle?.runs.orEmpty().firstNotNullOfOrNull { run ->
                            run.navigationEndpoint?.browseEndpoint
                                ?.takeIf { it.isAlbumEndpoint }
                                ?.let { Album(name = run.text, id = it.browseId) }
                        },
                        duration = PageHelper.extractDuration(renderer.subtitle?.runs),
                        musicVideoType = renderer.musicVideoType,
                        thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.any {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } == true,
                        endpoint = endpoint,
                        setVideoId = endpoint.playlistSetVideoId,
                        libraryAddToken = libraryTokens.addToken,
                        libraryRemoveToken = libraryTokens.removeToken,
                        isEpisode = renderer.isEpisode,
                    )
                }
                renderer.isAlbum ->
                    AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint
                                ?.playlistId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists = listOfNotNull(Artist(
                                name = "",
                                id = renderer.menu?.menuRenderer?.items?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "ARTIST"
                                }?.menuNavigationItemRenderer?.navigationEndpoint?.browseEndpoint?.browseId,
                            )),
                        year =
                            renderer.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text
                                ?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
                        explicit =
                            renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null,
                    )
                renderer.isPlaylist ->
                    PlaylistItem(
                        id =
                            renderer.navigationEndpoint.browseEndpoint
                                ?.browseId
                                ?.removePrefix("VL") ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        author = Artist(
                            name = renderer.subtitle?.runs?.lastOrNull()?.text ?: return null,
                            id = null
                        ),
                        songCountText = renderer.subtitle.runs.findLast {
                            it.text.any { c -> c.isDigit() } && !it.text.contains("view", ignoreCase = true)
                        }?.text,
                        thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
                        playEndpoint =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint ?: return null,
                        shuffleEndpoint =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint ?: return null,
                        radioEndpoint =
                            renderer.menu.menuRenderer.items
                                .find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint
                    )
                renderer.isArtist -> {
                    ArtistItem(
                        id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
                        shuffleEndpoint =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint ?: return null,
                        radioEndpoint =
                            renderer.menu.menuRenderer.items
                                .find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint ?: return null,
                    )
                }
                renderer.isPodcast -> {
                    PodcastItem(
                        id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        author = renderer.subtitle?.runs?.firstOrNull()?.let {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId
                            )
                        },
                        episodeCountText = renderer.subtitle?.runs?.lastOrNull()?.text,
                        thumbnail = renderer.thumbnailRenderer.getThumbnailUrl(),
                        playEndpoint = renderer.thumbnailOverlay
                            ?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint,
                        shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                            it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                        }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                    )
                }
                else -> null
            }
        }
    }
}
