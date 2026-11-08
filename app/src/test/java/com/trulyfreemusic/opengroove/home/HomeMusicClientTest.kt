package com.trulyfreemusic.opengroove.home

import com.trulyfreemusic.opengroove.model.PlaybackMode
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class HomeMusicClientTest {
    private val config = HomeMusicConfig("http://192.168.1.20:4533/", "listener", "sesame")
    @Test fun tokenAuthMatchesProtocolAndDoesNotSendPassword() {
        val params = params(HomeMusicClient(config).url("ping", salt = "c19b2d"))
        assertEquals("26719a1196d2a940705a59634eb18eab", params["t"])
        assertEquals("1.16.1", params["v"])
        assertEquals("OpenGroove", params["c"])
        assertFalse(params.containsKey("p"))
        assertFalse(config.toString().contains("sesame"))
        assertNotEquals(HomeMusicClient(config).url("ping"), HomeMusicClient(config).url("ping"))
    }
    @Test fun serverIsLimitedToExplicitPrivateAddressesWithoutEmbeddedCredentials() {
        listOf("http://example.com", "http://8.8.8.8", "file:///tmp/a", "http://user:pass@192.168.1.1", "http://192.168.1.1/?p=secret", "http://192.168.1.1/#x", "http://192.168.001.1", "http://192.168.1.1/../a").forEach { url ->
            assertTrue(url, runCatching { HomeMusicConfig(url, "a", "b") }.isFailure)
        }
        assertEquals("http://192.168.1.20:4533", config.server)
        assertEquals("https://10.0.0.4/music", HomeMusicConfig("https://10.0.0.4/music/", "a", "b").server)
    }
    @Test fun albumAndSearchUsePagingAndSongsAreCredentialFreeReferences() {
        val urls = mutableListOf<String>()
        val client = HomeMusicClient(config) { url ->
            urls += url
            when {
                "getAlbumList2" in url -> response("\"albumList2\":{\"album\":[{\"id\":\"a\",\"name\":\"家 Music\",\"artist\":\"Artist\",\"songCount\":2}]}")
                "getAlbum.view" in url -> response("\"album\":{\"song\":[{\"id\":\"x\",\"title\":\"Tone\",\"duration\":12},{\"id\":\"video\",\"isVideo\":true}]}")
                else -> response("\"searchResult3\":{\"song\":[{\"id\":\"x\",\"title\":\"Tone\"}]}")
            }
        }
        assertEquals("家 Music", client.albums(50).single().title)
        assertEquals("50", params(urls.last())["offset"])
        val song = client.album("a").single()
        assertEquals(PlaybackMode.HOME_LIBRARY, song.playbackMode)
        assertTrue(song.isDirectPlaybackAllowed())
        assertTrue(song.streamUrl.startsWith("home-music://"))
        assertEquals("", song.artworkUrl)
        assertFalse(song.toString().contains("sesame"))
        assertEquals("", song.licenseUrl)
        assertEquals("Tone", client.search("家 & Music", 50, 100).songs.single().title)
        assertEquals("家 & Music", params(urls.last())["query"])
        assertEquals("100", params(urls.last())["songOffset"])
        val stream = params(client.streamUrl(song.streamUrl))
        assertEquals("mp3", stream["format"])
        assertEquals("192", stream["maxBitRate"])
        assertEquals("x", stream["id"])
        assertEquals("folder/家 + song", params(client.streamUrl("home-music://${config.accountKey}/${encodeHome("folder/家 + song")}"))["id"])
        assertTrue(runCatching { HomeMusicClient(HomeMusicConfig(config.server, "other", "pw")).streamUrl(song.streamUrl) }.isFailure)
    }
    @Test fun serverErrorsAreRedactedAndEmptyLibrariesWork() {
        val empty = HomeMusicClient(config) { response("\"searchResult3\":{}") }
        assertTrue(empty.search("x").songs.isEmpty())
        val rejected = HomeMusicClient(config) { """{"subsonic-response":{"status":"failed","error":{"code":40,"message":"sesame"}}}""" }
        val error = runCatching { rejected.ping() }.exceptionOrNull()!!
        assertTrue(error.message!!.contains("sign-in failed"))
        assertFalse(error.toString().contains("sesame"))
    }
    @Test fun realHttpPathRejectsRedirectsAndParsesJson() {
        val server = ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = thread {
            repeat(2) { index ->
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* consume headers */ }
                    val response = if (index == 0) {
                        val body = response("\"version\":\"1.16.1\"")
                        "HTTP/1.1 200 OK\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
                    } else "HTTP/1.1 302 Found\r\nLocation: http://8.8.8.8/should-never-be-followed\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(response.toByteArray())
                }
            }
        }
        try {
            val api = HomeMusicClient(HomeMusicConfig("http://127.0.0.1:${server.localPort}", "a", "b"))
            api.ping()
            assertTrue(runCatching { api.ping() }.exceptionOrNull() is HomeMusicException)
        } finally { server.close(); worker.join(5_000) }
    }

    private fun response(body: String) = "{\"subsonic-response\":{\"status\":\"ok\",$body}}"
    private fun params(url: String) = URI(url).rawQuery.split('&').associate {
        val pair = it.split('=', limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
    }
}
