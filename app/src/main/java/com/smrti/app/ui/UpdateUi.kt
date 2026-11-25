package com.smrti.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.smrti.app.R
import com.smrti.app.data.UpdateInfo
import com.smrti.app.data.VersionCheck
import com.smrti.app.data.VersionGap
import com.smrti.app.data.Updater
import kotlinx.coroutines.launch

/** Checks GitHub when the app opens (at most every 6 hours) and again when it comes back to the front. */
@Composable
fun UpdateAutoCheck() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Updater.check(ctx) }
    LifecycleResumeEffect(Unit) {
        Updater.onResume(ctx)
        onPauseOrDispose { }
    }
}

/** What to do about the update, based on how far the published version is from the installed one. */
@Composable
private fun suggestion(check: VersionCheck?): String? = when (check?.gap) {
    VersionGap.Major -> stringResource(R.string.update_suggest_major)
    VersionGap.Minor -> stringResource(R.string.update_suggest_minor)
    VersionGap.Patch -> stringResource(R.string.update_suggest_patch)
    VersionGap.InstalledNewer -> stringResource(R.string.update_suggest_newer)
    else -> null
}

@Composable
private fun notesText(info: UpdateInfo): String = info.notes.take(3).joinToString("\n") { "• $it" }

/** The "new version" card on Home. Shows nothing unless an update is waiting. */
@Composable
fun UpdateBanner() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val info = when (val s = Updater.state) {
        is Updater.State.Available -> s.info
        is Updater.State.Downloading -> s.info
        is Updater.State.Ready -> s.info
        else -> return
    }
    if (Updater.state is Updater.State.Available && Updater.dismissedVersion == info.version) return

    Panel(Modifier.padding(bottom = 16.dp)) {
        Text(stringResource(R.string.update_available_title, info.version), style = MaterialTheme.typography.titleMedium)
        Updater.lastCheck?.let { c ->
            Text(
                stringResource(R.string.update_versions, c.installed, c.published),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            suggestion(c)?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium,
                    color = if (c.gap == VersionGap.Major) colors.primary else colors.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        if (info.notes.isNotEmpty()) {
            Text(
                notesText(info), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        when (val s = Updater.state) {
            is Updater.State.Downloading -> {
                LinearProgressIndicator(
                    progress = { s.percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                Text(
                    stringResource(R.string.update_downloading, s.percent),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            is Updater.State.Ready -> {
                Row(Modifier.padding(top = 12.dp)) {
                    Button(onClick = { Updater.installAgain(ctx) }) { Text(stringResource(R.string.update_install)) }
                }
            }
            else -> Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { Updater.downloadAndInstall(ctx, info) } }) {
                    Text(stringResource(R.string.update_now))
                }
                TextButton(onClick = { Updater.dismiss(info.version) }) { Text(stringResource(R.string.update_later)) }
            }
        }
    }
}

/** Settings row: current status plus a "Check for updates" / "Update" button. */
@Composable
fun UpdateSettingsRow() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Updater.state
    val check = Updater.lastCheck
    val status = when (s) {
        is Updater.State.Checking -> stringResource(R.string.update_checking)
        is Updater.State.UpToDate ->
            if (check?.gap == VersionGap.InstalledNewer) stringResource(R.string.update_suggest_newer)
            else stringResource(R.string.update_up_to_date)
        is Updater.State.Available -> stringResource(R.string.update_available_title, s.info.version)
        is Updater.State.Downloading -> stringResource(R.string.update_downloading, s.percent)
        is Updater.State.Ready -> stringResource(R.string.update_ready)
        is Updater.State.Failed -> stringResource(R.string.update_failed)
        else -> stringResource(R.string.update_row_sub)
    }
    // installed vs published, and what to do about it (only once a check has finished)
    val versions = if (check != null && (s is Updater.State.Available || s is Updater.State.UpToDate))
        stringResource(R.string.update_versions, check.installed, check.published) else null
    val advice = if (s is Updater.State.Available) suggestion(check) else null
    val subtitle = listOfNotNull(status, versions, advice).joinToString("\n")
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.update_row_title), style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (s) {
            is Updater.State.Available -> TextButton(onClick = { scope.launch { Updater.downloadAndInstall(ctx, s.info) } }) {
                Text(stringResource(R.string.update_now))
            }
            is Updater.State.Ready -> TextButton(onClick = { Updater.installAgain(ctx) }) {
                Text(stringResource(R.string.update_install))
            }
            is Updater.State.Checking, is Updater.State.Downloading -> {}
            else -> TextButton(onClick = { scope.launch { Updater.check(ctx, force = true) } }) {
                Text(stringResource(R.string.update_check))
            }
        }
    }
}
