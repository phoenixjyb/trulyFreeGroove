package com.trulyfreemusic.opengroove.youtube

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.trulyfreemusic.opengroove.BuildConfig
import com.trulyfreemusic.opengroove.data.SearchLanguage

@Composable
fun YouTubeBrowseScreen(
    state: YouTubeUiState,
    savedVideos: List<YouTubeVideo>,
    configured: Boolean,
    onQueryChange: (String) -> Unit,
    onLanguageChange: (SearchLanguage) -> Unit,
    onSearch: () -> Unit,
    onWatch: (YouTubeVideo) -> Unit,
    onToggleSaved: (YouTubeVideo) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val savedIds = savedVideos.mapTo(mutableSetOf(), YouTubeVideo::videoId)
    val effectiveQuery = listOf(state.query.ifBlank { "music" }, state.language.searchHint)
        .filter(String::isNotBlank)
        .joinToString(" ")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SmartDisplay, contentDescription = null, tint = YouTubeRed, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("YouTube Watch", fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Official search and visible in-app playback",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                placeholder = { Text("Song, artist, channel…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    FilledIconButton(onClick = onSearch, enabled = configured && !state.isSearching) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search YouTube")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (configured) onSearch() }),
            )
            Spacer(Modifier.height(10.dp))
            YouTubeLanguageFilters(state.language, onLanguageChange)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "YouTube video and its official controls stay visible while playing. " +
                            "OpenGroove does not extract audio, download media, hide ads, or play YouTube in the background.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!configured) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("YouTube API key required", fontWeight = FontWeight.Bold)
                        Text(
                            "Add your own Android-restricted YouTube Data API key to local.properties. " +
                                "The official external search remains available meanwhile.",
                            fontSize = 13.sp,
                        )
                        OutlinedButton(
                            onClick = {
                                onOpenUrl("https://www.youtube.com/results?search_query=${Uri.encode(effectiveQuery)}")
                            },
                        ) {
                            Text("Search in YouTube")
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        state.error?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        if (savedVideos.isNotEmpty()) {
            item {
                Text("Saved YouTube videos", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    "References only • metadata refreshed within 30 days",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            items(savedVideos, key = { "saved:${it.videoId}" }) { video ->
                YouTubeVideoCard(video, saved = true, onWatch, onToggleSaved, onOpenUrl)
            }
        }

        if (state.results.isNotEmpty() || state.isSearching) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("YouTube results", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Embeddable music videos • clearly marked YouTube source", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (state.isSearching) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
        items(state.results, key = { "result:${it.videoId}" }) { video ->
            YouTubeVideoCard(video, savedIds.contains(video.videoId), onWatch, onToggleSaved, onOpenUrl)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("YouTube API use", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "YouTube Terms",
                        modifier = Modifier.clickable { onOpenUrl("https://www.youtube.com/t/terms") }.padding(vertical = 5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                    )
                    Text(
                        "Google Privacy",
                        modifier = Modifier.clickable { onOpenUrl("https://policies.google.com/privacy") }.padding(vertical = 5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    "OpenGroove Privacy",
                    modifier = Modifier.clickable {
                        onOpenUrl("https://github.com/phoenixjyb/trulyFreeGroove/blob/main/PRIVACY.md")
                    }.padding(vertical = 5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun YouTubeWatchScreen(
    video: YouTubeVideo,
    saved: Boolean,
    onBack: () -> Unit,
    onToggleSaved: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to YouTube results")
                }
                Column(Modifier.weight(1f)) {
                    Text("YouTube", color = YouTubeRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(video.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onToggleSaved) {
                    Icon(
                        if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (saved) "Remove saved video" else "Save video",
                    )
                }
                IconButton(onClick = { video.watchUrl()?.let(onOpenExternal) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open in YouTube")
                }
            }
            OfficialYouTubePlayer(
                videoId = video.videoId,
                onOpenExternal = onOpenExternal,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .heightIn(min = 200.dp)
                    .background(Color.Black),
            )
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(video.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(video.channelTitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (video.isLive) YouTubeBadge("LIVE")
                    if (video.madeForKids == true) YouTubeBadge("Made for kids")
                    val duration = formatYouTubeDuration(video.durationSeconds)
                    if (duration.isNotBlank()) YouTubeBadge(duration)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Playback is provided by YouTube's official embedded player. Ads, availability, age checks, " +
                        "regional restrictions, and account behavior remain under YouTube's control.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                Button(onClick = { video.watchUrl()?.let(onOpenExternal) }) {
                    Text("Open in YouTube")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun YouTubeVideoCard(
    video: YouTubeVideo,
    saved: Boolean,
    onWatch: (YouTubeVideo) -> Unit,
    onToggleSaved: (YouTubeVideo) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onWatch(video) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = "${video.title} YouTube thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(124.dp).height(72.dp).clip(RoundedCornerShape(12.dp)),
                )
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(50),
                    color = YouTubeRed,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.padding(5.dp).size(18.dp))
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    video.channelTitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("YouTube", color = YouTubeRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (video.isLive) {
                        Spacer(Modifier.width(7.dp))
                        Text("LIVE", color = YouTubeRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    val duration = formatYouTubeDuration(video.durationSeconds)
                    if (duration.isNotBlank()) {
                        Spacer(Modifier.width(7.dp))
                        Text(duration, fontSize = 10.sp)
                    }
                }
            }
            IconButton(onClick = { onToggleSaved(video) }) {
                Icon(
                    if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (saved) "Remove saved video" else "Save video",
                )
            }
            IconButton(onClick = { video.watchUrl()?.let(onOpenUrl) }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open in YouTube")
            }
        }
    }
}

@Composable
private fun YouTubeLanguageFilters(selected: SearchLanguage, onSelected: (SearchLanguage) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        SearchLanguage.entries.forEach { language ->
            FilterChip(
                selected = selected == language,
                onClick = { onSelected(language) },
                label = { Text(language.label, fontSize = 11.sp) },
            )
        }
    }
}

@Composable
private fun YouTubeBadge(label: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OfficialYouTubePlayer(
    videoId: String,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    check(YouTubePlaybackPolicy.isCanonicalVideoId(videoId)) { "Invalid YouTube video ID." }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context.findActivity()
    val baseOrigin = "https://${BuildConfig.APPLICATION_ID.lowercase()}"
    val chromeClient = remember(videoId, activity) { OpenGrooveChromeClient(activity, onOpenExternal) }
    val webView = remember(videoId) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.userAgentString = "${settings.userAgentString} OpenGroove/${BuildConfig.VERSION_NAME}"
            webChromeClient = chromeClient
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (url == "about:blank" || url.startsWith(baseOrigin)) return false
                    if (!request.isForMainFrame && request.url.isYouTubeEmbedUrl()) return false
                    if (request.url.scheme == "https" || request.url.scheme == "http") onOpenExternal(url)
                    return true
                }
            }
            loadDataWithBaseURL(
                "$baseOrigin/",
                youtubePlayerHtml(videoId, baseOrigin),
                "text/html",
                Charsets.UTF_8.name(),
                null,
            )
        }
    }

    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    webView.evaluateJavascript("pauseOpenGroovePlayer()", null)
                    webView.onPause()
                }
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.evaluateJavascript("pauseOpenGroovePlayer()", null)
            chromeClient.dispose()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    AndroidView(factory = { webView }, modifier = modifier)
}

private class OpenGrooveChromeClient(
    private val activity: Activity?,
    private val onOpenExternal: (String) -> Unit,
) : WebChromeClient() {
    private var customView: View? = null
    private var callback: CustomViewCallback? = null
    private val popupWebViews = mutableSetOf<WebView>()

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val decor = activity?.window?.decorView as? ViewGroup
        if (decor == null || customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        this.callback = callback
        view.setBackgroundColor(AndroidColor.BLACK)
        decor.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        customView = null
        callback?.onCustomViewHidden()
        callback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
    ): Boolean {
        val context = activity ?: return false
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        if (!isUserGesture) return false
        val popup = WebView(context)
        popup.settings.javaScriptEnabled = true
        popup.webViewClient = object : WebViewClient() {
            private var opened = false

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                if (!opened && (request.url.scheme == "https" || request.url.scheme == "http")) {
                    opened = true
                    onOpenExternal(request.url.toString())
                }
                closePopup(popup)
                return true
            }
        }
        popupWebViews += popup
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView?) {
        window?.let(::closePopup)
    }

    fun dispose() {
        onHideCustomView()
        popupWebViews.toList().forEach(::closePopup)
    }

    private fun closePopup(webView: WebView) {
        popupWebViews -= webView
        webView.stopLoading()
        webView.destroy()
    }
}

private fun youtubePlayerHtml(videoId: String, baseOrigin: String): String = """
    <!doctype html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>
          html, body, #player { width: 100%; height: 100%; margin: 0; padding: 0; background: #000; overflow: hidden; }
        </style>
      </head>
      <body>
        <div id="player"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
          var player;
          function onYouTubeIframeAPIReady() {
            player = new YT.Player('player', {
              width: '100%',
              height: '100%',
              videoId: '$videoId',
              playerVars: {
                playsinline: 1,
                autoplay: 0,
                controls: 1,
                fs: 1,
                origin: '$baseOrigin'
              }
            });
          }
          function pauseOpenGroovePlayer() {
            if (player && typeof player.pauseVideo === 'function') player.pauseVideo();
          }
        </script>
      </body>
    </html>
""".trimIndent()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Uri.isYouTubeEmbedUrl(): Boolean =
    host?.lowercase() in setOf("www.youtube.com", "youtube.com", "www.youtube-nocookie.com") &&
        path?.startsWith("/embed/") == true

private fun formatYouTubeDuration(seconds: Int): String = when {
    seconds <= 0 -> ""
    seconds >= 3_600 -> "%d:%02d:%02d".format(seconds / 3_600, seconds / 60 % 60, seconds % 60)
    else -> "%d:%02d".format(seconds / 60, seconds % 60)
}

private val YouTubeRed = Color(0xFFFF0033)
