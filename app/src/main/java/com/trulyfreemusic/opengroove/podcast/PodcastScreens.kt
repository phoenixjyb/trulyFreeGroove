package com.trulyfreemusic.opengroove.podcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trulyfreemusic.opengroove.data.SearchLanguage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private enum class PodcastListMode { SEARCH, SUBSCRIPTIONS, UNPLAYED }

@Composable
fun PodcastBrowseScreen(
    state: PodcastUiState,
    subscriptions: List<PodcastShow>,
    unplayedEpisodes: List<PodcastEpisode>,
    onQueryChange: (String) -> Unit,
    onLibraryQueryChange: (String) -> Unit,
    onLanguageChange: (SearchLanguage) -> Unit,
    onSearch: () -> Unit,
    onAddFeed: (String) -> Unit,
    onSelectShow: (PodcastShow) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSubscribe: (PodcastShow) -> Unit,
    onPlay: (PodcastEpisode) -> Unit,
    onQueue: (PodcastEpisode) -> Unit,
    onTogglePlayed: (PodcastEpisode) -> Unit,
    onOpen: (String) -> Unit,
) {
    val selectedShow = state.selectedShow
    if (selectedShow != null) {
        PodcastShowScreen(
            show = selectedShow,
            episodes = state.episodes,
            subscribed = subscriptions.any { it.feedUrl == selectedShow.feedUrl },
            isLoading = state.isLoadingFeed,
            error = state.error,
            onBack = onBack,
            onRefresh = onRefresh,
            onToggleSubscribe = { onToggleSubscribe(selectedShow) },
            onPlay = onPlay,
            onQueue = onQueue,
            onTogglePlayed = onTogglePlayed,
            onOpen = onOpen,
        )
        return
    }

    var mode by remember { mutableStateOf(PodcastListMode.SEARCH) }
    var showFeedDialog by remember { mutableStateOf(false) }
    val filteredUnplayedEpisodes = unplayedEpisodes.filter { it.matchesLibraryQuery(state.libraryQuery) }
    val shows = when (mode) {
        PodcastListMode.SEARCH -> state.results
        PodcastListMode.SUBSCRIPTIONS -> subscriptions
        PodcastListMode.UNPLAYED -> emptyList()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Podcasts", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("Publisher feeds, played directly", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.Podcasts, contentDescription = null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { showFeedDialog = true }) {
                Icon(Icons.Rounded.RssFeed, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add RSS feed")
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = mode == PodcastListMode.SEARCH,
                    onClick = { mode = PodcastListMode.SEARCH },
                    label = { Text("Search results") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, Modifier.size(17.dp)) },
                )
                FilterChip(
                    selected = mode == PodcastListMode.SUBSCRIPTIONS,
                    onClick = { mode = PodcastListMode.SUBSCRIPTIONS },
                    label = { Text("Subscriptions ${subscriptions.size}") },
                    leadingIcon = { Icon(Icons.Rounded.Podcasts, contentDescription = null, Modifier.size(17.dp)) },
                )
                FilterChip(
                    selected = mode == PodcastListMode.UNPLAYED,
                    onClick = { mode = PodcastListMode.UNPLAYED },
                    label = { Text("Unplayed ${unplayedEpisodes.size}") },
                    leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, Modifier.size(17.dp)) },
                )
            }
        }
        if (mode == PodcastListMode.SEARCH) {
            item {
                PodcastSearchField(state.query, onQueryChange, onSearch)
                Spacer(Modifier.height(8.dp))
                PodcastLanguageFilters(state.language, onLanguageChange)
            }
        } else if (mode == PodcastListMode.UNPLAYED) {
            item {
                PodcastLibrarySearchField(state.libraryQuery, onLibraryQueryChange)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (mode) {
                        PodcastListMode.SEARCH -> "Find a show"
                        PodcastListMode.SUBSCRIPTIONS -> "Your subscriptions"
                        PodcastListMode.UNPLAYED -> "Your podcast inbox"
                    },
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (state.isSearching) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        state.error?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        val listIsEmpty = if (mode == PodcastListMode.UNPLAYED) filteredUnplayedEpisodes.isEmpty() else shows.isEmpty()
        if (listIsEmpty && !state.isSearching) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Podcasts, contentDescription = null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (mode) {
                                PodcastListMode.SEARCH -> "Search by show, host, or topic"
                                PodcastListMode.SUBSCRIPTIONS -> "No subscriptions yet"
                                PodcastListMode.UNPLAYED -> if (state.libraryQuery.isBlank()) "You're all caught up" else "No matching unplayed episodes"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when (mode) {
                                PodcastListMode.SEARCH -> "Try English, 中文, or 廣東話, or add a publisher RSS URL."
                                PodcastListMode.SUBSCRIPTIONS -> "Open a show and tap Subscribe to keep it here."
                                PodcastListMode.UNPLAYED -> if (state.libraryQuery.isBlank()) {
                                    "New episodes from subscribed feeds will appear here."
                                } else {
                                    "Search by episode, show, host, or description."
                                }
                            },
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        items(shows, key = PodcastShow::feedUrl) { show ->
            PodcastShowCard(
                show = show,
                subscribed = subscriptions.any { it.feedUrl == show.feedUrl },
                onClick = { onSelectShow(show) },
            )
        }
        if (mode == PodcastListMode.UNPLAYED) {
            items(filteredUnplayedEpisodes, key = PodcastEpisode::episodeId) { episode ->
                PodcastEpisodeCard(episode, onPlay, onQueue, onTogglePlayed, onOpen)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showFeedDialog) {
        AddPodcastFeedDialog(
            onAdd = {
                showFeedDialog = false
                onAddFeed(it)
            },
            onDismiss = { showFeedDialog = false },
        )
    }
}

@Composable
private fun PodcastShowScreen(
    show: PodcastShow,
    episodes: List<PodcastEpisode>,
    subscribed: Boolean,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onPlay: (PodcastEpisode) -> Unit,
    onQueue: (PodcastEpisode) -> Unit,
    onTogglePlayed: (PodcastEpisode) -> Unit,
    onOpen: (String) -> Unit,
) {
    var episodeQuery by remember(show.feedUrl) { mutableStateOf("") }
    val visibleEpisodes = episodes.filter { it.matchesLibraryQuery(episodeQuery) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to podcasts")
                }
                Spacer(Modifier.width(4.dp))
                Text("Back to podcasts", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh feed") }
            }
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = show.artworkUrl,
                    contentDescription = "${show.title} artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(112.dp).clip(RoundedCornerShape(22.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(show.title, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Text(show.author, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onToggleSubscribe) {
                        Icon(if (subscribed) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (subscribed) "Subscribed" else "Subscribe")
                    }
                }
            }
            if (show.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(show.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
            if (show.websiteUrl.isHttpUrl()) {
                TextButton(onClick = { onOpen(show.websiteUrl) }) {
                    Text("Publisher website")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, Modifier.size(16.dp))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Episodes", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            Text("Streamed from the publisher • not downloaded", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            Text("Subscribed feeds refresh about every 12 hours when online", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            PodcastLibrarySearchField(
                query = episodeQuery,
                onQueryChange = { episodeQuery = it },
                placeholder = "Search this show…",
            )
        }
        error?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        if (visibleEpisodes.isEmpty() && episodes.isNotEmpty()) {
            item {
                Text(
                    "No episodes match this search.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(visibleEpisodes, key = PodcastEpisode::episodeId) { episode ->
            PodcastEpisodeCard(episode, onPlay, onQueue, onTogglePlayed, onOpen)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PodcastSearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        placeholder = { Text("Podcast, host, or topic…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            FilledIconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Search podcasts") }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

@Composable
private fun PodcastLibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search unplayed episodes…",
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun PodcastLanguageFilters(selected: SearchLanguage, onSelected: (SearchLanguage) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SearchLanguage.entries.forEach { language ->
            FilterChip(
                selected = selected == language,
                onClick = { onSelected(language) },
                label = { Text(language.label) },
            )
        }
    }
}

@Composable
private fun PodcastShowCard(show: PodcastShow, subscribed: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = show.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(15.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(show.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(show.author, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(show.genre, show.country).filter(String::isNotBlank).joinToString(" • "), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            if (subscribed) Icon(Icons.Rounded.Check, contentDescription = "Subscribed", tint = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun PodcastEpisodeCard(
    episode: PodcastEpisode,
    onPlay: (PodcastEpisode) -> Unit,
    onQueue: (PodcastEpisode) -> Unit,
    onTogglePlayed: (PodcastEpisode) -> Unit,
    onOpen: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(episode.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(formatPodcastDate(episode.publishedAt), formatPodcastDurationLabel(episode.durationMs))
                            .filter(String::isNotBlank).joinToString(" • "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                FilledIconButton(onClick = { onPlay(episode) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play episode")
                }
                IconButton(onClick = { onQueue(episode) }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Add episode to queue")
                }
                IconButton(onClick = { onTogglePlayed(episode) }) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = if (episode.completed) "Mark episode unplayed" else "Mark episode played",
                        tint = if (episode.completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (episode.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(episode.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (episode.positionMs > 0 || episode.completed) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (episode.completed) "Played" else "Resume at ${formatPodcastDurationLabel(episode.positionMs)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (episode.websiteUrl.isHttpUrl()) {
                TextButton(onClick = { onOpen(episode.websiteUrl) }) { Text("Episode page") }
            }
        }
    }
}

@Composable
fun PodcastMiniPlayer(
    episode: PodcastEpisode,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    error: String?,
    onOpen: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 8.dp) {
        Column {
            Slider(
                value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxWidth().height(20.dp),
            )
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = episode.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(episode.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(episode.showTitle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledIconButton(onClick = onToggle) {
                    if (isBuffering) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause")
                }
            }
            error?.let { message ->
                Text(
                    message,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
fun PodcastPlayerScreen(
    episode: PodcastEpisode,
    queue: List<PodcastEpisode>,
    currentIndex: Int,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    sleepTimerEndAtMs: Long,
    error: String?,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeed: (Float) -> Unit,
    onSleepTimer: (Int?) -> Unit,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val remainingMs = (sleepTimerEndAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to podcasts")
                    }
                    Text("Now playing", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = episode.artworkUrl,
                        contentDescription = "${episode.showTitle} artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(28.dp)),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(episode.title, fontSize = 23.sp, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(episode.showTitle, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Slider(
                        value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatPodcastDurationLabel(positionMs), fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text(formatPodcastDurationLabel(durationMs), fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(onClick = onPrevious, enabled = currentIndex > 0) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous episode", Modifier.size(32.dp))
                        }
                        FilledIconButton(onClick = onToggle, modifier = Modifier.size(62.dp)) {
                            if (isBuffering) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                            else Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause", Modifier.size(32.dp))
                        }
                        IconButton(onClick = onNext, enabled = currentIndex in 0 until queue.lastIndex) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next episode", Modifier.size(32.dp))
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(7.dp))
                    Text("Playback speed", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    PodcastPlaybackSpeeds.forEach { speed ->
                        FilterChip(
                            selected = abs(playbackSpeed - speed) < 0.01f,
                            onClick = { onSpeed(speed) },
                            label = { Text("${speed}×") },
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(7.dp))
                    Column {
                        Text("Sleep timer", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (remainingMs > 0L) "Pauses in ${formatTimerRemaining(remainingMs)}" else "Off",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    FilterChip(selected = remainingMs == 0L, onClick = { onSleepTimer(null) }, label = { Text("Off") })
                    PodcastSleepTimerMinutes.forEach { minutes ->
                        val target = minutes * 60_000L
                        FilterChip(
                            selected = remainingMs > 0L && abs(remainingMs - target) < 70_000L,
                            onClick = { onSleepTimer(minutes) },
                            label = { Text("$minutes min") },
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(7.dp))
                    Text("Queue", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${queue.size}", color = MaterialTheme.colorScheme.secondary)
                }
            }
            itemsIndexed(queue, key = { _, queued -> queued.episodeId }) { index, queued ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onJumpTo(index) },
                    color = if (index == currentIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                            if (index == currentIndex) Icon(Icons.Rounded.PlayArrow, contentDescription = "Currently playing", Modifier.size(19.dp))
                            else Text("${index + 1}", fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(queued.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(queued.showTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(onClick = { onRemove(index) }, enabled = index != currentIndex) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove from queue")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun AddPodcastFeedDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    val valid = url.trim().isHttpUrl()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add publisher RSS feed") },
        text = {
            Column {
                Text("Paste the public RSS or Atom feed URL supplied by the podcast publisher.", fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("https://example.com/feed.xml") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { Button(onClick = { onAdd(url.trim()) }, enabled = valid) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatPodcastDate(timestamp: Long): String = if (timestamp <= 0L) "" else {
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
}

private fun formatPodcastDurationLabel(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSeconds = durationMs / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun formatTimerRemaining(durationMs: Long): String {
    val totalMinutes = (durationMs + 59_999L) / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
