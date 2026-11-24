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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuotesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(ctx).quotes() }
    val total by remember { dao.observeCount() }.collectAsState(initial = 0)
    val activeCount by remember { dao.observeActiveCount() }.collectAsState(initial = 0)
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
        Pager(PagingConfig(pageSize = 30, prefetchDistance = 15, enablePlaceholders = false)) {
            dao.paged(debounced, category, if (favoritesOnly) 1 else 0, if (showActive) 1 else 0)
        }.flow.cachedIn(scope)
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

    // Select mode: long-press a quote (or menu > Select), tick quotes, Select all, Delete
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var matchingIds by remember { mutableStateOf(emptyList<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(selectMode, debounced, category, favoritesOnly, total, showActive, activeCount) {
        if (selectMode) matchingIds = dao.matchingIds(debounced, category, if (favoritesOnly) 1 else 0, if (showActive) 1 else 0)
    }
    val allSelected = matchingIds.isNotEmpty() && selected.containsAll(matchingIds)
    fun exitSelect() { selectMode = false; selected = emptySet(); confirmDelete = false }
    BackHandler(enabled = selectMode) { exitSelect() }

    /** Moves quotes between Active and Inactive, with an Undo bar. */
    fun moveQuotes(ids: List<Long>, toActive: Boolean) {
        if (ids.isEmpty()) return
        scope.launch {
            withContext(Dispatchers.IO) { ids.chunked(500).forEach { dao.setActive(it, toActive) } }
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = ctx.getString(if (toActive) R.string.moved_to_active_n else R.string.moved_to_inactive_n, ids.size),
                actionLabel = ctx.getString(R.string.undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) withContext(Dispatchers.IO) {
                ids.chunked(500).forEach { dao.setActive(it, !toActive) }
            }
        }
    }

    fun moveSelected() {
        val ids = selected.toList()
        val toActive = !showActive
        exitSelect()
        moveQuotes(ids, toActive)
    }

    fun deleteSelected() {
        val ids = selected.toList()
        exitSelect()
        scope.launch {
            // keep the rows in memory so Undo can put them back (in chunks: SQLite limits how many ids one query takes)
            val removed = withContext(Dispatchers.IO) {
                val rows = ids.chunked(500).flatMap { dao.byIds(it) }
                ids.chunked(500).forEach { dao.deleteByIds(it) }
                rows
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
                        stringResource(R.string.n_selected, selected.size),
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { selected = if (allSelected) emptySet() else matchingIds.toSet() }) {
                        Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all))
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = selected.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
                QuietButton(
                    onClick = { moveSelected() },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)
                ) {
                    Text(stringResource(if (showActive) R.string.move_to_inactive else R.string.move_to_active))
                }
            } else Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenTitle(stringResource(R.string.quotes_title), stringResource(R.string.quotes_saved, total), Modifier.weight(1f))
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.select)) },
                            enabled = total > 0,
                            onClick = { menuOpen = false; selectMode = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_json)) },
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

            if (total > 0) {
                TabRow(
                    selectedTabIndex = if (showActive) 0 else 1,
                    containerColor = Color.Transparent,
                    divider = {},
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Tab(
                        selected = showActive,
                        onClick = { if (!showActive) { showActive = true; exitSelect() } },
                        text = { Text(stringResource(R.string.tab_active, activeCount)) }
                    )
                    Tab(
                        selected = !showActive,
                        onClick = { if (showActive) { showActive = false; exitSelect() } },
                        text = { Text(stringResource(R.string.tab_inactive, inactiveCount)) }
                    )
                }
                OutlinedTextField(
                colors = quietFieldColors(),
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_quotes)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
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
                items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                    val quote = items[index] ?: return@items
                    val isSelected = quote.id in selected
                    val rowContent: @Composable () -> Unit = {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        if (selectMode) selected = if (isSelected) selected - quote.id else selected + quote.id
                                    },
                                    onLongClick = {
                                        if (!selectMode) { selectMode = true; selected = selected + quote.id }
                                    },
                                    // double tap opens the quote for editing
                                    onDoubleClick = {
                                        if (!selectMode) Nav.open(Page.EditQuote(quote))
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectMode) Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(start = 4.dp, end = 12.dp))
                            Box(Modifier.weight(1f)) {
                                QuoteRow(
                                    quote,
                                    serial = index + 1,
                                    selecting = selectMode,
                                    onDelete = { pendingDelete = quote },
                                    onToggleFavorite = { scope.launch { dao.setFavorite(quote.id, !quote.isFavorite) } },
                                    onToggleActive = { moveQuotes(listOf(quote.id), !quote.isActive) },
                                    onEdit = { Nav.open(Page.EditQuote(quote)) }
                                )
                            }
                        }
                    }
                    if (selectMode) rowContent() else SwipeToDeleteRow(onDelete = { pendingDelete = quote }) { rowContent() }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + LocalNavInset.current))

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

/**
 * Swipe a quote to the left to delete it (an Undo bar appears). Swiping to the right is left free,
 * so it still moves to the previous tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val currentDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        // ask first: the row springs back, and is removed only if the person confirms
        if (value == SwipeToDismissBoxValue.EndToStart) currentDelete()
        false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

@Composable
private fun QuoteRow(
    quote: Quote,
    serial: Int,
    selecting: Boolean,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        var menuOpen by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$serial  ·  ${categoryEmoji(quote.category)} ${quote.category}" +
                    (if (quote.isFavorite) "  ★" else "") +
                    (if (quote.imagePath != null) "  🖼️" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (!selecting) Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options), tint = colors.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (quote.isFavorite) R.string.remove_favorite else R.string.add_favorite)) },
                        leadingIcon = {
                            Icon(
                                painterResource(if (quote.isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline),
                                contentDescription = null
                            )
                        },
                        onClick = { menuOpen = false; onToggleFavorite() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (quote.isActive) R.string.move_to_inactive else R.string.move_to_active)) },
                        onClick = { menuOpen = false; onToggleActive() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
        Text(
            quote.text,
            fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 25.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (quote.author.isNotBlank()) {
            Text(
                "— ${quote.author}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
