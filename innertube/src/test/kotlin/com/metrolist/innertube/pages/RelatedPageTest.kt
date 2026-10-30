package com.metrolist.innertube.pages

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.MusicResponsiveListItemRenderer
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
}
