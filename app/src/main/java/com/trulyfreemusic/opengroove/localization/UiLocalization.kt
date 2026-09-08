package com.trulyfreemusic.opengroove.localization

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

enum class UiLanguage(val languageTag: String, val compactLabel: String) {
    ENGLISH("en", "EN"),
    SIMPLIFIED_CHINESE("zh-Hans", "简中"),
    TRADITIONAL_CHINESE("zh-Hant", "繁中");

    companion object {
        fun fromLocale(locale: Locale): UiLanguage = when {
            locale.language != "zh" -> ENGLISH
            locale.script.equals("Hant", ignoreCase = true) -> TRADITIONAL_CHINESE
            locale.country.uppercase() in setOf("HK", "MO", "TW") -> TRADITIONAL_CHINESE
            else -> SIMPLIFIED_CHINESE
        }
    }
}

object UiLanguagePreferences {
    private const val PREFERENCES = "open_groove_ui_language"
    private const val KEY_LANGUAGE = "language_tag"

    fun current(context: Context): UiLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val appLocale = context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.takeIf { !it.isEmpty }
                ?.get(0)
            appLocale?.let { return UiLanguage.fromLocale(it) }
        }
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return UiLanguage.entries.firstOrNull { it.languageTag == stored }
            ?: UiLanguage.fromLocale(Locale.getDefault())
    }

    fun apply(activity: Activity, language: UiLanguage) {
        activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.languageTag)
            .apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language.languageTag)
        } else {
            activity.recreate()
        }
    }

    fun localizedContext(context: Context): Context {
        val locale = Locale.forLanguageTag(current(context).languageTag)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}

@Composable
fun UiLanguageSelector(selected: UiLanguage, onSelected: (UiLanguage) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UiLanguage.entries.forEach { language ->
            FilterChip(
                selected = language == selected,
                onClick = { onSelected(language) },
                label = { MaterialText(language.compactLabel, fontSize = 12.sp) },
            )
        }
    }
}

@Composable
fun LocalizedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
) {
    val locale = LocalConfiguration.current.locales.get(0)
    val language = UiLanguage.fromLocale(locale)
    MaterialText(
        text = localizeUiText(text, language),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
        overflow = overflow,
        maxLines = maxLines,
    )
}

internal fun localizeUiText(text: String, language: UiLanguage): String {
    if (language == UiLanguage.ENGLISH || text.isBlank()) return text
    val dictionary = if (language == UiLanguage.TRADITIONAL_CHINESE) TRADITIONAL else SIMPLIFIED
    dictionary[text]?.let { return it }

    Regex("^(Saved|Recent|Subscriptions|Unplayed) (\\d+)$").matchEntire(text)?.let { match ->
        val label = dictionary[match.groupValues[1]] ?: match.groupValues[1]
        return "$label ${match.groupValues[2]}"
    }
    Regex("^(\\d+) (track|tracks)$").matchEntire(text)?.let { return "${it.groupValues[1]} 首歌曲" }
    Regex("^(\\d+) in queue$").matchEntire(text)?.let {
        return if (language == UiLanguage.TRADITIONAL_CHINESE) {
            "佇列中 ${it.groupValues[1]} 首"
        } else {
            "队列中 ${it.groupValues[1]} 首"
        }
    }
    Regex("^(\\d+) min$").matchEntire(text)?.let {
        return if (language == UiLanguage.TRADITIONAL_CHINESE) {
            "${it.groupValues[1]} 分鐘"
        } else {
            "${it.groupValues[1]} 分钟"
        }
    }
    if (text.startsWith("Results for “")) return text.replaceFirst("Results for", dictionary.getValue("Results for"))
    if (text.startsWith("Queue “")) return text.replaceFirst("Queue", dictionary.getValue("Queue action"))
    if (text.startsWith("Play “")) return text.replaceFirst("Play", dictionary.getValue("Play action"))
    if (text.startsWith("Add “")) return text.replaceFirst("Add", dictionary.getValue("Add action"))
    if (text.startsWith("Resume at ")) return text.replaceFirst("Resume at", dictionary.getValue("Resume at"))
    if (text.startsWith("Pauses in ")) return text.replaceFirst("Pauses in", dictionary.getValue("Pauses in"))
    Regex("^(\\d+)(m|h|d) ago$").matchEntire(text)?.let {
        val unit = when (it.groupValues[2]) {
            "m" -> if (language == UiLanguage.TRADITIONAL_CHINESE) "分鐘" else "分钟"
            "h" -> if (language == UiLanguage.TRADITIONAL_CHINESE) "小時" else "小时"
            else -> "天"
        }
        return "${it.groupValues[1]} $unit\u524d"
    }
    if (text.contains(" • ")) return text.split(" • ").joinToString(" • ") { dictionary[it] ?: it }
    return text
}

private val SIMPLIFIED = mapOf(
    "Settings" to "设置",
    "App interface" to "应用界面",
    "Done" to "完成",
    "Discover" to "发现",
    "Radio" to "电台",
    "Podcasts" to "播客",
    "Library" to "音乐库",
    "Now playing" to "正在播放",
    "Up next" to "接下来播放",
    "Clear queue" to "清空队列",
    "Search official platforms" to "搜索官方平台",
    "Fresh finds" to "最新发现",
    "Licensed results" to "授权结果",
    "Music with a clear source." to "来源清晰的音乐。",
    "Wikimedia Commons + Jamendo • license shown per track" to "Wikimedia Commons + Jamendo • 每首歌曲均显示许可",
    "Song, artist, mood…" to "歌曲、歌手、心情…",
    "All" to "全部",
    "English" to "英语",
    "国语 / 中文" to "国语 / 中文",
    "粤语 / Cantonese" to "粤语",
    "Your playlists" to "你的播放列表",
    "Saved only on this device" to "仅保存在此设备",
    "Start your first playlist" to "创建你的第一个播放列表",
    "Create one, then add any licensed track you discover." to "创建列表，然后加入发现的授权歌曲。",
    "Create playlist" to "创建播放列表",
    "Play all" to "全部播放",
    "No tracks yet" to "暂无歌曲",
    "License" to "许可",
    "Source" to "来源",
    "Create a playlist first." to "请先创建播放列表。",
    "New playlist" to "新建播放列表",
    "Cancel" to "取消",
    "Play next" to "下一首播放",
    "Play now" to "立即播放",
    "Add to end" to "添加到队尾",
    "Playlist name" to "播放列表名称",
    "Create" to "创建",
    "Add action" to "添加",
    "Queue action" to "加入队列",
    "Play action" to "播放",
    "YouTube Watch" to "YouTube 观看",
    "Song, artist, channel…" to "歌曲、歌手、频道…",
    "YouTube API key required" to "需要 YouTube API 密钥",
    "Search in YouTube" to "在 YouTube 中搜索",
    "Saved YouTube videos" to "已保存的 YouTube 视频",
    "YouTube results" to "YouTube 结果",
    "Embeddable music videos • clearly marked YouTube source" to "可嵌入音乐视频 • 清楚标注 YouTube 来源",
    "YouTube API use" to "YouTube API 使用说明",
    "Watch signed in" to "登录后观看",
    "Made for kids" to "适合儿童",
    "Playback is provided by YouTube's official embedded player. Ads, availability, age checks, regional restrictions, and account behavior remain under YouTube's control." to
        "播放由 YouTube 官方嵌入式播放器提供。广告、可用性、年龄验证、地区限制及账号行为均由 YouTube 管理。",
    "If YouTube requests sign-in or bot verification, use the signed-in browser tab below. Its Back button returns here; Google sign-in cannot be completed inside an embedded WebView." to
        "如果 YouTube 要求登录或进行机器人验证，请使用下方已登录的浏览器标签页。其返回按钮会回到这里；Google 登录无法在嵌入式 WebView 内完成。",
    "LIVE" to "直播",
    "Publisher feeds, played directly" to "直接播放发布者订阅源",
    "Find a show" to "查找节目",
    "Your subscriptions" to "你的订阅",
    "Your podcast inbox" to "你的播客收件箱",
    "Search by show, host, or topic" to "按节目、主持人或主题搜索",
    "No subscriptions yet" to "尚未订阅任何节目",
    "You're all caught up" to "你已收听完毕",
    "No matching unplayed episodes" to "没有匹配的未播放单集",
    "Try English, 中文, or 廣東話, or add a publisher RSS URL." to "可尝试英语、中文或粤语，也可添加发布者 RSS 地址。",
    "Open a show and tap Subscribe to keep it here." to "打开节目并点击订阅，即可保存在这里。",
    "New episodes from subscribed feeds will appear here." to "已订阅节目的新单集会显示在这里。",
    "Search by episode, show, host, or description." to "按单集、节目、主持人或简介搜索。",
    "Add RSS feed" to "添加 RSS 订阅源",
    "Search results" to "搜索结果",
    "Subscriptions" to "订阅",
    "Unplayed" to "未播放",
    "Back to podcasts" to "返回播客",
    "Subscribed" to "已订阅",
    "Subscribe" to "订阅",
    "Publisher website" to "发布者网站",
    "Episodes" to "单集",
    "Streamed from the publisher • not downloaded" to "从发布者流式播放 • 不下载",
    "Subscribed feeds refresh about every 12 hours when online" to "联网时约每 12 小时刷新订阅源",
    "Podcast, host, or topic…" to "播客、主持人或主题…",
    "Episode page" to "单集页面",
    "Played" to "已播放",
    "Resume at" to "从此处继续",
    "Playback speed" to "播放速度",
    "Sleep timer" to "睡眠定时器",
    "Off" to "关闭",
    "Queue" to "队列",
    "Pauses in" to "将在以下时间后暂停",
    "Add publisher RSS feed" to "添加发布者 RSS 订阅源",
    "Paste the public RSS or Atom feed URL supplied by the podcast publisher." to "粘贴播客发布者提供的公开 RSS 或 Atom 订阅地址。",
    "Add" to "添加",
    "INTERNET DIRECTORY" to "互联网目录",
    "Internet Radio" to "网络电台",
    "Stations from around the world" to "收听世界各地的电台",
    "Saved" to "已保存",
    "Recent" to "最近播放",
    "Chinese radio" to "中文电台",
    "中国大陆" to "中国大陆",
    "香港粤语" to "香港粤语",
    "台湾省" to "台湾省",
    "全球中文" to "全球中文",
    "中国大陆、香港粤语、台湾省及全球中文电台" to "中国大陆、香港粤语、台湾省及全球中文电台",
    "Browse" to "浏览",
    "Country or region" to "国家或地区",
    "Language" to "语言",
    "Genre" to "类型",
    "Category" to "类别",
    "Clear filter" to "清除筛选",
    "Internet streams" to "互联网音频流",
    "availability can change" to "可用性可能变化",
    "Saved locally" to "本地保存",
    "playback still needs internet" to "播放仍需联网",
    "Saved stations" to "已保存的电台",
    "Recently played" to "最近播放",
    "No saved stations yet" to "尚未保存电台",
    "No recently played stations" to "暂无最近播放电台",
    "Tap the heart beside any station to keep it here." to "点击电台旁的爱心即可保存。",
    "Stations you play will appear here." to "播放过的电台会显示在这里。",
    "Clear recent stations" to "清除最近播放",
    "Loading more…" to "正在加载更多…",
    "Load more stations" to "加载更多电台",
    "Search station name…" to "搜索电台名称…",
    "Worldwide" to "全球",
    "All countries" to "所有国家和地区",
    "All languages" to "所有语言",
    "Back to stations" to "返回电台列表",
    "Retry stream" to "重试音频流",
    "Save station" to "保存电台",
    "Station site" to "电台网站",
    "Choose a country" to "选择国家或地区",
    "Country name or code" to "国家、地区名称或代码",
    "Close" to "关闭",
    "Popular stations" to "热门电台",
    "Results for" to "搜索结果：",
    "Online when checked" to "目录检查时在线",
    "Offline when checked" to "目录检查时离线",
    "STREAM" to "音频流",
    "CONNECTING" to "正在连接",
    "STREAMING" to "正在播放",
    "READY" to "准备就绪",
    "Pop" to "流行",
    "Rock" to "摇滚",
    "Jazz" to "爵士",
    "Classical" to "古典",
    "Electronic" to "电子",
    "Hip Hop" to "嘻哈",
    "Country" to "乡村",
    "Blues" to "蓝调",
    "Reggae" to "雷鬼",
    "News" to "新闻",
    "Talk" to "谈话",
    "Sports" to "体育",
    "Culture" to "文化",
    "Education" to "教育",
    "Kids" to "儿童",
    "Religious" to "宗教",
    "Community" to "社区",
    "Official search and visible in-app playback" to "官方搜索及应用内可见播放",
    "YouTube video and its official controls stay visible while playing. OpenGroove does not extract audio, download media, hide ads, or play YouTube in the background." to
        "播放时会始终显示 YouTube 视频及其官方控件。OpenGroove 不提取音频、不下载媒体、不隐藏广告，也不会在后台播放 YouTube。",
    "Add your own Android-restricted YouTube Data API key to local.properties. The official external search remains available meanwhile." to
        "请在 local.properties 中添加你自己的 Android 限制型 YouTube Data API 密钥。在此之前仍可使用官方外部搜索。",
    "References only • metadata refreshed within 30 days" to "仅保存引用 • 元数据在 30 天内刷新",
    "YouTube Terms" to "YouTube 条款",
    "Google Privacy" to "Google 隐私政策",
    "OpenGroove Privacy" to "OpenGroove 隐私政策",
    "Wikimedia Commons is active. Add your own Jamendo client ID to expand the open-music catalog." to
        "Wikimedia Commons 已启用。添加你自己的 Jamendo 客户端 ID 可扩展开放音乐目录。",
    "Search failed. Check your connection." to "搜索失败，请检查网络连接。",
    "No licensed tracks found. Try another search." to "没有找到授权歌曲，请尝试其他搜索词。",
    "Add a restricted YouTube Data API key to enable in-app search." to "请添加受限的 YouTube Data API 密钥以启用应用内搜索。",
    "Type a song, artist, or channel before searching YouTube." to "请先输入歌曲、歌手或频道，再搜索 YouTube。",
    "No embeddable YouTube videos matched this search." to "没有符合此次搜索的可嵌入 YouTube 视频。",
    "YouTube search failed. Check the key, quota, and connection." to "YouTube 搜索失败，请检查密钥、配额和网络连接。",
    "No podcasts found. Try another title, host, or language." to "没有找到播客，请尝试其他标题、主持人或语言。",
    "Podcast search failed. Check your connection." to "播客搜索失败，请检查网络连接。",
    "This publisher feed could not be loaded." to "无法加载此发布者订阅源。",
    "No working stations found. Try another filter." to "没有找到可用电台，请尝试其他筛选条件。",
    "Could not load more stations. Try again." to "无法加载更多电台，请重试。",
    "Radio search failed. Check your connection." to "电台搜索失败，请检查网络连接。",
    "The playback service could not start. Reopen OpenGroove and try again." to "播放服务无法启动，请重新打开 OpenGroove 后再试。",
    "The player is still starting. Try again in a moment." to "播放器仍在启动，请稍后再试。",
    "This track is unavailable right now." to "此歌曲暂时无法播放。",
    "This track cannot be played on this device." to "此歌曲无法在该设备上播放。",
    "This station uses a stream format that is not available right now. Try another station." to "该电台使用当前不支持的音频流格式，请尝试其他电台。",
    "This publisher audio stream cannot be played on this device." to "该发布者的音频流无法在此设备上播放。",
    "The sleep timer is unavailable in this playback session." to "此播放会话无法使用睡眠定时器。",
    "The sleep timer could not be changed." to "无法更改睡眠定时器。",
)

private val TRADITIONAL = SIMPLIFIED + mapOf(
    "Settings" to "設定",
    "App interface" to "App 介面",
    "Done" to "完成",
    "Discover" to "探索",
    "Radio" to "電台",
    "Podcasts" to "Podcast",
    "Library" to "音樂庫",
    "Now playing" to "正在播放",
    "Up next" to "接著播放",
    "Clear queue" to "清除佇列",
    "Fresh finds" to "最新探索",
    "Licensed results" to "授權結果",
    "Music with a clear source." to "來源清楚的音樂。",
    "English" to "英文",
    "国语 / 中文" to "國語 / 中文",
    "粤语 / Cantonese" to "粵語",
    "Your playlists" to "你的播放清單",
    "Saved only on this device" to "只儲存在此裝置",
    "Create playlist" to "建立播放清單",
    "Add action" to "加入",
    "Play all" to "全部播放",
    "No tracks yet" to "尚無歌曲",
    "License" to "授權",
    "Source" to "來源",
    "New playlist" to "新增播放清單",
    "Play next" to "下一首播放",
    "Add to end" to "加入佇列末端",
    "Playlist name" to "播放清單名稱",
    "Queue action" to "加入佇列",
    "Watch signed in" to "登入後觀看",
    "Made for kids" to "適合兒童",
    "Playback is provided by YouTube's official embedded player. Ads, availability, age checks, regional restrictions, and account behavior remain under YouTube's control." to
        "播放由 YouTube 官方嵌入式播放器提供。廣告、可用性、年齡驗證、地區限制及帳號行為均由 YouTube 管理。",
    "If YouTube requests sign-in or bot verification, use the signed-in browser tab below. Its Back button returns here; Google sign-in cannot be completed inside an embedded WebView." to
        "如果 YouTube 要求登入或進行機器人驗證，請使用下方已登入的瀏覽器分頁。其返回按鈕會回到這裡；Google 登入無法在嵌入式 WebView 內完成。",
    "YouTube results" to "YouTube 結果",
    "Saved YouTube videos" to "已儲存的 YouTube 影片",
    "Publisher feeds, played directly" to "直接播放發佈者訂閱來源",
    "Find a show" to "尋找節目",
    "Your subscriptions" to "你的訂閱",
    "Your podcast inbox" to "你的 Podcast 收件匣",
    "Search by show, host, or topic" to "依節目、主持人或主題搜尋",
    "No subscriptions yet" to "尚未訂閱任何節目",
    "You're all caught up" to "你已全部收聽完畢",
    "No matching unplayed episodes" to "沒有符合的未播放單集",
    "Add RSS feed" to "加入 RSS 訂閱來源",
    "Search results" to "搜尋結果",
    "Subscriptions" to "訂閱",
    "Unplayed" to "未播放",
    "Back to podcasts" to "返回 Podcast",
    "Subscribed" to "已訂閱",
    "Publisher website" to "發佈者網站",
    "Episodes" to "單集",
    "Played" to "已播放",
    "Resume at" to "從此處繼續",
    "Playback speed" to "播放速度",
    "Sleep timer" to "睡眠計時器",
    "Queue" to "佇列",
    "Add" to "加入",
    "Internet Radio" to "網絡電台",
    "Stations from around the world" to "收聽世界各地的電台",
    "Saved" to "已儲存",
    "Recent" to "最近播放",
    "Chinese radio" to "中文電台",
    "中国大陆" to "中國大陸",
    "香港粤语" to "香港粵語",
    "台湾省" to "台湾省",
    "中国大陆、香港粤语、台湾省及全球中文电台" to "中國大陸、香港粵語、台湾省及全球中文電台",
    "Browse" to "瀏覽",
    "Country or region" to "國家或地區",
    "Language" to "語言",
    "Genre" to "類型",
    "Category" to "分類",
    "Clear filter" to "清除篩選",
    "Saved stations" to "已儲存的電台",
    "Recently played" to "最近播放",
    "No saved stations yet" to "尚未儲存電台",
    "Clear recent stations" to "清除最近播放",
    "Load more stations" to "載入更多電台",
    "Search station name…" to "搜尋電台名稱…",
    "Worldwide" to "全球",
    "All countries" to "所有國家和地區",
    "All languages" to "所有語言",
    "Back to stations" to "返回電台列表",
    "Save station" to "儲存電台",
    "Station site" to "電台網站",
    "Choose a country" to "選擇國家或地區",
    "Close" to "關閉",
    "Popular stations" to "熱門電台",
    "Results for" to "搜尋結果：",
    "Online when checked" to "目錄檢查時在線",
    "Offline when checked" to "目錄檢查時離線",
    "STREAM" to "音訊串流",
    "CONNECTING" to "正在連線",
    "STREAMING" to "正在播放",
    "READY" to "準備就緒",
    "Official search and visible in-app playback" to "官方搜尋及 App 內可見播放",
    "References only • metadata refreshed within 30 days" to "僅儲存參照 • 中繼資料在 30 天內重新整理",
    "Search failed. Check your connection." to "搜尋失敗，請檢查網絡連線。",
    "YouTube search failed. Check the key, quota, and connection." to "YouTube 搜尋失敗，請檢查金鑰、配額及網絡連線。",
    "Podcast search failed. Check your connection." to "Podcast 搜尋失敗，請檢查網絡連線。",
    "No working stations found. Try another filter." to "沒有找到可用電台，請嘗試其他篩選條件。",
    "Could not load more stations. Try again." to "無法載入更多電台，請重試。",
    "Radio search failed. Check your connection." to "電台搜尋失敗，請檢查網絡連線。",
    "The player is still starting. Try again in a moment." to "播放器仍在啟動，請稍後再試。",
    "This track is unavailable right now." to "此歌曲暫時無法播放。",
    "This track cannot be played on this device." to "此歌曲無法在此裝置上播放。",
    "This station uses a stream format that is not available right now. Try another station." to "此電台使用目前不支援的音訊串流格式，請嘗試其他電台。",
    "Electronic" to "電子",
    "Hip Hop" to "嘻哈",
    "Country" to "鄉村",
    "Blues" to "藍調",
    "News" to "新聞",
    "Sports" to "體育",
    "Education" to "教育",
    "Community" to "社群",
)
