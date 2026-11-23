package com.smrti.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smrti.app.R
import com.smrti.app.data.Prefs

/** What a first-time user sees: why each permission is asked, before Android asks for it. */
@Composable
fun OnboardingPage() {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Nav.close() }
    fun start() {
        Prefs.setOnboarded(ctx)
        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else Nav.close()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🪔", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.onb_title), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.onb_subtitle),
                style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            PermissionCard("🔔", stringResource(R.string.onb_notif_title), stringResource(R.string.onb_notif_body))
            Spacer(Modifier.height(12.dp))
            PermissionCard("⏰", stringResource(R.string.onb_exact_title), stringResource(R.string.onb_exact_body))
            Spacer(Modifier.height(12.dp))
            PermissionCard("🖼️", stringResource(R.string.onb_photos_title), stringResource(R.string.onb_photos_body))
            Spacer(Modifier.height(12.dp))
            PermissionCard("🔋", stringResource(R.string.onb_battery_title), stringResource(R.string.onb_battery_body))
        }
        Button(
            onClick = { start() },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 56.dp)
        ) { Text(stringResource(R.string.onb_start), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun PermissionCard(emoji: String, title: String, body: String) {
    Panel {
        Row(verticalAlignment = Alignment.Top) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** Phones from some brands stop background apps to save battery; this page helps switch that off for Smrti. */
private enum class Brand { XIAOMI, OPPO, VIVO, SAMSUNG, OTHER }

private fun brand(): Brand = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" -> Brand.XIAOMI
    "oppo", "realme", "oneplus" -> Brand.OPPO
    "vivo", "iqoo" -> Brand.VIVO
    "samsung" -> Brand.SAMSUNG
    else -> Brand.OTHER
}

private fun autoStartScreens(brand: Brand): List<ComponentName> = when (brand) {
    Brand.XIAOMI -> listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    )
    Brand.OPPO -> listOf(
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
    )
    Brand.VIVO -> listOf(
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
    )
    Brand.SAMSUNG -> listOf(
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
    )
    Brand.OTHER -> emptyList()
}

private fun tryStart(ctx: Context, intent: Intent): Boolean = try {
    ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: Exception) { false }

private fun appInfo(ctx: Context) = tryStart(
    ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
)

/** Opens the brand's own auto-start / background screen if it exists, otherwise Smrti's app info page. */
private fun openAutoStart(ctx: Context) {
    for (component in autoStartScreens(brand())) {
        if (tryStart(ctx, Intent().setComponent(component))) return
    }
    if (!appInfo(ctx)) Toast.makeText(ctx, ctx.getString(R.string.settings_unavailable), Toast.LENGTH_SHORT).show()
}

@Composable
fun ReliabilityPage() {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val tip = when (brand()) {
        Brand.XIAOMI -> R.string.rel_tip_xiaomi
        Brand.OPPO -> R.string.rel_tip_oppo
        Brand.VIVO -> R.string.rel_tip_vivo
        Brand.SAMSUNG -> R.string.rel_tip_samsung
        Brand.OTHER -> R.string.rel_tip_other
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(stringResource(R.string.rel_title), stringResource(R.string.rel_subtitle))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(R.string.rel_intro),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Panel {
                Text(stringResource(R.string.rel_step_battery), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.rel_step_battery_body),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedButton(onClick = {
                    if (!tryStart(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) appInfo(ctx)
                }) { Text(stringResource(R.string.rel_open_battery)) }
            }
            Spacer(Modifier.height(12.dp))
            Panel {
                Text(stringResource(R.string.rel_step_autostart), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(tip),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openAutoStart(ctx) }) { Text(stringResource(R.string.rel_open_autostart)) }
                    OutlinedButton(onClick = { appInfo(ctx) }) { Text(stringResource(R.string.rel_open_appinfo)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
