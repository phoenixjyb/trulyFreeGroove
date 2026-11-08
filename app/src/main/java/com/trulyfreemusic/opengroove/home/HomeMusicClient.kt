package com.trulyfreemusic.opengroove.home

import com.trulyfreemusic.opengroove.model.PlaybackMode
import com.trulyfreemusic.opengroove.model.Track
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom

/** Credentials never belong in Track, artwork, queue persistence, logs or exception messages. */
class HomeMusicConfig(server: String, val username: String, val password: String) {
    val server: String = normalizeHomeServer(server)
    val accountKey: String = digest("SHA-256", "${this.server}\n$username").take(32)
    init { require(username.isNotBlank() && password.isNotEmpty()) }
    override fun toString() = "HomeMusicConfig(redacted)"
}

internal fun normalizeHomeServer(value: String): String {
    val uri = URI(value.trim().trimEnd('/'))
    require(uri.scheme in setOf("http", "https") && uri.rawUserInfo == null && uri.rawQuery == null && uri.fragment == null)
    val parts = uri.host.orEmpty().split('.')
    require(parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 && it == it.toIntOrNull().toString() })
    val n = parts.map(String::toInt)
    require(n[0] == 10 || n[0] == 127 || n[0] == 192 && n[1] == 168 || n[0] == 172 && n[1] in 16..31)
    require(uri.port == -1 || uri.port in 1..65535)
    require(!uri.rawPath.orEmpty().contains("%") && !uri.path.orEmpty().contains(".."))
    return uri.toASCIIString().trimEnd('/')
}

internal fun digest(algorithm: String, value: String): String = MessageDigest.getInstance(algorithm)
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
internal fun encodeHome(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

class HomeMusicException(val uiMessage: String) : IOException(uiMessage)

data class HomeAlbum(val id: String, val title: String, val artist: String, val songCount: Int)
data class HomeSearchResult(val albums: List<HomeAlbum>, val songs: List<Track>)

class HomeMusicClient(
    val config: HomeMusicConfig,
    private val transport: (String) -> String = ::readHomeJson,
) {
    fun url(endpoint: String, parameters: Map<String, String> = emptyMap(), salt: String = randomSalt()): String {
        require(endpoint in setOf("ping", "getAlbumList2", "getAlbum", "search3", "stream"))
        val auth = linkedMapOf(
            "u" to config.username, "t" to digest("MD5", config.password + salt), "s" to salt,
            "v" to "1.16.1", "c" to "OpenGroove", "f" to "json",
        )
        require(parameters.keys.none { it in auth })
        auth.putAll(parameters)
        return "${config.server}/rest/$endpoint.view?" + auth.entries.joinToString("&") { (k, v) -> "${encodeHome(k)}=${encodeHome(v)}" }
    }

    fun ping() { request("ping") }
    fun albums(offset: Int = 0): List<HomeAlbum> = request("getAlbumList2", mapOf(
        "type" to "alphabeticalByName", "size" to PAGE_SIZE.toString(), "offset" to offset.toString(),
    )).optJSONObject("albumList2")?.albums("album").orEmpty()

    fun album(id: String): List<Track> = request("getAlbum", mapOf("id" to id))
        .optJSONObject("album")?.songs("song").orEmpty()

    fun search(query: String, albumOffset: Int = 0, songOffset: Int = 0): HomeSearchResult {
        val result = request("search3", mapOf(
            "query" to query, "artistCount" to "0", "albumCount" to PAGE_SIZE.toString(),
            "songCount" to PAGE_SIZE.toString(), "albumOffset" to albumOffset.toString(), "songOffset" to songOffset.toString(),
        )).optJSONObject("searchResult3") ?: JSONObject()
        return HomeSearchResult(result.albums("album"), result.songs("song"))
    }

    fun streamUrl(reference: String): String {
        val uri = URI(reference)
        require(uri.scheme == "home-music" && uri.host == config.accountKey && uri.rawQuery == null && uri.fragment == null)
        val segment = uri.rawPath.removePrefix("/")
        require(segment.isNotBlank() && !segment.contains('/'))
        val id = URLDecoder.decode(segment, "UTF-8")
        return url("stream", mapOf("id" to id, "format" to "mp3", "maxBitRate" to "192"))
    }

    private fun request(endpoint: String, parameters: Map<String, String> = emptyMap()): JSONObject {
        try {
            val root = JSONObject(transport(url(endpoint, parameters))).getJSONObject("subsonic-response")
            if (root.optString("status") != "ok") {
                throw HomeMusicException(if (root.optJSONObject("error")?.optInt("code") in listOf(40, 41, 50))
                    "Home Music sign-in failed. Check the account and password."
                else "Home Music server rejected the request.")
            }
            return root
        } catch (error: HomeMusicException) { throw error }
        catch (_: Exception) { throw HomeMusicException("Home Music is unavailable. Check home Wi-Fi and the server.") }
    }

    private fun JSONObject.albums(key: String): List<HomeAlbum> = optJSONArray(key)?.let { array ->
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { album ->
            album.optString("id").takeIf(String::isNotBlank)?.let { HomeAlbum(it, album.optString("name"), album.optString("artist"), album.optInt("songCount")) }
        } }
    }.orEmpty()

    private fun JSONObject.songs(key: String): List<Track> = optJSONArray(key)?.let { array ->
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { song ->
            val id = song.optString("id")
            if (id.isBlank() || song.optBoolean("isDir") || song.optBoolean("isVideo")) null else Track(
                id = "home:${config.accountKey}:$id", title = song.optString("title").ifBlank { id },
                artist = song.optString("artist"), album = song.optString("album"),
                durationSeconds = song.optInt("duration"), artworkUrl = "", providerName = "Home Music",
                streamUrl = "home-music://${config.accountKey}/${encodeHome(id)}", sourceUrl = config.server,
                licenseUrl = "", playbackMode = PlaybackMode.HOME_LIBRARY,
            )
        } }
    }.orEmpty()

    companion object { const val PAGE_SIZE = 50 }
}

private fun randomSalt(): String = ByteArray(16).apply { SecureRandom().nextBytes(this) }
    .joinToString("") { "%02x".format(it) }

private fun readHomeJson(url: String): String {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        if (connection.responseCode != 200) throw HomeMusicException("Home Music is unavailable. Check home Wi-Fi and the server.")
        return connection.inputStream.bufferedReader().use { reader ->
            // Bound untrusted metadata; never load audio through this path.
            val buffer = CharArray(8192)
            val result = StringBuilder()
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (result.length + count > 4_000_000) throw IOException("Metadata too large")
                result.append(buffer, 0, count)
            }
            result.toString()
        }
    } finally { connection.disconnect() }
}
