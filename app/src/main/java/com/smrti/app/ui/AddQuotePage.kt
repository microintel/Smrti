package com.smrti.app.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smrti.app.data.AppDatabase
import com.smrti.app.data.Prefs
import com.smrti.app.data.ImageStore
import com.smrti.app.data.Quote
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.smrti.app.R
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import java.text.BreakIterator

/** Splits typed/pasted text into single emoji-like symbols (letters, digits and spaces are ignored). */
private fun extractEmojis(input: String): List<String> {
    val out = ArrayList<String>()
    val it = BreakIterator.getCharacterInstance()
    it.setText(input)
    var start = it.first()
    var end = it.next()
    while (end != BreakIterator.DONE) {
        val g = input.substring(start, end)
        val cp = g.codePointAt(0)
        if (g.isNotBlank() && !Character.isLetterOrDigit(cp) && !Character.isWhitespace(cp)) out.add(g)
        start = end
        end = it.next()
    }
    return out
}

private val categories = listOf("Quote", "Motivation", "Dialogue")

/** Emoji of the categories the user created (loaded at start, updated when a new one is made). */
object CategoryIcons {
    var custom by mutableStateOf(mapOf<String, String>())
}

internal fun categoryEmoji(category: String) = when (category) {
    "Quote" -> "💬"
    "Motivation" -> "🔥"
    "Dialogue" -> "🎬"
    else -> CategoryIcons.custom[category] ?: "✨"
}

// Quick-insert emoji, grouped. The phone keyboard's own emoji work in the field too.
private val emojiGroups = listOf(
    "😊" to listOf("😊", "😄", "😍", "🥰", "😎", "🤩", "😌", "🙂", "😉", "🤔", "😢", "😴", "🥳", "🤗", "😇", "🙏"),
    "❤️" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💖", "💫", "💕", "💞", "😘", "🫶", "👏", "👍"),
    "🔥" to listOf("🔥", "💪", "⚡", "🚀", "🏆", "🎯", "🥇", "⭐", "🌟", "✨", "💯", "📈", "🧠", "💡", "✅", "🎉"),
    "🌿" to listOf("🌿", "🌸", "🌻", "🌈", "☀️", "🌙", "⭐", "🌊", "🍃", "🌳", "🦋", "🕊️", "🏔️", "🌅", "🍀", "🪔"),
    "🎬" to listOf("🎬", "🎭", "🎤", "🎧", "🎵", "📖", "✍️", "📝", "🕰️", "⏳", "🗝️", "🔔", "🧭", "🪞", "🕯️", "💭")
)

/** Popup with every emoji group plus the user's own emojis. Calls [onPick] for each tap; the caller decides whether to close. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiPopup(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var group by remember { mutableIntStateOf(0) }
    var mine by remember { mutableStateOf(Prefs.customEmojis(ctx)) }
    var newEmoji by remember { mutableStateOf("") }
    val myTab = emojiGroups.size // the last tab is the user's own emojis

    fun addMine() {
        val added = extractEmojis(newEmoji).filter { it !in mine }.distinct()
        if (added.isNotEmpty()) {
            mine = (mine + added).takeLast(80)
            Prefs.setCustomEmojis(ctx, mine)
        }
        newEmoji = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    emojiGroups.forEachIndexed { i, (icon, _) ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (i == group) colors.secondaryContainer else Color.Transparent)
                                .clickable { group = i },
                            contentAlignment = Alignment.Center
                        ) { Text(icon, fontSize = 20.sp) }
                    }
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (group == myTab) colors.secondaryContainer else Color.Transparent)
                            .clickable { group = myTab },
                        contentAlignment = Alignment.Center
                    ) { Text("➕", fontSize = 18.sp) }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    if (group < myTab) {
                        emojiGroups[group].second.chunked(5).forEach { rowItems ->
                            Row {
                                rowItems.forEach { e ->
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape).clickable { onPick(e) },
                                        contentAlignment = Alignment.Center
                                    ) { Text(e, fontSize = 24.sp) }
                                }
                            }
                        }
                    } else {
                        // your own emojis: add from your keyboard, tap to use, hold to remove
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                colors = quietFieldColors(),
                                value = newEmoji, onValueChange = { newEmoji = it },
                                placeholder = { Text(stringResource(R.string.type_or_paste_emoji)) },
                                singleLine = true, shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = extractEmojis(newEmoji).isNotEmpty(),
                                onClick = { addMine() },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.heightIn(min = 56.dp)
                            ) { Text(stringResource(R.string.add)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (mine.isEmpty()) {
                            Text(
                                stringResource(R.string.open_your_keyboard_s_emoji_pick),
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                            )
                        } else {
                            mine.chunked(5).forEach { rowItems ->
                                Row {
                                    rowItems.forEach { e ->
                                        Box(
                                            Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .combinedClickable(
                                                    onClick = { onPick(e) },
                                                    onLongClick = {
                                                        mine = mine - e
                                                        Prefs.setCustomEmojis(ctx, mine)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) { Text(e, fontSize = 24.sp) }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.tap_to_use_hold_to_remove), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    )
}

/** Category chooser: shows only the selected category; tapping opens a drop-down list with a "New category" entry. */
@Composable
private fun CategoryDropdown(selected: String, options: List<String>, onSelect: (String) -> Unit, onNew: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(colors.surface)
                .clickable { open = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${categoryEmoji(selected)}  $selected",
                modifier = Modifier.weight(1f), maxLines = 1,
                fontWeight = FontWeight.Medium, color = colors.onSurface
            )
            Text("▾", fontSize = 18.sp, color = colors.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${categoryEmoji(c)}  $c") },
                    onClick = { onSelect(c); open = false }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("➕  ${stringResource(R.string.new_category)}", color = colors.primary, fontWeight = FontWeight.Medium) },
                onClick = { open = false; onNew() }
            )
        }
    }
}

/** Create a category: a name and an emoji picked from the popup. */
@Composable
private fun NewCategoryDialog(onCreate: (name: String, emoji: String) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("✨") }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_category)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceVariant)
                        .clickable { picking = true },
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = 26.sp) }
                OutlinedTextField(
                colors = quietFieldColors(),
                    value = name, onValueChange = { name = it.take(24) },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), emoji) }) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
    if (picking) EmojiPopup(onPick = { emoji = it; picking = false }, onDismiss = { picking = false })
}

/** Add a quote, full page inside the app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddQuotePage(editing: Quote? = null) {
    val colors = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(ctx).quotes() }

    var field by remember { mutableStateOf(TextFieldValue(editing?.text ?: "")) }
    var author by remember { mutableStateOf(editing?.author ?: "") }
    var category by remember { mutableStateOf(editing?.category ?: categories.first()) }
    // choices: the built-in ones, the ones the user created, and any that came with imported quotes
    val usedCategories by remember { dao.observeCategories() }.collectAsState(initial = emptyList())
    var created by remember { mutableStateOf(Prefs.customCategories(ctx)) }
    val options = (categories + created.map { it.first } + usedCategories + listOfNotNull(editing?.category)).distinct()
    var showNewCategory by remember { mutableStateOf(false) }
    var showEmojis by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var imagePath by remember { mutableStateOf(editing?.imagePath) }
    val pickPicture = androidx.activity.compose.rememberLauncherForActivityResult(remember { PickPersistableImage() }) { uri ->
        if (uri != null) scope.launch { ImageStore.keep(ctx, uri)?.let { imagePath = it } }
    }
    LaunchedEffect(saved) { if (saved) { delay(1000); Nav.close() } }
    val text = field.text

    fun insert(emoji: String) {
        val sel = field.selection
        val updated = field.text.replaceRange(sel.min, sel.max, emoji)
        field = TextFieldValue(updated, TextRange(sel.min + emoji.length))
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader(
            stringResource(if (editing == null) R.string.new_entry else R.string.edit_quote),
            stringResource(if (editing == null) R.string.add_to_smrti else R.string.change_it_here)
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Category: shows only the chosen one, opens a list
            CategoryDropdown(
                selected = category, options = options,
                onSelect = { category = it }, onNew = { showNewCategory = true }
            )

            Spacer(Modifier.height(14.dp))

            // Writing surface: you type straight into a calm card, no box lines
            val writeShape = RoundedCornerShape(20.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(writeShape)
                    .background(colors.surface)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                TextField(
                    value = field, onValueChange = { field = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.quote_motivation_or_dialogue),
                            fontFamily = FontFamily.Serif, fontSize = 20.sp, color = colors.onSurfaceVariant
                        )
                    },
                    textStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 20.sp, lineHeight = 30.sp),
                    minLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showEmojis = true }) { Text("😊  ${stringResource(R.string.add_emoji)}") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.characters_count, text.length),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Picture for this quote (optional)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
                    imagePath?.let { Thumbnail(imageModel(it), Modifier.fillMaxSize(), sizePx = 240) } ?: Text("🖼️", fontSize = 28.sp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.quote_picture_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(if (imagePath == null) R.string.quote_picture_hint_none else R.string.quote_picture_hint_set),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { pickPicture.launch(arrayOf("image/*")) }) {
                            Text(stringResource(if (imagePath == null) R.string.choose_picture else R.string.change_picture))
                        }
                        if (imagePath != null) TextButton(onClick = { imagePath = null }) { Text(stringResource(R.string.remove_picture)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            enabled = text.isNotBlank(),
            onClick = {
                if (editing == null) {
                    val quote = Quote(text = text.trim(), author = author.trim(), category = category, isCustom = true, imagePath = imagePath)
                    scope.launch { dao.insert(quote) }
                    saved = true
                } else {
                    scope.launch {
                        try {
                            dao.update(editing.copy(text = text.trim(), author = author.trim(), category = category, imagePath = imagePath))
                            saved = true
                        } catch (_: android.database.sqlite.SQLiteConstraintException) {
                            // the same text and author already exist as another quote
                            Toast.makeText(ctx, ctx.getString(R.string.quote_exists), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 56.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.notif_save), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (showEmojis) EmojiPopup(onPick = { insert(it) }, onDismiss = { showEmojis = false })
    if (showNewCategory) NewCategoryDialog(
        onCreate = { name, emoji ->
            val existing = options.firstOrNull { it.equals(name, ignoreCase = true) }
            if (existing != null) {
                category = existing
            } else {
                created = created + (name to emoji)
                Prefs.setCustomCategories(ctx, created)
                CategoryIcons.custom = created.toMap()
                category = name
            }
            showNewCategory = false
        },
        onDismiss = { showNewCategory = false }
    )
    if (saved) ClosingMoment("🌟", stringResource(R.string.saved_title), stringResource(R.string.saved_subtitle))
    }
}
