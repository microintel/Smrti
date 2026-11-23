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
import com.smrti.app.CrashLogger
import androidx.compose.ui.res.stringResource
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
        ScreenTitle(stringResource(R.string.settings))

        SectionLabel(stringResource(R.string.appearance))
        Panel {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Prefs.THEME_SYSTEM to R.string.motion_system, Prefs.THEME_LIGHT to R.string.theme_light, Prefs.THEME_DARK to R.string.theme_dark).forEach { (mode, label) ->
                    OptionTile(stringResource(label), themeMode == mode, Modifier.weight(1f)) {
                        themeMode = mode
                        ThemeState.mode = mode
                        Prefs.setThemeMode(ctx, mode)
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.content))
        Panel {
        categories.forEach { category ->
            SettingRow(category, stringResource(R.string.include_in_reminders)) {
                Switch(checked = category !in disabled, onCheckedChange = { on ->
                    val updated = if (on) disabled - category else disabled + category
                    disabled = updated
                    Prefs.setDisabledCategories(ctx, updated)
                })
            }
        }
        SettingRow(stringResource(R.string.show_images), stringResource(R.string.attach_one)) {
            Switch(checked = useImages, onCheckedChange = {
                useImages = it
                Prefs.setUseImages(ctx, it)
            })
        }
        SettingRow(stringResource(R.string.large_image_banner), stringResource(R.string.large_image_sub)) {
            Switch(checked = largeImage, onCheckedChange = {
                largeImage = it
                Prefs.setLargeImage(ctx, it)
            })
        }
        }

        SectionLabel(stringResource(R.string.quiet_hours))
        Panel {
        SettingRow(stringResource(R.string.pause_overnight), stringResource(R.string.no_reminders_window)) {
            Switch(checked = quietOn, onCheckedChange = {
                quietOn = it
                Prefs.setQuietOn(ctx, it)
            })
        }
        if (quietOn) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { editingTime = if (editingTime == "start") null else "start" }) {
                    Text(stringResource(R.string.from_time, formatClock(quietStart)))
                }
                OutlinedButton(onClick = { editingTime = if (editingTime == "end") null else "end" }) {
                    Text(stringResource(R.string.until_time, formatClock(quietEnd)))
                }
            }
            // set the time right here, no pop-up
            editingTime?.let { which ->
                key(which) {
                    InlineTimeEditor(
                        label = stringResource(if (which == "start") R.string.pause_from else R.string.resume_at),
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

        SectionLabel(stringResource(R.string.delivery))
        Panel {
        SettingRow(stringResource(R.string.rel_row_title), stringResource(R.string.rel_row_sub)) {
            TextButton(onClick = { Nav.open(Page.Reliability) }) { Text(stringResource(R.string.open)) }
        }
        SettingRow(stringResource(R.string.notifications), stringResource(if (allowed) R.string.motion_on else R.string.notif_off_cant_show)) {
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                )
            }) { Text(stringResource(R.string.open)) }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            SettingRow(
                stringResource(R.string.precise_timing),
                stringResource(if (exact) R.string.motion_on else R.string.precise_off)
            ) {
                if (!exact) {
                    TextButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                        )
                    }) { Text(stringResource(R.string.allow)) }
                }
            }
        }
        }

        SectionLabel(stringResource(R.string.about))
        Panel {
            SettingRow(stringResource(R.string.about_smrti), stringResource(R.string.version_by, appVersion(ctx))) {
                TextButton(onClick = { Nav.open(Page.About) }) { Text(stringResource(R.string.open)) }
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
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onSet(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.set)) }
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
        PageHeader(stringResource(R.string.about))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(R.drawable.smrti_mark), contentDescription = stringResource(R.string.app_name), modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.app_name), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.version_n, appVersion(ctx)), style = MaterialTheme.typography.bodyMedium, color = muted)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.smrti_means_memory_small_reminders_to),
                style = MaterialTheme.typography.bodyLarge, color = muted, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
            Panel {
                Text(stringResource(R.string.what_it_does), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.about_what_body),
                    style = MaterialTheme.typography.bodyMedium, color = muted
                )
            }

            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.developed_by), style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.height(10.dp))
            // the logo is light-coloured, so it always sits on a dark chip (readable in both themes)
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF041237))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Image(
                    painterResource(R.drawable.microintel_logo), contentDescription = stringResource(R.string.microintel),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(48.dp).aspectRatio(368f / 120f)
                )
            }
            var crash by remember { mutableStateOf(CrashLogger.read(ctx)) }
            crash?.let { report ->
                Spacer(Modifier.height(24.dp))
                Panel {
                    Text(stringResource(R.string.crash_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.crash_body),
                        style = MaterialTheme.typography.bodyMedium, color = muted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, report.takeLast(20000))
                            ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text(stringResource(R.string.share)) }
                        TextButton(onClick = { CrashLogger.clear(ctx); crash = null }) { Text(stringResource(R.string.clear)) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
