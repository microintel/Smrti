package com.smrti.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smrti.app.data.AppDatabase
import com.smrti.app.data.Quote
import com.smrti.app.data.QuoteImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QuotesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(ctx).quotes() }
    val quotes by remember { dao.observeAll() }.collectAsState(initial = emptyList())

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenTitle("Quotes", "${quotes.size} saved", Modifier.weight(1f))
                OutlinedButton(onClick = { Nav.open(Page.ImportGuide) }) { Text("Import JSON") }
            }

            if (quotes.isEmpty()) {
                EmptyState(
                    emoji = "💬",
                    title = "No quotes yet",
                    message = "Add your first line, or import a whole list from a file.",
                    actionLabel = "Add a quote",
                    modifier = Modifier.padding(top = 24.dp)
                ) { Nav.open(Page.AddQuote) }
            }

            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 100.dp)) {
                items(quotes, key = { it.id }) { quote ->
                    QuoteRow(quote, onDelete = { scope.launch { dao.delete(quote) } })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { Nav.open(Page.AddQuote) },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Add quote") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun QuoteRow(quote: Quote, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var confirming by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                "${categoryEmoji(quote.category)} ${quote.category}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                fontWeight = FontWeight.Medium
            )
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
            // confirmation happens right here in the list, no pop-up
            if (confirming) {
                Row(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Delete this quote?", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    TextButton(onClick = { confirming = false }) { Text("Cancel") }
                    TextButton(onClick = onDelete) { Text("Delete", color = colors.error) }
                }
            }
        }
        if (!confirming) {
            IconButton(onClick = { confirming = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.onSurfaceVariant)
            }
        }
    }
}
