package com.trulyfreemusic.opengroove.playback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.trulyfreemusic.opengroove.model.Track

@Composable
fun MusicMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    queueSize: Int,
    onOpen: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
    ) {
        Column {
            Slider(
                value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxWidth().height(20.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = "${track.title} artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (queueSize > 1) "${track.artist} • $queueSize in queue" else track.artist,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPrevious, enabled = queueSize > 1) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track")
                }
                FilledIconButton(onClick = onToggle) {
                    if (isBuffering) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play or pause",
                        )
                    }
                }
                IconButton(onClick = onNext, enabled = queueSize > 1) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next track")
                }
            }
        }
    }
}

@Composable
fun MusicPlayerScreen(
    track: Track,
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    error: String?,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to music")
                    }
                    Text("Now playing", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = "${track.title} artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(230.dp).clip(RoundedCornerShape(28.dp)),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(track.title, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (track.album.isNotBlank()) {
                        Text(track.album, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Spacer(Modifier.height(12.dp))
                    Slider(
                        value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatMusicDuration(positionMs), fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text(formatMusicDuration(durationMs), fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        IconButton(onClick = onShuffle, enabled = queue.size > 1) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = if (shuffleEnabled) "Turn shuffle off" else "Turn shuffle on",
                                tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onPrevious, enabled = queue.size > 1) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track", Modifier.size(34.dp))
                        }
                        FilledIconButton(onClick = onToggle, modifier = Modifier.size(64.dp)) {
                            if (isBuffering) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                            else Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play or pause",
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        IconButton(onClick = onNext, enabled = queue.size > 1) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next track", Modifier.size(34.dp))
                        }
                        IconButton(onClick = onRepeat) {
                            Icon(
                                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> "Repeat one"
                                    Player.REPEAT_MODE_ALL -> "Repeat all"
                                    else -> "Repeat off"
                                },
                                tint = if (repeatMode == Player.REPEAT_MODE_OFF) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(7.dp))
                    Text("Up next", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${queue.size}", color = MaterialTheme.colorScheme.secondary)
                }
            }
            itemsIndexed(queue, key = { index, queued -> "$index:${queued.providerName}:${queued.id}" }) { index, queued ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onJumpTo(index) },
                    color = if (index == currentIndex) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                            if (index == currentIndex) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Currently playing", Modifier.size(19.dp))
                            } else {
                                Text("${index + 1}", fontSize = 12.sp)
                            }
                        }
                        AsyncImage(
                            model = queued.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)),
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(queued.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(queued.artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Column {
                            IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(onClick = { onMove(index, index + 1) }, enabled = index < queue.lastIndex, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
                            }
                        }
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove from queue")
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear queue")
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

private fun formatMusicDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
