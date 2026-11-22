package com.smrti.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.smrti.app.ui.HomeScreen
import com.smrti.app.ui.ImagesScreen
import com.smrti.app.notify.Notifier
import com.smrti.app.ui.Nav
import com.smrti.app.ui.OpenedReminder
import com.smrti.app.ui.Page
import com.smrti.app.ui.PageHost
import com.smrti.app.ui.ThemeState
import com.smrti.app.ui.isAppDark
import com.smrti.app.data.Prefs
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.consumeWindowInsets
import com.smrti.app.ui.QuotesScreen
import com.smrti.app.ui.SettingsScreen
import com.smrti.app.ui.SmrtiTheme

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun handleNotificationIntent(intent: Intent?) {
        intent ?: return
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        val text = intent.getStringExtra(Notifier.EXTRA_TEXT)
        if (!fromHistory && !text.isNullOrBlank()) {
            // opens as a full page inside the app, not a pop-up
            Nav.open(
                Page.Reminder(
                    OpenedReminder(
                        text = text,
                        author = intent.getStringExtra(Notifier.EXTRA_AUTHOR).orEmpty(),
                        category = intent.getStringExtra(Notifier.EXTRA_CATEGORY).orEmpty(),
                        imagePath = intent.getStringExtra(Notifier.EXTRA_IMAGE_PATH)
                    )
                )
            )
        }
        // consume, so rotating or re-opening doesn't show it again
        intent.removeExtra(Notifier.EXTRA_TEXT)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // keep the logo on screen a moment, then it pops and fades into the app
        var holdSplash = true
        Handler(Looper.getMainLooper()).postDelayed({ holdSplash = false }, 650)
        splash.setKeepOnScreenCondition { holdSplash }
        splash.setOnExitAnimationListener { provider ->
            val icon = provider.iconView
            val root = provider.view
            icon.animate()
                .scaleX(1.15f).scaleY(1.15f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    icon.animate()
                        .scaleX(0.4f).scaleY(0.4f).alpha(0f)
                        .setDuration(320)
                        .setInterpolator(AccelerateInterpolator())
                        .start()
                    root.animate()
                        .alpha(0f)
                        .setDuration(320)
                        .withEndAction { provider.remove() }
                        .start()
                }
                .start()
        }
        super.onCreate(savedInstanceState)
        ThemeState.mode = Prefs.themeMode(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (savedInstanceState == null) handleNotificationIntent(intent)

        setContent {
            // status / navigation bar icons follow the chosen theme
            val dark = isAppDark()
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                )
                onDispose { }
            }
            SmrtiTheme { SmrtiRoot() }
        }
    }
}

private data class Tab(val label: String, val icon: @Composable () -> Painter)

private val tabs = listOf(
    Tab("Home") { rememberVectorPainter(Icons.Default.Home) },
    Tab("Quotes") { painterResource(R.drawable.ic_tab_quote) },
    Tab("Images") { painterResource(R.drawable.ic_tab_image) },
    Tab("Settings") { rememberVectorPainter(Icons.Default.Settings) }
)

private val NavHeight = 64.dp
private val NavMargin = 12.dp

@Composable
private fun SmrtiRoot() {
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    // arriving from a notification lands on Home, behind the reminder page
    val openPage = Nav.page
    LaunchedEffect(openPage) { if (openPage is Page.Reminder) pagerState.scrollToPage(0) }

    // hide the floating bar while the keyboard is open
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            // swipe left / right to change tabs
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(Modifier.fillMaxSize().padding(bottom = if (imeOpen) 0.dp else NavHeight + NavMargin * 2)) {
                    when (page) {
                        0 -> HomeScreen()
                        1 -> QuotesScreen()
                        2 -> ImagesScreen()
                        else -> SettingsScreen()
                    }
                }
            }

            AnimatedVisibility(
                visible = !imeOpen && openPage == null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                FloatingNav(
                    selected = pagerState.currentPage,
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } }
                )
            }

            // full-screen pages (reminder, add quote, import, albums, about) slide in over everything
            PageHost(Modifier.fillMaxSize())
        }
    }
}

/** Floating pill: the selected tab expands to show its label, the others stay as icons. */
@Composable
private fun FloatingNav(selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier
            .padding(bottom = NavMargin)
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outline, shape)
            .height(NavHeight)
            .padding(horizontal = 8.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = index == selected
            val itemShape = RoundedCornerShape(24.dp)
            Row(
                Modifier
                    .height(48.dp)
                    .clip(itemShape)
                    .background(if (active) colors.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) }
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    tab.icon(), contentDescription = tab.label,
                    tint = if (active) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                AnimatedVisibility(
                    visible = active,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    Text(
                        tab.label,
                        color = colors.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
