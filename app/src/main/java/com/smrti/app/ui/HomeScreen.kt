package com.smrti.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.smrti.app.data.ImagePool
import coil.size.Size
import coil.request.ImageRequest
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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

/** What the timing drop-down offers (anything else goes through Custom). */
private val intervalChoices = listOf(1, 5, 10, 15, 30, 60, 120, 180, 300, 720, 1440)

private fun timeUntil(ctx: android.content.Context, target: Long, now: Long): String {
    val minutes = ((target - now) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> ctx.getString(R.string.less_than_a_minute)
        minutes < 60 -> ctx.getString(R.string.minutes_short, minutes.toInt())
        else -> ctx.getString(R.string.hours_minutes_short, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}

/**
 * "Next in 12 min · 14:30". It owns its own clock, ticks only while the app is on screen, and only this
 * one line is redrawn, so the rest of Home never recomposes just because time passed.
 */
@Composable
private fun NextStatus(enabled: Boolean, tick: Int) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var nextAt by remember { mutableLongStateOf(Prefs.nextAt(ctx)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tick) {
        nextAt = Prefs.nextAt(ctx)
        now = System.currentTimeMillis()
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                nextAt = Prefs.nextAt(ctx)
                now = System.currentTimeMillis()
                delay(20_000)
            }
        }
    }
    val text = when {
        !enabled -> stringResource(R.string.off)
        nextAt > now -> stringResource(
            R.string.next_in, timeUntil(ctx, nextAt, now), DateFormat.getTimeFormat(ctx).format(Date(nextAt))
        )
        else -> stringResource(R.string.starting)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    // bumped when the schedule changes; only the small status line below listens to it (the page itself never ticks)
    var statusTick by remember { mutableIntStateOf(0) }
    var allowed by remember { mutableStateOf(Notifier.canNotify(ctx)) }
    var showCustom by remember { mutableStateOf(false) }

    LaunchedEffect(quoteCount > 0) {
        if (preview == null) preview = db.quotes().random()
    }
    // the quote's own picture, or a random one from the library (same rule as the notifications)
    var previewImage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(preview?.id, images.isEmpty()) {
        val q = preview
        previewImage = if (q == null || !Prefs.useImages(ctx)) null else try {
            q.imagePath ?: ImagePool.randomSource(ctx)
        } catch (_: Throwable) { null }
    }
    LifecycleResumeEffect(Unit) {
        allowed = Notifier.canNotify(ctx)
        statusTick++
        onPauseOrDispose { }
    }

    fun applyInterval(minutes: Int) {
        interval = minutes
        Prefs.setIntervalMinutes(ctx, minutes)
        if (enabled) {
            Scheduler.scheduleNext(ctx)
            statusTick++
        }
    }

    val colors = MaterialTheme.colorScheme
    val customSelected = interval !in intervalChoices

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + LocalNavInset.current)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.smrti_mark), contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }

        UpdateAutoCheck()
        Spacer(Modifier.height(24.dp))
        UpdateBanner()

        // Quote
        Panel {
            Crossfade(targetState = preview, label = "quote") { q ->
                if (q == null) {
                    EmptyState(
                        emoji = "✨",
                        title = stringResource(R.string.nothing_to_remember_yet),
                        message = stringResource(R.string.add_a_quote_and_it_will),
                        actionLabel = stringResource(R.string.add_a_quote),
                        verticalPadding = 8.dp
                    ) { Nav.open(Page.AddQuote) }
                } else {
                    val picture = previewImage
                    if (picture != null) {
                        // the quote written over its picture
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
                            AsyncImage(
                                model = remember(picture) {
                                    ImageRequest.Builder(ctx).data(imageModel(picture)).size(Size(1080, 1080)).build()
                                },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                onError = { previewImage = null },
                                modifier = Modifier.matchParentSize()
                            )
                            // darkens the picture so the words stay readable
                            Box(
                                Modifier.matchParentSize().background(
                                    Brush.verticalGradient(listOf(Color(0x59000000), Color(0xD9000000)))
                                )
                            )
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = 280.dp).padding(20.dp),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(q.category, style = MaterialTheme.typography.labelLarge, color = Color(0xFFFAB62F))
                                Spacer(Modifier.height(10.dp))
                                Text(q.text, fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 33.sp, color = Color(0xFFF6F1E4))
                                if (q.author.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("— ${q.author}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD5DAE8))
                                }
                            }
                        }
                    } else Column {
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
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                preview?.let { q ->
                    var fav by remember(q.id) { mutableStateOf(q.isFavorite) }
                    IconButton(onClick = {
                        fav = !fav
                        scope.launch { db.quotes().setFavorite(q.id, fav) }
                    }) {
                        Icon(
                            painterResource(if (fav) R.drawable.ic_star else R.drawable.ic_star_outline),
                            contentDescription = stringResource(if (fav) R.string.remove_favorite else R.string.add_favorite),
                            tint = if (fav) colors.primary else colors.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = { scope.launch { preview = db.quotes().random() } }) {
                    Text(stringResource(R.string.show_another))
                }
            }
        }

        if (!allowed) {
            Spacer(Modifier.height(16.dp))
            Panel {
                Text(stringResource(R.string.notifications_are_blocked), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.smrti_can_t_remind_you_until),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                QuietButton(
                    onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    },
                ) { Text(stringResource(R.string.turn_on_notifications)) }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Reminders
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reminders), style = MaterialTheme.typography.titleLarge)
                    NextStatus(enabled, statusTick)
                }
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    Prefs.setEnabled(ctx, it)
                    if (it) Scheduler.scheduleNext(ctx) else Scheduler.cancel(ctx)
                    statusTick++
                })
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.remind_me_every), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            // a drop-down: the timing is rarely changed, so it stays tucked away until needed
            var menuOpen by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceVariant)
                        .clickable { menuOpen = true }
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatInterval(ctx, interval),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (customSelected) Text(
                        stringResource(R.string.custom_dots).trimEnd('…', '.'),
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.ArrowDropDown,
                        contentDescription = null, tint = colors.onSurfaceVariant
                    )
                }
                androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    intervalChoices.forEach { m ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    formatInterval(ctx, m),
                                    color = if (interval == m) colors.primary else colors.onSurface,
                                    fontWeight = if (interval == m) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            onClick = { menuOpen = false; showCustom = false; applyInterval(m) }
                        )
                    }
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                if (customSelected) stringResource(R.string.custom_with, formatInterval(ctx, interval)) else stringResource(R.string.custom_dots),
                                color = if (customSelected) colors.primary else colors.onSurface,
                                fontWeight = if (customSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = { menuOpen = false; showCustom = true }
                    )
                }
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
                            sent -> ctx.getString(R.string.reminder_sent)
                            !Notifier.canNotify(ctx) -> ctx.getString(R.string.notifications_blocked_toast)
                            else -> ctx.getString(R.string.nothing_to_send)
                        }
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text(stringResource(R.string.send_a_reminder_now), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.quotes_images_count, quoteCount, images.size),
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
                colors = quietFieldColors(),
            value = value,
            onValueChange = { value = it.filter(Char::isDigit).take(5) },
            label = { Text(stringResource(R.string.every)) },
            singleLine = true,
            isError = value.isNotEmpty() && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietFilterChip(selected = !hours, onClick = { hours = false }, label = { Text(stringResource(R.string.minutes)) })
            QuietFilterChip(selected = hours, onClick = { hours = true }, label = { Text(stringResource(R.string.hours)) })
        }
        Text(
            stringResource(R.string.anything_from_1_minute_to_7),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Button(
                enabled = valid,
                onClick = { onConfirm(minutes!!) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) { Text(stringResource(R.string.set_interval)) }
        }
    }
}
