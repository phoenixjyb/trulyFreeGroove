package com.trulyfreemusic.opengroove.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trulyfreemusic.opengroove.localization.LocalizedText as Text
import androidx.compose.material3.Text as ContentText
import com.trulyfreemusic.opengroove.model.Track

@Composable
fun HomeMusicScreen(
    state: HomeMusicState,
    onConnect: (String, String, String) -> Unit,
    onDisconnect: () -> Unit,
    onBrowse: () -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onAlbum: (HomeAlbum) -> Unit,
    onMore: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onQueue: (Track) -> Unit,
) {
    var server by remember(state.server) { mutableStateOf(state.server) }
    var username by remember(state.username) { mutableStateOf(state.username) }
    var password by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Home Music", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Your collection on home Wi-Fi", color = MaterialTheme.colorScheme.secondary)
        }
        if (!state.configured) {
            item { Text("Connect to your Navidrome server. Play only music you own or have permission to stream.") }
            item { OutlinedTextField(server, { server = it }, label = { Text("Server URL") },
                placeholder = { ContentText("http://192.168.1.20:4533") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth()) }
            item { Text("HTTP is unencrypted. Use a dedicated music password on trusted home Wi-Fi. No port forwarding.", fontSize = 12.sp) }
            item { Button(onClick = { onConnect(server, username, password); password = "" }, enabled = !state.loading && password.isNotEmpty()) { Text("Connect") } }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        ContentText(state.server, fontSize = 12.sp)
                        Text("Private library • Personal streaming", fontSize = 12.sp)
                    }
                    TextButton(onClick = onDisconnect) { Text("Disconnect") }
                }
            }
            item {
                OutlinedTextField(state.query, onQuery, label = { Text("Search albums, artists and songs") },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    trailingIcon = { TextButton(onClick = onSearch, enabled = !state.loading) { Text("Search") } }, modifier = Modifier.fillMaxWidth())
            }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBrowse, enabled = !state.loading) { Text("Albums") }
                state.selectedAlbum?.let { ContentText(it.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } }
            if (state.selectedAlbum == null) {
                items(state.albums, key = { "album:${it.id}" }) { album ->
                    ElevatedCard(Modifier.fillMaxWidth().clickable(enabled = !state.loading) { onAlbum(album) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Album, contentDescription = null, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                ContentText(album.title, fontWeight = FontWeight.Bold)
                                ContentText(album.artist, fontSize = 12.sp)
                                Text("${album.songCount} tracks", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (state.songs.isNotEmpty()) {
                item { Button(onClick = { onPlay(state.songs, 0) }) { Icon(Icons.Rounded.PlayArrow, null); Text("Play all") } }
                items(state.songs, key = { "song:${it.id}" }) { song ->
                    Row(Modifier.fillMaxWidth().clickable { onPlay(state.songs, state.songs.indexOf(song)) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            ContentText(song.title, fontWeight = FontWeight.Bold)
                            ContentText(listOf(song.artist, song.album).filter(String::isNotBlank).joinToString(" • "), fontSize = 12.sp)
                            Text("Home Music • Personal streaming", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onQueue(song) }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue track") }
                    }
                }
            }
            if (!state.loading && state.albums.isEmpty() && state.songs.isEmpty()) item { Text("No music found. Check the server library and scan status.") }
            if (state.canLoadMore && state.selectedAlbum == null) item { OutlinedButton(onClick = onMore, enabled = !state.loading) { Text("Load more") } }
        }
        if (state.loading) item { CircularProgressIndicator(Modifier.size(28.dp)) }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
    }
}
