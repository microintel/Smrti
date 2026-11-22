package com.smrti.app.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smrti.app.data.AppDatabase
import com.smrti.app.data.Prefs
import com.smrti.app.data.Quote
import androidx.compose.material3.TextButton
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

internal fun categoryEmoji(category: String) = when (category) {
    "Quote" -> "💬"
    "Motivation" -> "🔥"
    "Dialogue" -> "🎬"
    else -> "✨"
}

// Quick-insert emoji, grouped. The phone keyboard's own emoji work in the field too.
private val emojiGroups = listOf(
    "😊" to listOf("😊", "😄", "😍", "🥰", "😎", "🤩", "😌", "🙂", "😉", "🤔", "😢", "😴", "🥳", "🤗", "😇", "🙏"),
    "❤️" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💖", "💫", "💕", "💞", "😘", "🫶", "👏", "👍"),
    "🔥" to listOf("🔥", "💪", "⚡", "🚀", "🏆", "🎯", "🥇", "⭐", "🌟", "✨", "💯", "📈", "🧠", "💡", "✅", "🎉"),
    "🌿" to listOf("🌿", "🌸", "🌻", "🌈", "☀️", "🌙", "⭐", "🌊", "🍃", "🌳", "🦋", "🕊️", "🏔️", "🌅", "🍀", "🪔"),
    "🎬" to listOf("🎬", "🎭", "🎤", "🎧", "🎵", "📖", "✍️", "📝", "🕰️", "⏳", "🗝️", "🔔", "🧭", "🪞", "🕯️", "💭")
)

/** Add a quote, full page inside the app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddQuotePage() {
    val colors = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(ctx).quotes() }

    var field by remember { mutableStateOf(TextFieldValue("")) }
    var author by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.first()) }
    var group by remember { mutableIntStateOf(0) }
    var mine by remember { mutableStateOf(Prefs.customEmojis(ctx)) }
    var newEmoji by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { delay(1000); Nav.close() } }
    val myTab = emojiGroups.size // the last tab is the user's own emojis
    val text = field.text

    fun addMine() {
        val added = extractEmojis(newEmoji).filter { it !in mine }.distinct()
        if (added.isNotEmpty()) {
            mine = (mine + added).takeLast(80)
            Prefs.setCustomEmojis(ctx, mine)
        }
        newEmoji = ""
    }

    fun insert(emoji: String) {
        val sel = field.selection
        val updated = field.text.replaceRange(sel.min, sel.max, emoji)
        field = TextFieldValue(updated, TextRange(sel.min + emoji.length))
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader("New entry", "Add to Smrti")

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Live preview, styled like the reminder card
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceVariant)
                    .height(IntrinsicSize.Min)
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(colors.primary))
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "${categoryEmoji(category)}  $category",
                        style = MaterialTheme.typography.labelLarge, color = colors.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text.ifBlank { "Your words appear here…" },
                        fontFamily = FontFamily.Serif, fontSize = 19.sp, lineHeight = 28.sp,
                        color = if (text.isBlank()) colors.onSurfaceVariant else colors.onSurface
                    )
                    if (author.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("— ${author.trim()}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Category
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c ->
                    val on = category == c
                    val pill = RoundedCornerShape(50)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(pill)
                            .background(if (on) colors.primary else colors.surfaceVariant)
                            .border(1.dp, if (on) colors.primary else colors.outline, pill)
                            .clickable { category = c },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${categoryEmoji(c)} $c", fontSize = 13.sp, maxLines = 1,
                            fontWeight = FontWeight.Medium,
                            color = if (on) colors.onPrimary else colors.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Text
            OutlinedTextField(
                value = field, onValueChange = { field = it },
                label = { Text("Quote, motivation or dialogue") },
                minLines = 3, shape = RoundedCornerShape(16.dp),
                supportingText = { Text("${text.length} characters", color = colors.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth()
            )

            // Emoji strip
            Spacer(Modifier.height(4.dp))
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
            Spacer(Modifier.height(6.dp))
            if (group < myTab) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceVariant)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    emojiGroups[group].second.forEach { e ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable { insert(e) },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 24.sp) }
                    }
                }
            } else {
                // your own emojis: add from your keyboard, tap to use, hold to remove
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newEmoji, onValueChange = { newEmoji = it },
                            placeholder = { Text("Type or paste emoji") },
                            singleLine = true, shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            enabled = extractEmojis(newEmoji).isNotEmpty(),
                            onClick = { addMine() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(56.dp)
                        ) { Text("Add") }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (mine.isEmpty()) {
                        Text(
                            "Open your keyboard's emoji, pick any, then tap Add. They'll be saved here.",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                        )
                    } else {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            mine.forEach { e ->
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            onClick = { insert(e) },
                                            onLongClick = {
                                                mine = mine - e
                                                Prefs.setCustomEmojis(ctx, mine)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) { Text(e, fontSize = 24.sp) }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Tap to use · hold to remove", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Author
            OutlinedTextField(
                value = author, onValueChange = { author = it },
                label = { Text("Author / character / movie (optional)") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = colors.onSurfaceVariant) },
                singleLine = true, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        Button(
            enabled = text.isNotBlank(),
            onClick = {
                val quote = Quote(text = text.trim(), author = author.trim(), category = category, isCustom = true)
                scope.launch { dao.insert(quote) }
                saved = true
            },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(56.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (saved) ClosingMoment("🌟", "Saved", "Added to your collection")
    }
}
