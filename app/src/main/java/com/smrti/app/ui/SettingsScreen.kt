package com.smrti.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.key
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.smrti.app.R
import com.smrti.app.data.AppDatabase
import com.smrti.app.data.Prefs
import com.smrti.app.data.formatClock
import com.smrti.app.notify.Notifier
import com.smrti.app.notify.Scheduler

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val categories by remember { db.quotes().observeCategories() }.collectAsState(initial = emptyList())

    var disabled by remember { mutableStateOf(Prefs.disabledCategories(ctx)) }
    var useImages by remember { mutableStateOf(Prefs.useImages(ctx)) }
    var largeImage by remember { mutableStateOf(Prefs.largeImage(ctx)) }
    var quietOn by remember { mutableStateOf(Prefs.quietOn(ctx)) }
    var quietStart by remember { mutableIntStateOf(Prefs.quietStart(ctx)) }
    var quietEnd by remember { mutableIntStateOf(Prefs.quietEnd(ctx)) }
    var allowed by remember { mutableStateOf(Notifier.canNotify(ctx)) }
    var exact by remember { mutableStateOf(Scheduler.canScheduleExact(ctx)) }
    var editingTime by remember { mutableStateOf<String?>(null) } // "start" | "end"
    var themeMode by remember { mutableStateOf(ThemeState.mode) }

    LifecycleResumeEffect(Unit) {
        allowed = Notifier.canNotify(ctx)
        exact = Scheduler.canScheduleExact(ctx)
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTitle("Settings")

        SectionLabel("Appearance")
        Panel {
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Prefs.THEME_SYSTEM to "System", Prefs.THEME_LIGHT to "Light", Prefs.THEME_DARK to "Dark").forEach { (mode, label) ->
                    OptionTile(label, themeMode == mode, Modifier.weight(1f)) {
                        themeMode = mode
                        ThemeState.mode = mode
                        Prefs.setThemeMode(ctx, mode)
                    }
                }
            }
        }

        SectionLabel("Content")
        Panel {
        categories.forEach { category ->
            SettingRow(category, "Include in reminders") {
                Switch(checked = category !in disabled, onCheckedChange = { on ->
                    val updated = if (on) disabled - category else disabled + category
                    disabled = updated
                    Prefs.setDisabledCategories(ctx, updated)
                })
            }
        }
        SettingRow("Show images", "Attach one of your images") {
            Switch(checked = useImages, onCheckedChange = {
                useImages = it
                Prefs.setUseImages(ctx, it)
            })
        }
        SettingRow("Large image banner", "Show the picture in the notification without expanding it") {
            Switch(checked = largeImage, onCheckedChange = {
                largeImage = it
                Prefs.setLargeImage(ctx, it)
            })
        }
        }

        SectionLabel("Quiet hours")
        Panel {
        SettingRow("Pause overnight", "No reminders in this window") {
            Switch(checked = quietOn, onCheckedChange = {
                quietOn = it
                Prefs.setQuietOn(ctx, it)
            })
        }
        if (quietOn) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { editingTime = if (editingTime == "start") null else "start" }) {
                    Text("From ${formatClock(quietStart)}")
                }
                OutlinedButton(onClick = { editingTime = if (editingTime == "end") null else "end" }) {
                    Text("Until ${formatClock(quietEnd)}")
                }
            }
            // set the time right here, no pop-up
            editingTime?.let { which ->
                key(which) {
                    InlineTimeEditor(
                        label = if (which == "start") "Pause from" else "Resume at",
                        initialMinutes = if (which == "start") quietStart else quietEnd,
                        onCancel = { editingTime = null },
                        onSet = { minutes ->
                            if (which == "start") { quietStart = minutes; Prefs.setQuietStart(ctx, minutes) }
                            else { quietEnd = minutes; Prefs.setQuietEnd(ctx, minutes) }
                            editingTime = null
                        }
                    )
                }
            }
        }
        }

        SectionLabel("Delivery")
        Panel {
        SettingRow("Notifications", if (allowed) "On" else "Off · reminders can't show") {
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                )
            }) { Text("Open") }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            SettingRow(
                "Precise timing",
                if (exact) "On" else "Off · short intervals may arrive a few minutes late"
            ) {
                if (!exact) {
                    TextButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                        )
                    }) { Text("Allow") }
                }
            }
        }
        }

        SectionLabel("About")
        Panel {
            SettingRow("About Smrti", "Version ${appVersion(ctx)} · Developed by Microintel") {
                TextButton(onClick = { Nav.open(Page.About) }) { Text("Open") }
            }
        }
    }

}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

private fun appVersion(ctx: Context): String =
    try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { null } ?: "1.0"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineTimeEditor(label: String, initialMinutes: Int, onCancel: () -> Unit, onSet: (Int) -> Unit) {
    val ctx = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = DateFormat.is24HourFormat(ctx)
    )
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        TimeInput(state = state)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = { onSet(state.hour * 60 + state.minute) }) { Text("Set") }
        }
    }
}

/** About, as a full page inside the app. */
@Composable
fun AboutPage() {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val muted = colors.onSurfaceVariant
    Column(Modifier.fillMaxSize()) {
        PageHeader("About")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(R.drawable.smrti_mark), contentDescription = "Smrti", modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(14.dp))
            Text("Smrti", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("Version ${appVersion(ctx)}", style = MaterialTheme.typography.bodyMedium, color = muted)
            Spacer(Modifier.height(16.dp))
            Text(
                "Smrti (स्मृति) means memory. Small reminders to keep what matters close.",
                style = MaterialTheme.typography.bodyLarge, color = muted, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
            Panel {
                Text("What it does", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sends you a quote, a bit of motivation or a dialogue at the interval you choose, " +
                        "with one of your own pictures. Reminders pop up on your screen, show on the lock screen, " +
                        "and tapping one opens it here.",
                    style = MaterialTheme.typography.bodyMedium, color = muted
                )
            }

            Spacer(Modifier.height(32.dp))
            Text("Developed by", style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.height(10.dp))
            // the logo is light-coloured, so it always sits on a dark chip (readable in both themes)
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF111111))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Image(
                    painterResource(R.drawable.microintel_logo), contentDescription = "Microintel",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(48.dp).aspectRatio(368f / 120f)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
