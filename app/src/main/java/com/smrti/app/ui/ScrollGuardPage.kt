package com.smrti.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import android.net.Uri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxHeight
import com.smrti.app.data.GuardStats
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.smrti.app.R
import com.smrti.app.data.Prefs
import com.smrti.app.data.formatInterval
import com.smrti.app.notify.Notifier
import com.smrti.app.notify.ScrollGuardService

/** Scroll Guard: pick the apps, the time limit, the early heads-up and the message. */
@Composable
fun ScrollGuardPage() {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme

    var popupOk by remember { mutableStateOf(ScrollGuardService.canShowPopup(ctx)) }
    var clock by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); clock++ } }
    var serviceOn by remember { mutableStateOf(ScrollGuardService.hasUsageAccess(ctx)) }
    var notifyOk by remember { mutableStateOf(Notifier.canNotify(ctx)) }
    var enabled by remember { mutableStateOf(Prefs.guardEnabled(ctx)) }
    var apps by remember { mutableStateOf(Prefs.guardApps(ctx)) }
    val limit0 = Prefs.guardLimitMinutes(ctx)
    var hours by remember { mutableStateOf((limit0 / 60).toString()) }
    var mins by remember { mutableStateOf((limit0 % 60).toString()) }
    var limit by remember { mutableStateOf(limit0) }
    var warn by remember { mutableStateOf(Prefs.guardWarnMinutes(ctx)) }
    var message by remember { mutableStateOf(Prefs.guardMessage(ctx)) }

    LifecycleResumeEffect(Unit) {
        serviceOn = ScrollGuardService.hasUsageAccess(ctx)
        notifyOk = Notifier.canNotify(ctx)
        popupOk = ScrollGuardService.canShowPopup(ctx)
        ScrollGuardService.sync(ctx)
        onPauseOrDispose { }
    }

    fun saveLimit(h: String, m: String) {
        val total = (h.toIntOrNull() ?: 0) * 60 + (m.toIntOrNull() ?: 0)
        if (total >= 1) {
            limit = total.coerceAtMost(24 * 60)
            Prefs.setGuardLimitMinutes(ctx, limit)
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(stringResource(R.string.guard_title), stringResource(R.string.guard_subtitle))
        Column(
            Modifier.weight(1f).fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(stringResource(R.string.guard_intro), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            // 1. the phone-level permission
            Panel {
                Text(stringResource(R.string.guard_access_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (serviceOn) R.string.guard_access_on else R.string.guard_access_off),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                QuietButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text(stringResource(if (serviceOn) R.string.open else R.string.guard_access_enable)) }
                if (!notifyOk) {
                    Text(
                        stringResource(R.string.guard_notif_off),
                        style = MaterialTheme.typography.bodySmall, color = colors.error,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Panel {
                Text(stringResource(R.string.guard_popup_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (popupOk) R.string.guard_popup_on else R.string.guard_popup_off),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                QuietButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text(stringResource(if (popupOk) R.string.open else R.string.guard_access_enable)) }
            }

            // today's numbers; they start from 0 every day
            SectionLabel(stringResource(R.string.guard_today))
            Panel {
                val liveTick = clock // re-reads the numbers every second
                var totalMs = 0L
                var totalWarn = 0
                ScrollGuardService.APPS.forEach { app ->
                    val ms = GuardStats.usageMs(ctx, app.pkg)
                    val w = GuardStats.warnings(ctx, app.pkg)
                    totalMs += ms
                    totalWarn += w
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(guardDuration(ctx, ms), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.guard_warnings_n, w),
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.guard_total_today), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(guardDuration(ctx, totalMs), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.guard_warnings_n, totalWarn),
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                        )
                    }
                }
                Text(
                    stringResource(R.string.guard_today_sub),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            Panel {
                GuardRow(stringResource(R.string.guard_switch), stringResource(R.string.guard_switch_sub)) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it; Prefs.setGuardEnabled(ctx, it); ScrollGuardService.sync(ctx) })
                }
            }

            SectionLabel(stringResource(R.string.guard_apps))
            Panel {
                ScrollGuardService.APPS.forEach { app ->
                    GuardRow(app.label, null) {
                        Switch(checked = app.pkg in apps, onCheckedChange = { on ->
                            apps = if (on) apps + app.pkg else apps - app.pkg
                            Prefs.setGuardApps(ctx, apps)
                        })
                    }
                }
            }

            SectionLabel(stringResource(R.string.guard_limit))
            Panel {
                Text(stringResource(R.string.guard_limit_sub), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hours, onValueChange = { v -> hours = v.filter(Char::isDigit).take(2); saveLimit(hours, mins) },
                        label = { Text(stringResource(R.string.guard_hours)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mins, onValueChange = { v -> mins = v.filter(Char::isDigit).take(3); saveLimit(hours, mins) },
                        label = { Text(stringResource(R.string.guard_minutes)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    stringResource(R.string.guard_limit_now, formatInterval(ctx, limit)),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            SectionLabel(stringResource(R.string.guard_warn))
            Panel {
                Text(stringResource(R.string.guard_warn_sub), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(0, 1, 5, 10).forEach { m ->
                        OptionTile(
                            if (m == 0) stringResource(R.string.guard_warn_none) else formatInterval(ctx, m),
                            warn == m, Modifier.weight(1f)
                        ) { warn = m; Prefs.setGuardWarnMinutes(ctx, m) }
                    }
                }
            }

            SectionLabel(stringResource(R.string.guard_message_label))
            Panel {
                OutlinedTextField(
                    value = message, onValueChange = { message = it.take(140); Prefs.setGuardMessage(ctx, message) },
                    placeholder = { Text(stringResource(R.string.guard_default_message)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4
                )
                Text(
                    stringResource(R.string.guard_message_sub),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuardRow(title: String, subtitle: String?, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

private fun guardDuration(ctx: android.content.Context, ms: Long): String =
    if (ms < 60_000L) ctx.getString(R.string.guard_sec_short, (ms / 1000).toInt())
    else formatInterval(ctx, (ms / 60_000L).toInt())
