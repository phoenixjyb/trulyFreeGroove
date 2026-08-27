package com.trulyfreemusic.opengroove

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.trulyfreemusic.opengroove.model.Track
import com.trulyfreemusic.opengroove.data.SearchLanguage
import com.trulyfreemusic.opengroove.radio.RadioBrowseScreen
import com.trulyfreemusic.opengroove.radio.RadioMiniPlayer
import com.trulyfreemusic.opengroove.radio.RadioPlayerScreen
import com.trulyfreemusic.opengroove.radio.RadioStation
import com.trulyfreemusic.opengroove.ui.MainViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OpenGrooveTheme { OpenGrooveApp() } }
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFF3C4),
    onPrimary = Color(0xFF241F10),
    secondary = Color(0xFFBCAEFF),
    onSecondary = Color(0xFF2D2360),
    tertiary = Color(0xFF8FFFC1),
    onTertiary = Color(0xFF003823),
    background = Color(0xFF120F1C),
    onBackground = Color(0xFFF5F1FF),
    surface = Color(0xFF201A2E),
    onSurface = Color(0xFFF5F1FF),
    surfaceVariant = Color(0xFF2A2238),
    onSurfaceVariant = Color(0xFFD2C8DA),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outlineVariant = Color(0xFF4B4451),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5E46C8),
    onPrimary = Color.White,
    secondary = Color(0xFF00658A),
    onSecondary = Color.White,
    tertiary = Color(0xFF006C4C),
    onTertiary = Color.White,
    background = Color(0xFFFFF9FF),
    onBackground = Color(0xFF1D1A20),
    surface = Color(0xFFFFF9FF),
    onSurface = Color(0xFF1D1A20),
    surfaceVariant = Color(0xFFECE6F0),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outlineVariant = Color(0xFFCAC4D0),
)

@Composable
private fun OpenGrooveTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? MainActivity ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}

private enum class AppSection { DISCOVER, RADIO, LIBRARY }

@Composable
private fun OpenGrooveApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val radioState by viewModel.radioState.collectAsStateWithLifecycle()
    val savedStations by viewModel.savedStations.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(AppSection.DISCOVER) }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var currentStation by remember { mutableStateOf<RadioStation?>(null) }
    var radioQueue by remember { mutableStateOf(emptyList<RadioStation>()) }
    var showRadioPlayer by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = "This stream is unavailable right now. Try the next station."
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playerError = null
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(isPlaying, currentTrack) {
        while (currentTrack != null) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0L) durationMs = player.duration
            delay(if (isPlaying) 500 else 1_000)
        }
    }

    fun play(track: Track) {
        if (!track.isDirectPlaybackAllowed()) {
            context.openUrl(track.sourceUrl)
            return
        }
        currentTrack = track
        currentStation = null
        showRadioPlayer = false
        playerError = null
        positionMs = 0L
        durationMs = (track.durationSeconds * 1_000L).coerceAtLeast(1L)
        try {
            player.setMediaItem(MediaItem.fromUri(track.streamUrl))
            player.prepare()
            player.play()
        } catch (_: RuntimeException) {
            player.stop()
            player.clearMediaItems()
            playerError = "This track cannot be played on this device."
        }
    }

    fun playStation(station: RadioStation) {
        if (!station.isPlayable()) return
        currentTrack = null
        currentStation = station
        playerError = null
        radioQueue = when {
            radioState.stations.any { it.id == station.id } -> radioState.stations
            savedStations.any { it.id == station.id } -> savedStations
            else -> listOf(station)
        }
        showRadioPlayer = true
        try {
            player.setMediaItem(MediaItem.fromUri(station.streamUrl))
            player.prepare()
            player.play()
            viewModel.registerStationClick(station.id)
        } catch (_: RuntimeException) {
            player.stop()
            player.clearMediaItems()
            playerError = "This station uses a stream format that is not available right now. Try another station."
        }
    }

    fun switchStation(delta: Int) {
        val station = currentStation ?: return
        val queue = radioQueue.ifEmpty { listOf(station) }
        val currentIndex = queue.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
        val nextIndex = (currentIndex + delta + queue.size) % queue.size
        playStation(queue[nextIndex])
    }

    BackHandler(enabled = showRadioPlayer) {
        showRadioPlayer = false
    }

    Box(Modifier.fillMaxSize()) {
      Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            Column {
                currentTrack?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = player::seekTo,
                        onToggle = { if (player.isPlaying) player.pause() else player.play() },
                    )
                }
                currentStation?.let { station ->
                    RadioMiniPlayer(
                        station = station,
                        isPlaying = isPlaying,
                        onOpen = { showRadioPlayer = true },
                        onToggle = { if (player.isPlaying) player.pause() else player.play() },
                    )
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = section == AppSection.DISCOVER,
                        onClick = { section = AppSection.DISCOVER },
                        icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                        label = { Text("Discover") },
                    )
                    NavigationBarItem(
                        selected = section == AppSection.RADIO,
                        onClick = { section = AppSection.RADIO },
                        icon = { Icon(Icons.Rounded.Radio, contentDescription = null) },
                        label = { Text("Radio") },
                    )
                    NavigationBarItem(
                        selected = section == AppSection.LIBRARY,
                        onClick = { section = AppSection.LIBRARY },
                        icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                        label = { Text("Playlists") },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background,
                        ),
                        endY = 900f,
                    ),
                )
                .padding(innerPadding),
        ) {
            when (section) {
                AppSection.DISCOVER -> DiscoverScreen(
                    query = searchState.query,
                    language = searchState.language,
                    tracks = searchState.tracks,
                    isLoading = searchState.isLoading,
                    error = searchState.error,
                    jamendoConfigured = viewModel.jamendoConfigured,
                    onQueryChange = viewModel::setQuery,
                    onLanguageChange = viewModel::setLanguage,
                    onSearch = { viewModel.search() },
                    onPlay = ::play,
                    onAdd = { addTrack = it },
                    onOpen = context::openUrl,
                )
                AppSection.RADIO -> RadioBrowseScreen(
                    state = radioState,
                    savedStations = savedStations,
                    onQueryChange = viewModel::setRadioQuery,
                    onSearch = viewModel::searchRadio,
                    onCountry = viewModel::selectRadioCountry,
                    onTag = viewModel::selectRadioTag,
                    onClearFilters = viewModel::clearRadioFilters,
                    onToggleSaved = viewModel::toggleSavedStation,
                    onPlay = ::playStation,
                )
                AppSection.LIBRARY -> LibraryScreen(
                    playlists = playlists,
                    onCreate = { showCreatePlaylist = true },
                    onPlay = ::play,
                    onRemove = viewModel::removeFromPlaylist,
                    onOpen = context::openUrl,
                )
            }
        }
      }

      currentStation?.takeIf { showRadioPlayer }?.let { station ->
          RadioPlayerScreen(
              station = station,
              saved = savedStations.any { it.id == station.id },
              isPlaying = isPlaying,
              error = playerError,
              onBack = { showRadioPlayer = false },
              onToggle = { if (player.isPlaying) player.pause() else player.play() },
              onPrevious = { switchStation(-1) },
              onNext = { switchStation(1) },
              onToggleSaved = { viewModel.toggleSavedStation(station) },
              onHomepage = { context.openUrl(station.homepageUrl) },
          )
      }
    }

    addTrack?.let { track ->
        AddToPlaylistDialog(
            track = track,
            playlistNames = playlists.keys.toList(),
            onAdd = { name ->
                viewModel.addToPlaylist(name, track)
                addTrack = null
            },
            onCreate = {
                addTrack = null
                showCreatePlaylist = true
            },
            onDismiss = { addTrack = null },
        )
    }
    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onCreate = { name ->
                if (viewModel.createPlaylist(name)) showCreatePlaylist = false
            },
            onDismiss = { showCreatePlaylist = false },
        )
    }
}

@Composable
private fun DiscoverScreen(
    query: String,
    language: SearchLanguage,
    tracks: List<Track>,
    isLoading: Boolean,
    error: String?,
    jamendoConfigured: Boolean,
    onQueryChange: (String) -> Unit,
    onLanguageChange: (SearchLanguage) -> Unit,
    onSearch: () -> Unit,
    onPlay: (Track) -> Unit,
    onAdd: (Track) -> Unit,
    onOpen: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("OpenGroove", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(
                "Music with a clear source.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(20.dp))
            SearchField(query, onQueryChange, onSearch)
            Spacer(Modifier.height(10.dp))
            LanguageFilters(language, onLanguageChange)
        }
        item {
            Text("Search official platforms", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ProviderLinks(query, language)
        }
        if (!jamendoConfigured) {
            item { CatalogNotice() }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (query.isBlank()) "Fresh finds" else "Licensed results",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Wikimedia Commons + Jamendo • license shown per track", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
                if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        error?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        items(tracks, key = { it.id }) { track ->
            TrackCard(track, onPlay, onAdd, onOpen)
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        placeholder = { Text("Song, artist, mood…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            FilledIconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = "Search")
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

@Composable
private fun LanguageFilters(selected: SearchLanguage, onSelected: (SearchLanguage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SearchLanguage.entries.take(2).forEach { language ->
                FilterChip(
                    selected = selected == language,
                    onClick = { onSelected(language) },
                    label = { Text(language.label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SearchLanguage.entries.drop(2).forEach { language ->
                FilterChip(
                    selected = selected == language,
                    onClick = { onSelected(language) },
                    label = { Text(language.label) },
                )
            }
        }
    }
}

@Composable
private fun ProviderLinks(query: String, language: SearchLanguage) {
    val context = LocalContext.current
    val effectiveQuery = listOf(query.ifBlank { "music" }, language.searchHint)
        .filter(String::isNotBlank)
        .joinToString(" ")
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ProviderButton("YouTube Music") {
            context.openUrl("https://music.youtube.com/search?q=${Uri.encode(effectiveQuery)}")
        }
        ProviderButton("YouTube") {
            context.openUrl("https://www.youtube.com/results?search_query=${Uri.encode(effectiveQuery)}")
        }
        ProviderButton("Spotify") {
            context.openUrl("https://open.spotify.com/search/${Uri.encode(effectiveQuery)}")
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ProviderButton("QQ音乐") {
            context.openUrl("https://y.qq.com/n/ryqq/search?w=${Uri.encode(effectiveQuery)}&t=song")
        }
        ProviderButton("网易云音乐") {
            context.openUrl("https://music.163.com/#/search/m/?s=${Uri.encode(effectiveQuery)}&type=1")
        }
      }
    }
}

@Composable
private fun ProviderButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, maxLines = 1, fontSize = 11.sp)
        Spacer(Modifier.width(3.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, Modifier.size(13.dp))
    }
}

@Composable
private fun CatalogNotice() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text(
                "Wikimedia Commons is active. Add your own Jamendo client ID to expand the open-music catalog.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun TrackCard(
    track: Track,
    onPlay: (Track) -> Unit,
    onAdd: (Track) -> Unit,
    onOpen: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = "${track.title} artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        listOf(track.providerName, formatDuration(track.durationSeconds))
                            .filter(String::isNotBlank)
                            .joinToString(" • "),
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 11.sp,
                    )
                }
                Row {
                    Text(
                        "License",
                        modifier = Modifier.clickable { onOpen(track.licenseUrl) }.padding(vertical = 5.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Source",
                        modifier = Modifier.clickable { onOpen(track.sourceUrl) }.padding(vertical = 5.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = { onAdd(track) }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add to playlist")
            }
            FilledIconButton(onClick = { onPlay(track) }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    playlists: Map<String, List<Track>>,
    onCreate: () -> Unit,
    onPlay: (Track) -> Unit,
    onRemove: (String, String) -> Unit,
    onOpen: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your playlists", fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("Saved only on this device", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
                FilledIconButton(onClick = onCreate) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create playlist")
                }
            }
        }
        if (playlists.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(8.dp))
                        Text("Start your first playlist", fontWeight = FontWeight.Bold)
                        Text("Create one, then add any licensed track you discover.", fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onCreate) { Text("Create playlist") }
                    }
                }
            }
        }
        playlists.forEach { (name, tracks) ->
            item(key = "header:$name") {
                Column {
                    Text(name, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
            if (tracks.isEmpty()) {
                item(key = "empty:$name") {
                    Text("No tracks yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            items(tracks, key = { "$name:${it.id}" }) { track ->
                PlaylistTrackRow(
                    track = track,
                    onPlay = { onPlay(track) },
                    onRemove = { onRemove(name, track.id) },
                    onOpen = { onOpen(track.sourceUrl) },
                )
            }
            item(key = "divider:$name") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        }
    }
}

@Composable
private fun PlaylistTrackRow(track: Track, onPlay: () -> Unit, onRemove: () -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onPlay).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
        }
        IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open source") }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, contentDescription = "Remove") }
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 8.dp) {
        Column {
            Slider(
                value = positionMs.coerceAtMost(durationMs).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxWidth().height(20.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledIconButton(onClick = onToggle) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause")
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    track: Track,
    playlistNames: List<String>,
    onAdd: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add “${track.title}”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (playlistNames.isEmpty()) Text("Create a playlist first.")
                playlistNames.forEach { name ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onAdd(name) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    ) {
                        Text(name, Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCreate) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreatePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 50) name = it },
                label = { Text("Playlist name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun Context.openUrl(url: String) {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // No browser or provider app is installed; leave the current screen intact.
    }
}

private fun formatDuration(seconds: Int): String =
    if (seconds <= 0) "" else "%d:%02d".format(seconds / 60, seconds % 60)
