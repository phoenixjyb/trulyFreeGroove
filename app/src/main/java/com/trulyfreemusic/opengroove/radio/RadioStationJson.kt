package com.trulyfreemusic.opengroove.radio

import org.json.JSONArray
import org.json.JSONObject

internal fun RadioStation.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("streamUrl", streamUrl)
    put("homepageUrl", homepageUrl)
    put("faviconUrl", faviconUrl)
    put("country", country)
    put("countryCode", countryCode)
    put("language", language)
    put("tags", JSONArray(tags))
    put("codec", codec)
    put("bitrate", bitrate)
    put("votes", votes)
    put("isOnline", isOnline)
    put("lastCheckedAt", lastCheckedAt)
    put("isHls", isHls)
}

internal fun JSONObject.toRadioStationOrNull(): RadioStation? {
    val tagsArray = optJSONArray("tags") ?: JSONArray()
    val tags = buildList {
        for (index in 0 until tagsArray.length()) add(tagsArray.optString(index))
    }.filter(String::isNotBlank)
    val station = RadioStation(
        id = optString("id"),
        name = optString("name"),
        streamUrl = optString("streamUrl"),
        homepageUrl = optString("homepageUrl"),
        faviconUrl = optString("faviconUrl"),
        country = optString("country"),
        countryCode = optString("countryCode"),
        language = optString("language"),
        tags = tags,
        codec = optString("codec"),
        bitrate = optInt("bitrate"),
        votes = optInt("votes"),
        isOnline = optBoolean("isOnline", true),
        lastCheckedAt = optString("lastCheckedAt"),
        isHls = optBoolean("isHls"),
    )
    return station.takeIf(RadioStation::isPlayable)
}
