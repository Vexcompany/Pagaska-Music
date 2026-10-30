package com.metrolist.innertube.pages

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.MusicResponsiveListItemRenderer
import com.metrolist.innertube.models.MusicTwoRowItemRenderer
import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.metrolist.innertube.models.SongItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RelatedPageTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `related song survives when album text has no browse endpoint`() {
        val renderer = json.decodeFromString<MusicResponsiveListItemRenderer>(
            """
            {
              "flexColumns": [
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song title"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                  {"text":"Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}},
                  {"text":" • "},
                  {"text":"2026"}
                ]}}},
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Album without link"}]}}}
              ],
              "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"cover","width":100,"height":100}]}}},
              "overlay":{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{
                "playNavigationEndpoint":{"watchEndpoint":{
                  "videoId":"song-id",
                  "watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}
                }}
              }}}}
            }
            """.trimIndent(),
        )

        val song = RelatedPage.fromMusicResponsiveListItemRenderer(renderer) as? SongItem

        assertNotNull(song)
        assertEquals("song-id", song?.id)
        assertEquals(listOf(Artist(name = "Artist", id = "UCartist")), song?.artists)
        assertEquals(MUSIC_VIDEO_TYPE_ATV, song?.musicVideoType)
        assertNull(song?.album)
    }
    @Test
    fun related_two_row_song_preserves_artist_album_duration_and_audio_type() {
        val renderer = json.decodeFromString<MusicTwoRowItemRenderer>(
            """
            {
              "title":{"runs":[{"text":"Two-row song","navigationEndpoint":null}]},
              "subtitle":{"runs":[
                {"text":"Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}},
                {"text":" • ","navigationEndpoint":null},
                {"text":"Album","navigationEndpoint":{"browseEndpoint":{
                  "browseId":"MPREb_album",
                  "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}
                }}},
                {"text":" • ","navigationEndpoint":null},
                {"text":"3:42","navigationEndpoint":null}
              ]},
              "subtitleBadges":null,
              "menu":null,
              "thumbnailRenderer":{
                "musicThumbnailRenderer":{
                  "thumbnail":{"thumbnails":[{"url":"https://example.com/cover.jpg","width":100,"height":100}]},
                  "thumbnailCrop":null,
                  "thumbnailScale":null
                },
                "musicAnimatedThumbnailRenderer":null,
                "croppedSquareThumbnailRenderer":null
              },
              "navigationEndpoint":{"watchEndpoint":{
                "videoId":"two-row-song",
                "watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}
              }},
              "thumbnailOverlay":null
            }
            """.trimIndent(),
        )

        val song = RelatedPage.fromMusicTwoRowItemRenderer(renderer) as? SongItem

        assertNotNull(song)
        assertEquals("two-row-song", song?.id)
        assertEquals(listOf(Artist(name = "Artist", id = "UCartist")), song?.artists)
        assertEquals(Album(name = "Album", id = "MPREb_album"), song?.album)
        assertEquals(222, song?.duration)
        assertEquals(MUSIC_VIDEO_TYPE_ATV, song?.musicVideoType)
        assertEquals("https://example.com/cover.jpg", song?.thumbnail)
        assertEquals("two-row-song", song?.endpoint?.videoId)
    }

}
