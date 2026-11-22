package com.smrti.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.smrti.app.data.Album
import com.smrti.app.data.AlbumStore
import com.smrti.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pick which phone albums feed the reminders. Full page inside the app. */
@Composable
fun AlbumsPage() {
    val colors = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var albums by remember { mutableStateOf<List<Album>?>(null) }
    var picked by remember { mutableStateOf(Prefs.albumIds(ctx)) }
    LaunchedEffect(Unit) { albums = withContext(Dispatchers.IO) { AlbumStore.listAlbums(ctx) } }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Choose albums",
            if (picked.isEmpty()) "Every photo in them feeds your reminders" else "${picked.size} selected"
        )

        val list = albums
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                list == null -> Text(
                    "Loading…", color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
                list.isEmpty() -> EmptyState(
                    emoji = "📷",
                    title = "No albums found",
                    message = "Photos on your phone will show up here as albums."
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list, key = { it.id }) { album ->
                        val on = album.id in picked
                        val shape = RoundedCornerShape(20.dp)
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(shape)
                                .border(if (on) 3.dp else 1.dp, if (on) colors.primary else colors.outline, shape)
                                .clickable { picked = if (on) picked - album.id else picked + album.id }
                        ) {
                            AsyncImage(
                                model = album.cover, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                                    .padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 10.dp)
                            ) {
                                Column {
                                    Text(
                                        album.name, color = Color.White, fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text("${album.count} photos", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                                }
                            }
                            if (on) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(28.dp)
                                        .background(colors.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { Nav.close() }, modifier = Modifier.height(56.dp)) { Text("Cancel") }
            Button(
                onClick = {
                    Prefs.setAlbumIds(ctx, picked)
                    Prefs.setSeriesIndex(ctx, 0)
                    Nav.close()
                },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
