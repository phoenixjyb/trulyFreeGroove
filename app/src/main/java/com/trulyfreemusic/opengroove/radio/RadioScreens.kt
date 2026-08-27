package com.trulyfreemusic.opengroove.radio

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.Duration
import java.time.Instant

private enum class StationListMode { DISCOVER, SAVED, RECENT }

@Composable
fun RadioBrowseScreen(
    state: RadioUiState,
    savedStations: List<RadioStation>,
    recentStations: List<RadioStation>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCountry: (RadioCountry?) -> Unit,
    onTag: (String, RadioBrowseMode) -> Unit,
    onClearFilters: () -> Unit,
    onToggleSaved: (RadioStation) -> Unit,
    onClearRecent: () -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (RadioStation) -> Unit,
) {
    var browsePanel by remember { mutableStateOf(RadioBrowseMode.COUNTRY) }
    var listMode by remember { mutableStateOf(StationListMode.DISCOVER) }
    var showCountryDialog by remember { mutableStateOf(false) }
    val visibleStations = when (listMode) {
        StationListMode.DISCOVER -> state.stations
        StationListMode.SAVED -> savedStations
        StationListMode.RECENT -> recentStations
    }
    val savedIds = savedStations.mapTo(mutableSetOf(), RadioStation::id)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                        Spacer(Modifier.width(8.dp))
                        Text("INTERNET DIRECTORY", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Internet Radio", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                    Text("Stations from around the world", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(18.dp))
            RadioSearchField(state.query, onQueryChange, onSearch)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = listMode == StationListMode.DISCOVER,
                    onClick = { listMode = StationListMode.DISCOVER },
                    label = { Text("Discover") },
                    leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null, Modifier.size(17.dp)) },
                )
                FilterChip(
                    selected = listMode == StationListMode.SAVED,
                    onClick = { listMode = StationListMode.SAVED },
                    label = { Text("Saved ${savedStations.size}") },
                    leadingIcon = { Icon(Icons.Rounded.Favorite, contentDescription = null, Modifier.size(17.dp)) },
                )
                FilterChip(
                    selected = listMode == StationListMode.RECENT,
                    onClick = { listMode = StationListMode.RECENT },
                    label = { Text("Recent ${recentStations.size}") },
                    leadingIcon = { Icon(Icons.Rounded.Radio, contentDescription = null, Modifier.size(17.dp)) },
                )
            }
        }

        if (listMode == StationListMode.DISCOVER) {
            item {
                Text("Browse", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BrowseButton(
                        label = "Country",
                        selected = browsePanel == RadioBrowseMode.COUNTRY,
                        icon = Icons.Rounded.Language,
                    ) { browsePanel = RadioBrowseMode.COUNTRY }
                    BrowseButton(
                        label = "Genre",
                        selected = browsePanel == RadioBrowseMode.GENRE,
                        icon = Icons.Rounded.GraphicEq,
                    ) { browsePanel = RadioBrowseMode.GENRE }
                    BrowseButton(
                        label = "Category",
                        selected = browsePanel == RadioBrowseMode.CATEGORY,
                        icon = Icons.Rounded.Category,
                    ) { browsePanel = RadioBrowseMode.CATEGORY }
                }
                Spacer(Modifier.height(10.dp))
                when (browsePanel) {
                    RadioBrowseMode.COUNTRY -> CountryBrowser(
                        selected = state.selectedCountry,
                        countries = state.countries,
                        onSelect = onCountry,
                        onShowAll = { showCountryDialog = true },
                    )
                    RadioBrowseMode.GENRE -> TagBrowser(
                        options = RadioGenres,
                        selected = state.selectedTag.takeIf { state.mode == RadioBrowseMode.GENRE },
                        onSelect = { onTag(it, RadioBrowseMode.GENRE) },
                    )
                    RadioBrowseMode.CATEGORY -> TagBrowser(
                        options = RadioCategories,
                        selected = state.selectedTag.takeIf { state.mode == RadioBrowseMode.CATEGORY },
                        onSelect = { onTag(it, RadioBrowseMode.CATEGORY) },
                    )
                    else -> Unit
                }
                if (state.selectedCountry != null || state.selectedTag != null) {
                    TextButton(onClick = onClearFilters) { Text("Clear filter") }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (listMode) {
                            StationListMode.DISCOVER -> radioResultTitle(state)
                            StationListMode.SAVED -> "Saved stations"
                            StationListMode.RECENT -> "Recently played"
                        },
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (listMode == StationListMode.DISCOVER) {
                            "Internet streams • availability can change"
                        } else {
                            "Saved locally • playback still needs internet"
                        },
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                    )
                }
                if (state.isLoading && listMode == StationListMode.DISCOVER) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }

        if (listMode == StationListMode.DISCOVER) state.error?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        if (listMode != StationListMode.DISCOVER && visibleStations.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (listMode == StationListMode.SAVED) "No saved stations yet" else "No recently played stations",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (listMode == StationListMode.SAVED) {
                                "Tap the heart beside any station to keep it here."
                            } else {
                                "Stations you play will appear here."
                            },
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        if (listMode == StationListMode.RECENT && visibleStations.isNotEmpty()) {
            item {
                TextButton(onClick = onClearRecent) { Text("Clear recent stations") }
            }
        }

        items(visibleStations, key = RadioStation::id) { station ->
            StationCard(
                station = station,
                saved = station.id in savedIds,
                onToggleSaved = { onToggleSaved(station) },
                onPlay = { onPlay(station) },
            )
        }
        if (listMode == StationListMode.DISCOVER && visibleStations.isNotEmpty() && state.hasMore) {
            item {
                OutlinedButton(
                    onClick = onLoadMore,
                    enabled = !state.isLoadingMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isLoadingMore) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.isLoadingMore) "Loading more…" else "Load more stations")
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (showCountryDialog) {
        CountryDialog(
            countries = state.countries,
            onSelect = {
                showCountryDialog = false
                onCountry(it)
            },
            onDismiss = { showCountryDialog = false },
        )
    }
}

@Composable
private fun RadioSearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        placeholder = { Text("Search station name…") },
        leadingIcon = { Icon(Icons.Rounded.Radio, contentDescription = null) },
        trailingIcon = {
            FilledIconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Search stations") }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

@Composable
private fun BrowseButton(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(17.dp)) },
    )
}

@Composable
private fun CountryBrowser(
    selected: RadioCountry?,
    countries: List<RadioCountry>,
    onSelect: (RadioCountry?) -> Unit,
    onShowAll: () -> Unit,
) {
    val popular = countries.take(6)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Worldwide") })
            popular.forEach { country ->
                FilterChip(
                    selected = selected?.code == country.code,
                    onClick = { onSelect(country) },
                    label = { Text("${countryFlag(country.code)} ${country.name}") },
                )
            }
        }
        OutlinedButton(onClick = onShowAll, enabled = countries.isNotEmpty()) {
            Text(if (selected == null) "All countries" else "${countryFlag(selected.code)} ${selected.name}")
        }
    }
}

@Composable
private fun TagBrowser(options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { tag ->
                    FilterChip(selected = selected == tag, onClick = { onSelect(tag) }, label = { Text(tag) })
                }
            }
        }
    }
}

@Composable
private fun StationCard(
    station: RadioStation,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    onPlay: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StationArtwork(station, 62)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(station.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(station.country, station.language).filter(String::isNotBlank).joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stationTechnicalLine(station),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stationAvailabilityLine(station),
                    color = if (station.isOnline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onToggleSaved) {
                Icon(
                    if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (saved) "Remove saved station" else "Save station",
                    tint = if (saved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledIconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play station") }
        }
    }
}

@Composable
fun RadioPlayerScreen(
    station: RadioStation,
    saved: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    error: String?,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleSaved: () -> Unit,
    onHomepage: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    radius = 1_500f,
                ),
            ),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 14.dp, top = 8.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to stations")
                Spacer(Modifier.width(7.dp))
                Text("Back to stations", fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 30.dp, vertical = 78.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f), shape = CircleShape) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(
                            if (isPlaying || isBuffering) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        when {
                            isBuffering -> "CONNECTING"
                            isPlaying -> "STREAMING"
                            else -> "READY"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
            Surface(
                modifier = Modifier.size(230.dp),
                shape = RoundedCornerShape(48.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 18.dp,
            ) {
                StationArtwork(station, 230)
            }
            Spacer(Modifier.height(34.dp))
            Text(
                station.name,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                listOf(countryFlag(station.countryCode), station.country, station.language)
                    .filter(String::isNotBlank).joinToString("  "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(stationTechnicalLine(station), color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                TextButton(onClick = onRetry) { Text("Retry stream") }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(58.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous station", Modifier.size(36.dp))
                }
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(78.dp)) {
                    if (isBuffering) {
                        CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play or pause",
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(58.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next station", Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onToggleSaved) {
                    Icon(if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (saved) "Saved" else "Save station")
                }
                if (station.homepageUrl.startsWith("http")) {
                    OutlinedButton(onClick = onHomepage) {
                        Text("Station site")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RadioMiniPlayer(
    station: RadioStation,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StationArtwork(station, 44)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(station.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${if (isBuffering) "CONNECTING" else "STREAM"} • ${station.country}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                )
            }
            FilledIconButton(onClick = onToggle) {
                if (isBuffering) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause")
                }
            }
        }
    }
}

@Composable
private fun StationArtwork(station: RadioStation, size: Int) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(
            Brush.linearGradient(listOf(Color(0xFF23577D), Color(0xFF543F8D))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (station.faviconUrl.startsWith("http")) {
            AsyncImage(
                model = station.faviconUrl,
                contentDescription = "${station.name} logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.Radio, contentDescription = null, tint = Color.White.copy(alpha = 0.76f), modifier = Modifier.size((size / 2).dp))
        }
    }
}

@Composable
private fun CountryDialog(countries: List<RadioCountry>, onSelect: (RadioCountry) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, countries) {
        countries.filter { it.name.contains(query.trim(), ignoreCase = true) || it.code.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a country") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Country name or code") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 430.dp)) {
                    items(filtered, key = RadioCountry::code) { country ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(country) }.padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(countryFlag(country.code), fontSize = 22.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(country.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(country.stationCount.toString(), color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun radioResultTitle(state: RadioUiState): String {
    state.selectedCountry?.let { country ->
        return "${countryFlag(country.code)} ${country.name}"
    }
    state.selectedTag?.let { return it }
    return if (state.query.isNotBlank()) "Results for “${state.query}”" else "Popular stations"
}

private fun stationTechnicalLine(station: RadioStation): String = buildList {
    if (station.isHls) add("HLS")
    if (station.codec.isNotBlank()) add(station.codec.uppercase())
    if (station.bitrate > 0) add("${station.bitrate} kbps")
    station.tags.firstOrNull()?.let(::add)
}.joinToString(" • ")

internal fun stationAvailabilityLine(station: RadioStation, now: Instant = Instant.now()): String {
    val status = if (station.isOnline) "Online when checked" else "Offline when checked"
    val checkedAt = runCatching { Instant.parse(station.lastCheckedAt) }.getOrNull() ?: return status
    val age = Duration.between(checkedAt, now).coerceAtLeast(Duration.ZERO)
    val ageLabel = when {
        age.toMinutes() < 1 -> "just now"
        age.toHours() < 1 -> "${age.toMinutes()}m ago"
        age.toDays() < 1 -> "${age.toHours()}h ago"
        else -> "${age.toDays()}d ago"
    }
    return "$status • $ageLabel"
}

private fun countryFlag(code: String): String {
    if (code.length != 2) return ""
    return code.uppercase().map { char -> Character.toChars(0x1F1E6 + (char.code - 'A'.code)).concatToString() }.joinToString("")
}
