package com.smrti.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.smrti.app.data.Album
import com.smrti.app.data.AlbumStore
import com.smrti.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smrti.app.data.AppDatabase
import com.smrti.app.data.ImageStore
import kotlinx.coroutines.launch
import java.io.File

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.width

@Composable
fun ImagesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val dao = remember { AppDatabase.get(ctx).images() }
    val images by remember { dao.observeAll() }.collectAsState(initial = emptyList())

    var order by remember { mutableStateOf(Prefs.imageOrder(ctx)) }
    var albumIds by remember { mutableStateOf(Prefs.albumIds(ctx)) }
    var albumPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var orderMenu by remember { mutableStateOf(false) }

    // the album page changes the saved choice: pick it up when that page closes
    LaunchedEffect(Nav.page) { if (Nav.page == null) albumIds = Prefs.albumIds(ctx) }
    // photos of the chosen albums, shown in the grid below
    LaunchedEffect(albumIds) {
        albumPhotos = withContext(Dispatchers.IO) { AlbumStore.imageUris(ctx, albumIds) }
    }

    val permissions = remember {
        when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    fun hasAccess() = permissions.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasAccess()) Nav.open(Page.Albums)
        else Toast.makeText(ctx, "Allow photo access to pick albums", Toast.LENGTH_SHORT).show()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        scope.launch { uris.forEach { ImageStore.import(ctx, it) } }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(
                "Images",
                buildString {
                    append("${images.size} saved")
                    if (albumIds.isNotEmpty()) append(" · ${albumIds.size} album${if (albumIds.size == 1) "" else "s"}")
                    append(if (order == Prefs.ORDER_SERIES) " · shown in order" else " · shown at random")
                },
                Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Panel {
                        // compact drop-down instead of two big tiles
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Show images", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (order == Prefs.ORDER_SERIES) "One after another, then loops" else "A different one each time",
                                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                                )
                            }
                            Box {
                                val menuShape = RoundedCornerShape(12.dp)
                                Row(
                                    Modifier
                                        .clip(menuShape)
                                        .background(colors.surfaceVariant)
                                        .border(1.dp, colors.outline, menuShape)
                                        .clickable { orderMenu = true }
                                        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (order == Prefs.ORDER_SERIES) "In series" else "Random",
                                        style = MaterialTheme.typography.bodyMedium, color = colors.primary
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Change order", tint = colors.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = orderMenu, onDismissRequest = { orderMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Random", color = if (order == Prefs.ORDER_RANDOM) colors.primary else colors.onSurface) },
                                        onClick = {
                                            order = Prefs.ORDER_RANDOM; Prefs.setImageOrder(ctx, order); orderMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("In series", color = if (order == Prefs.ORDER_SERIES) colors.primary else colors.onSurface) },
                                        onClick = {
                                            order = Prefs.ORDER_SERIES; Prefs.setImageOrder(ctx, order); orderMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Phone albums", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (albumIds.isEmpty()) "Use every photo from albums you choose"
                                    else "${albumIds.size} selected · ${albumPhotos.size} photos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { if (hasAccess()) Nav.open(Page.Albums) else askPhotos.launch(permissions) }) {
                                Text("Choose")
                            }
                        }
                    }
                }

                if (images.isEmpty() && albumPhotos.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            emoji = "🖼️",
                            title = "No pictures yet",
                            message = "Add photos, or choose a whole album, and one will appear with each reminder.",
                            actionLabel = "Add photos"
                        ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    }
                }

                if (images.isNotEmpty() && albumPhotos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { GridLabel("Saved in Smrti · ${images.size}") }
                }
                items(images, key = { "saved-${it.id}" }) { image ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
                        AsyncImage(
                            model = File(image.path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(32.dp)
                                .background(Color(0x99000000), CircleShape)
                                .clickable { scope.launch { ImageStore.remove(ctx, image) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // photos from the albums that were picked (they stay in the gallery, so no delete here)
                if (albumPhotos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { GridLabel("From your albums · ${albumPhotos.size}") }
                    items(albumPhotos, key = { "album-$it" }) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Add photos") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun GridLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}
