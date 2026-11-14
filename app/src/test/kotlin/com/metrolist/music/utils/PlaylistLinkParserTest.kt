package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

class PlaylistLinkParserTest {
    @Test
    fun parsesSpotifyPlaylistAndAlbumLinks() {
        val playlist = PlaylistLinkParser.parse("https://open.spotify.com/playlist/0123456789ABCDEFGHIJKL?si=test")
        val album = PlaylistLinkParser.parse("https://open.spotify.com/intl-id/album/0123456789ABCDEFGHIJKL")

        assertEquals(ExternalPlaylistSource.SPOTIFY, playlist?.source)
        assertEquals("playlist", playlist?.spotifyType)
        assertEquals(ExternalPlaylistSource.SPOTIFY, album?.source)
        assertEquals("album", album?.spotifyType)
    }

    @Test
    fun parsesAppleMusicPlaylistAndStorefront() {
        val playlist = PlaylistLinkParser.parse(
            "https://music.apple.com/id/playlist/example/pl.u-1234567890abcdef",
        )

        assertEquals(ExternalPlaylistSource.APPLE_MUSIC, playlist?.source)
        assertEquals("id", playlist?.storefront)
        assertEquals("pl.u-1234567890abcdef", playlist?.id)
    }

    @Test
    fun rejectsUnsupportedAndMalformedLinks() {
        assertNull(PlaylistLinkParser.parse("https://example.com/playlist/123"))
        assertNull(PlaylistLinkParser.parse("https://open.spotify.com/playlist/not-a-spotify-id"))
        assertNull(PlaylistLinkParser.parse("http://open.spotify.com/playlist/0123456789ABCDEFGHIJKL"))
        assertNull(PlaylistLinkParser.parse("https://user@open.spotify.com/playlist/0123456789ABCDEFGHIJKL"))
        assertNull(PlaylistLinkParser.parse("https://open.spotify.com:8443/playlist/0123456789ABCDEFGHIJKL"))
        assertNull(PlaylistLinkParser.parse("https://music.apple.com:8080/id/playlist/example/pl.u-1234567890abcdef"))
    }
    @Test
    fun resolvesOnlyTrustedAppleMusicPaginationUrls() {
        val current = "https://amp-api.music.apple.com/v1/catalog/id/playlists/pl.u-test/tracks?limit=100"

        assertEquals(
            "https://amp-api.music.apple.com/v1/catalog/id/playlists/pl.u-test/tracks?offset=100",
            resolveAppleMusicNextUrl(current, "/v1/catalog/id/playlists/pl.u-test/tracks?offset=100", "id", "pl.u-test"),
        )
        assertNull(
            resolveAppleMusicNextUrl(current, "https://attacker.example/collect", "id", "pl.u-test"),
        )
        assertNull(
            resolveAppleMusicNextUrl(current, "http://amp-api.music.apple.com/v1/catalog/id/playlists/pl.u-test/tracks?offset=100", "id", "pl.u-test"),
        )
        assertNull(
            resolveAppleMusicNextUrl(current, "/v1/catalog/id/playlists/pl.other/tracks?offset=100", "id", "pl.u-test"),
        )
    }

    @Test
    fun onlyAllowsTrustedSpotifyShortLinkRedirectHosts() {
        assertEquals(true, isTrustedSpotifyRedirectUrl(URI("https://spotify.link/abc")))
        assertEquals(true, isTrustedSpotifyRedirectUrl(URI("https://open.spotify.com/playlist/0123456789ABCDEFGHIJKL")))
        assertEquals(false, isTrustedSpotifyRedirectUrl(URI("https://attacker.example/collect")))
        assertEquals(false, isTrustedSpotifyRedirectUrl(URI("http://open.spotify.com/playlist/0123456789ABCDEFGHIJKL")))
        assertEquals(false, isTrustedSpotifyRedirectUrl(URI("https://open.spotify.com.attacker.example/playlist/0123456789ABCDEFGHIJKL")))
    }

}
