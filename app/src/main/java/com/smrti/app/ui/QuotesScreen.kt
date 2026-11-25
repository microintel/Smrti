package com.smrti.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.LoadState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.smrti.app.R
import com.smrti.app.data.AppDatabase
import com.smrti.app.data.Quote
import com.smrti.app.data.QuoteImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateMapOf
import androidx.room.withTransaction
import com.smrti.app.data.QuoteCounts
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuotesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(ctx).quotes() }
    val counts by remember { dao.observeCounts() }.collectAsState(initial = QuoteCounts(0, 0))
    val total = counts.total
    val activeCount = counts.active
    val inactiveCount = (total - activeCount).coerceAtLeast(0)
    // Active quotes are used for reminders; Inactive ones are kept but never sent
    var showActive by remember { mutableStateOf(true) }
    val categories by remember { dao.observeCategories() }.collectAsState(initial = emptyList())
    val snackbar = remember { SnackbarHostState() }

    // search + filters; the list only loads the page of quotes that is on screen
    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    LaunchedEffect(query) { delay(250); debounced = query.trim() }

    val flow = remember(debounced, category, favoritesOnly, showActive) {
        Pager(PagingConfig(pageSize = 40, initialLoadSize = 40, prefetchDistance = 20, enablePlaceholders = false)) {
            dao.paged(debounced, category, if (favoritesOnly) 1 else 0, if (showActive) 1 else 0)
        }.flow
    }
    val items = flow.collectAsLazyPagingItems()
    val filtering = debounced.isNotEmpty() || category.isNotEmpty() || favoritesOnly

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val msg = try {
                val json = QuoteImporter.exportJson(ctx)
                ctx.contentResolver.openOutputStream(uri, "w")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                ctx.getString(R.string.export_done, total)
            } catch (_: Exception) {
                ctx.getString(R.string.export_failed)
            }
            withContext(Dispatchers.Main) { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
        }
    }

    // Select mode: long-press a quote, tick more, or "Select all". Select all does NOT build a list of
    // thousands of ids: it is one flag, and `marked` then holds only the quotes that were un-ticked.
    var selectMode by remember { mutableStateOf(false) }
    var selectAll by remember { mutableStateOf(false) }
    val marked = remember { mutableStateMapOf<Long, Boolean>() }
    var matchingCount by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sheetQuote by remember { mutableStateOf<Quote?>(null) }
    val room = remember { AppDatabase.get(ctx) }
    LaunchedEffect(selectMode, debounced, category, favoritesOnly, showActive) {
        if (selectMode) matchingCount = dao.countMatching(debounced, category, if (favoritesOnly) 1 else 0, if (showActive) 1 else 0)
    }
    val selectedCount = if (selectAll) (matchingCount - marked.size).coerceAtLeast(0) else marked.size
    val allSelected = matchingCount > 0 && selectedCount == matchingCount
    fun isPicked(id: Long) = if (selectAll) !marked.containsKey(id) else marked.containsKey(id)
    fun toggle(id: Long) { if (marked.containsKey(id)) marked.remove(id) else marked[id] = true }
    fun exitSelect() { selectMode = false; selectAll = false; marked.clear(); confirmDelete = false }
    BackHandler(enabled = selectMode) { exitSelect() }

    // search is tucked behind an icon; closing it also clears the search and the filters
    var searchOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchOpen) { if (searchOpen) searchFocus.requestFocus() }
    fun closeSearch() { searchOpen = false; query = ""; debounced = ""; category = ""; favoritesOnly = false }
    BackHandler(enabled = searchOpen && !selectMode) { closeSearch() }

    // made once, so rows that did not change are skipped instead of redrawn on every tick of the selection
    val onRowClick = remember { { q: Quote -> if (selectMode) { toggle(q.id) } else { sheetQuote = q } } }
    val onRowLongClick = remember {
        { q: Quote -> if (selectMode) { toggle(q.id) } else { selectMode = true; selectAll = false; marked[q.id] = true } }
    }

    /** Turns the selection into ids (one query, off the main thread), leaves select mode, then runs [action]. */
    fun withSelectedIds(action: suspend (List<Long>) -> Unit) {
        val all = selectAll
        val skip = marked.keys.toSet()
        val q = debounced
        val cat = category
        val fav = if (favoritesOnly) 1 else 0
        val act = if (showActive) 1 else 0
        exitSelect()
        scope.launch {
            val ids = withContext(Dispatchers.IO) {
                if (all) dao.matchingIds(q, cat, fav, act).filter { it !in skip } else skip.toList()
            }
            action(ids)
        }
    }

    /** Moves quotes between Active and Inactive, with an Undo bar. One transaction = one list refresh. */
    fun moveQuotes(ids: List<Long>, toActive: Boolean) {
        if (ids.isEmpty()) return
        scope.launch {
            withContext(Dispatchers.IO) { room.withTransaction { ids.chunked(500).forEach { dao.setActive(it, toActive) } } }
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = ctx.getString(if (toActive) R.string.moved_to_active_n else R.string.moved_to_inactive_n, ids.size),
                actionLabel = ctx.getString(R.string.undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) withContext(Dispatchers.IO) {
                room.withTransaction { ids.chunked(500).forEach { dao.setActive(it, !toActive) } }
            }
        }
    }

    fun moveSelected() {
        val toActive = !showActive
        withSelectedIds { ids -> moveQuotes(ids, toActive) }
    }

    fun deleteSelected() {
        withSelectedIds { ids ->
            // keep the rows in memory so Undo can put them back (in chunks: SQLite limits how many ids one query takes)
            val removed = withContext(Dispatchers.IO) {
                room.withTransaction {
                    val rows = ids.chunked(500).flatMap { dao.byIds(it) }
                    ids.chunked(500).forEach { dao.deleteByIds(it) }
                    rows
                }
            }
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = ctx.getString(R.string.quotes_deleted_n, removed.size),
                actionLabel = ctx.getString(R.string.undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) withContext(Dispatchers.IO) { dao.insertAll(removed) }
        }
    }

    // a single quote is only deleted after the person confirms (menu or swipe)
    var pendingDelete by remember { mutableStateOf<Quote?>(null) }

    fun deleteWithUndo(quote: Quote) {
        scope.launch {
            dao.delete(quote)
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = ctx.getString(R.string.quote_deleted),
                actionLabel = ctx.getString(R.string.undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) dao.insert(quote)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selectMode) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { exitSelect() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                    Text(
                        stringResource(R.string.n_selected, selectedCount),
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                    )
                }
            } else if (searchOpen) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { closeSearch() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                    OutlinedTextField(
                        colors = quietFieldColors(),
                        value = query, onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_quotes)) },
                        singleLine = true, shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).focusRequester(searchFocus)
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    item {
                        QuietFilterChip(
                            selected = category.isEmpty() && !favoritesOnly,
                            onClick = { category = ""; favoritesOnly = false },
                            label = { Text(stringResource(R.string.filter_all)) }
                        )
                    }
                    item {
                        QuietFilterChip(
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = !favoritesOnly },
                            label = { Text("★ " + stringResource(R.string.filter_favorites)) }
                        )
                    }
                    items(categories, key = { it }) { c ->
                        QuietFilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) "" else c },
                            label = { Text("${categoryEmoji(c)} $c") }
                        )
                    }
                }
            } else Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenTitle(
                    stringResource(R.string.quotes_title),
                    if (showActive) stringResource(R.string.quotes_saved, total) else stringResource(R.string.tab_inactive, inactiveCount),
                    Modifier.weight(1f)
                )
                if (total > 0) IconButton(onClick = { searchOpen = true }) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search_quotes))
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.select)) },
                            enabled = total > 0,
                            leadingIcon = { Icon(painterResource(R.drawable.ic_select), contentDescription = null) },
                            onClick = { menuOpen = false; selectMode = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (showActive) R.string.show_inactive_quotes else R.string.show_active_quotes)) },
                            enabled = total > 0,
                            leadingIcon = {
                                Icon(
                                    painterResource(if (showActive) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                    contentDescription = null
                                )
                            },
                            onClick = { menuOpen = false; exitSelect(); showActive = !showActive }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_json)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_import), contentDescription = null) },
                            onClick = { menuOpen = false; Nav.open(Page.ImportGuide) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_quotes)) },
                            enabled = total > 0,
                            leadingIcon = { Icon(painterResource(R.drawable.ic_download), contentDescription = null) },
                            onClick = { menuOpen = false; export.launch("smrti-quotes.json") }
                        )
                    }
                }
            }

            if (total == 0) {
                EmptyState(
                    emoji = "💬",
                    title = stringResource(R.string.no_quotes_title),
                    message = stringResource(R.string.no_quotes_message),
                    actionLabel = stringResource(R.string.add_a_quote),
                    modifier = Modifier.padding(top = 24.dp)
                ) { Nav.open(Page.AddQuote) }
            } else if (filtering && items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading) {
                EmptyState(
                    emoji = "🔍",
                    title = stringResource(R.string.no_matches_title),
                    message = stringResource(R.string.no_matches_message),
                    actionLabel = stringResource(R.string.clear_filters),
                    modifier = Modifier.padding(top = 16.dp)
                ) { query = ""; debounced = ""; category = ""; favoritesOnly = false }
            }

            if (total > 0 && !filtering && items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading) {
                EmptyState(
                    emoji = if (showActive) "✨" else "💤",
                    title = stringResource(if (showActive) R.string.no_active_title else R.string.no_inactive_title),
                    message = stringResource(if (showActive) R.string.no_active_message else R.string.no_inactive_message),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 100.dp + LocalNavInset.current)) {
                items(count = items.itemCount, key = items.itemKey { it.id }, contentType = { 0 }) { index ->
                    val quote = items[index] ?: return@items
                    QuoteRow(
                        quote = quote,
                        selecting = selectMode,
                        selected = selectMode && isPicked(quote.id),
                        onClick = onRowClick,
                        onLongClick = onRowLongClick
                    )
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + LocalNavInset.current))

        sheetQuote?.let { q ->
            QuoteSheet(
                quote = q,
                onDismiss = { sheetQuote = null },
                onFavorite = { sheetQuote = null; scope.launch { dao.setFavorite(q.id, !q.isFavorite) } },
                onSelect = { sheetQuote = null; selectMode = true; selectAll = false; marked[q.id] = true },
                onToggleActive = { sheetQuote = null; moveQuotes(listOf(q.id), !q.isActive) },
                onEdit = { sheetQuote = null; Nav.open(Page.EditQuote(q)) },
                onDelete = { sheetQuote = null; pendingDelete = q }
            )
        }

        // select mode: the actions sit at the bottom, easy to reach with a thumb
        if (selectMode) androidx.compose.material3.Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalNavInset.current).fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuietButton(
                    onClick = { selectAll = !allSelected; marked.clear() },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all)) }
                QuietButton(
                    onClick = { moveSelected() },
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(if (showActive) R.string.move_to_inactive else R.string.move_to_active)) }
                TextButton(onClick = { confirmDelete = true }, enabled = selectedCount > 0) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        pendingDelete?.let { q ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.delete_quote_title)) },
                text = {
                    Text(
                        "“" + q.text.take(120) + (if (q.text.length > 120) "…" else "") + "”\n\n" +
                            stringResource(R.string.delete_quote_body)
                    )
                },
                confirmButton = { TextButton(onClick = { pendingDelete = null; deleteWithUndo(q) }) { Text(stringResource(R.string.delete)) } },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        if (confirmDelete) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_selected_title)) },
            text = { Text(stringResource(R.string.delete_selected_body)) },
            confirmButton = { TextButton(onClick = { deleteSelected() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )

        if (!selectMode) ExtendedFloatingActionButton(
            onClick = { Nav.open(Page.AddQuote) },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.add_quote_fab)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + LocalNavInset.current),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/** One quote: its text and one quiet line underneath. Tap for actions, long-press to select. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuoteRow(
    quote: Quote,
    selecting: Boolean,
    selected: Boolean,
    onClick: (Quote) -> Unit,
    onLongClick: (Quote) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = { onClick(quote) }, onLongClick = { onLongClick(quote) })
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // the picture chosen for this quote, so it is visible before the quote is opened
        quote.imagePath?.let { path ->
            val ctx = LocalContext.current
            AsyncImage(
                model = remember(path) { ImageRequest.Builder(ctx).data(imageModel(path)).size(Size(168, 168)).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(end = 14.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                quote.text,
                fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 25.sp,
                maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(quote.author.takeIf { it.isNotBlank() }?.let { "— $it" }, quote.category)
                .joinToString("  ·  ") + (if (quote.isFavorite) "  ★" else "")
            Text(
                meta,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        // the selection mark: its space is always kept, so the text does not re-wrap (and jump) when select mode starts
        Box(
            Modifier
                .padding(start = 12.dp)
                .size(22.dp)
                .then(
                    if (!selecting) Modifier
                    else if (selected) Modifier.clip(CircleShape).background(colors.primary)
                    else Modifier.border(1.5.dp, colors.outline, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Text("✓", color = colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** What you can do with one quote: big full-width rows near the thumb instead of a small menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuoteSheet(
    quote: Quote,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onSelect: () -> Unit,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        quote.imagePath?.let { path ->
            val ctx = LocalContext.current
            AsyncImage(
                model = remember(path) { reminderImageRequest(ctx, path) },
                contentDescription = null,
                contentScale = ContentScale.Fit,   // the whole picture, never cropped
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceVariant)
            )
        }
        Text(
            "“" + quote.text.take(140) + (if (quote.text.length > 140) "…" else "") + "”",
            fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 23.sp,
            maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        SheetAction(
            label = stringResource(if (quote.isFavorite) R.string.remove_favorite else R.string.add_favorite),
            onClick = onFavorite,
            icon = {
                Icon(
                    painterResource(if (quote.isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline),
                    contentDescription = null
                )
            }
        )
        SheetAction(label = stringResource(R.string.select), onClick = onSelect)
        SheetAction(
            label = stringResource(if (quote.isActive) R.string.move_to_inactive else R.string.move_to_active),
            onClick = onToggleActive
        )
        SheetAction(
            label = stringResource(R.string.edit), onClick = onEdit,
            icon = { Icon(Icons.Default.Edit, contentDescription = null) }
        )
        SheetAction(
            label = stringResource(R.string.delete), onClick = onDelete, color = colors.error,
            icon = { Icon(Icons.Default.Delete, contentDescription = null) }
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurface, icon: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides color) {
            Box(Modifier.size(24.dp)) { icon?.invoke() }
            Spacer(Modifier.width(20.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
