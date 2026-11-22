package com.smrti.app.ui

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.smrti.app.R
import com.smrti.app.data.AppDatabase
import com.smrti.app.data.Prefs
import com.smrti.app.data.Quote
import com.smrti.app.data.formatInterval
import com.smrti.app.notify.Notifier
import com.smrti.app.notify.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

private val presets = listOf(1, 10, 60, 300)

private fun timeUntil(target: Long, now: Long): String {
    val minutes = ((target - now) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "less than a minute"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.get(ctx) }
    val quoteCount by remember { db.quotes().observeCount() }.collectAsState(initial = 0)
    val images by remember { db.images().observeAll() }.collectAsState(initial = emptyList())

    var preview by remember { mutableStateOf<Quote?>(null) }
    var enabled by remember { mutableStateOf(Prefs.isEnabled(ctx)) }
    var interval by remember { mutableIntStateOf(Prefs.intervalMinutes(ctx)) }
    var nextAt by remember { mutableLongStateOf(Prefs.nextAt(ctx)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var allowed by remember { mutableStateOf(Notifier.canNotify(ctx)) }
    var showCustom by remember { mutableStateOf(false) }

    LaunchedEffect(quoteCount > 0) {
        if (preview == null) preview = db.quotes().random()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            nextAt = Prefs.nextAt(ctx)
            now = System.currentTimeMillis()
        }
    }
    LifecycleResumeEffect(Unit) {
        allowed = Notifier.canNotify(ctx)
        nextAt = Prefs.nextAt(ctx)
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }

    fun applyInterval(minutes: Int) {
        interval = minutes
        Prefs.setIntervalMinutes(ctx, minutes)
        if (enabled) {
            Scheduler.scheduleNext(ctx)
            nextAt = Prefs.nextAt(ctx)
            now = System.currentTimeMillis()
        }
    }

    val colors = MaterialTheme.colorScheme
    val customSelected = interval !in presets

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.smrti_mark), contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Text("Smrti", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))

        // Quote
        Panel {
            Crossfade(targetState = preview, label = "quote") { q ->
                if (q == null) {
                    EmptyState(
                        emoji = "✨",
                        title = "Nothing to remember yet",
                        message = "Add a quote and it will show up here and in your reminders.",
                        actionLabel = "Add a quote",
                        verticalPadding = 8.dp
                    ) { Nav.open(Page.AddQuote) }
                } else {
                    Column {
                        Text(
                            q.category,
                            style = MaterialTheme.typography.labelLarge, color = colors.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(q.text, fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 33.sp)
                        if (q.author.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("— ${q.author}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { scope.launch { preview = db.quotes().random() } }) {
                    Text("Show another")
                }
            }
        }

        if (!allowed) {
            Spacer(Modifier.height(16.dp))
            Panel {
                Text("Notifications are blocked", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Smrti can't remind you until you allow them.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedButton(
                    onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    },
                    border = BorderStroke(1.dp, colors.primary)
                ) { Text("Turn on notifications") }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Reminders
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reminders", style = MaterialTheme.typography.titleLarge)
                    val status = when {
                        !enabled -> "Off"
                        nextAt > now -> "Next in ${timeUntil(nextAt, now)} · ${DateFormat.getTimeFormat(ctx).format(Date(nextAt))}"
                        else -> "Starting…"
                    }
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    Prefs.setEnabled(ctx, it)
                    if (it) Scheduler.scheduleNext(ctx) else Scheduler.cancel(ctx)
                    nextAt = Prefs.nextAt(ctx)
                    now = System.currentTimeMillis()
                })
            }

            Spacer(Modifier.height(20.dp))
            Text("Remind me every", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.take(3).forEach { m ->
                    OptionTile(formatInterval(m), interval == m, Modifier.weight(1f)) { applyInterval(m) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionTile(formatInterval(presets[3]), interval == presets[3], Modifier.weight(1f)) {
                    applyInterval(presets[3])
                }
                OptionTile(
                    if (customSelected) "Custom · ${formatInterval(interval)}" else "Custom…",
                    customSelected, Modifier.weight(2f)
                ) { showCustom = !showCustom }
            }
            // typed right here in the panel, no pop-up
            if (showCustom) {
                Spacer(Modifier.height(14.dp))
                CustomIntervalInline(
                    initialMinutes = interval,
                    onCancel = { showCustom = false },
                    onConfirm = { applyInterval(it); showCustom = false }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    val sent = Notifier.sendNext(ctx)
                    withContext(Dispatchers.Main) {
                        val msg = when {
                            sent -> "Reminder sent"
                            !Notifier.canNotify(ctx) -> "Notifications are blocked"
                            else -> "Nothing to send. Add a quote or turn a category on."
                        }
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Send a reminder now", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }

        Spacer(Modifier.height(16.dp))
        Text(
            "$quoteCount quotes · ${images.size} images",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CustomIntervalInline(initialMinutes: Int, onCancel: () -> Unit, onConfirm: (Int) -> Unit) {
    var hours by remember { mutableStateOf(initialMinutes >= 60 && initialMinutes % 60 == 0) }
    var value by remember { mutableStateOf((if (hours) initialMinutes / 60 else initialMinutes).toString()) }
    val minutes = value.toIntOrNull()?.times(if (hours) 60 else 1)
    val valid = minutes != null && minutes in Prefs.MIN_INTERVAL..Prefs.MAX_INTERVAL

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.filter(Char::isDigit).take(5) },
            label = { Text("Every") },
            singleLine = true,
            isError = value.isNotEmpty() && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !hours, onClick = { hours = false }, label = { Text("Minutes") })
            FilterChip(selected = hours, onClick = { hours = true }, label = { Text("Hours") })
        }
        Text(
            "Anything from 1 minute to 7 days",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                enabled = valid,
                onClick = { onConfirm(minutes!!) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) { Text("Set interval") }
        }
    }
}
