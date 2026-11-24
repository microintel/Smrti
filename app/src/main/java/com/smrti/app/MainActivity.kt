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
import coil.imageLoader
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
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
import com.smrti.app.ui.reduceMotion
import com.smrti.app.ui.isAppDark
import com.smrti.app.data.Prefs
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.consumeWindowInsets
import com.smrti.app.ui.QuotesScreen
import com.smrti.app.ui.SettingsScreen
import com.smrti.app.ui.SmrtiTheme

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun handleNotificationIntent(intent: Intent?) {
        intent ?: return
        try {
            val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
            val text = intent.getStringExtra(Notifier.EXTRA_TEXT)
            if (!fromHistory && !text.isNullOrBlank()) {
                // start loading the picture now, so it is ready when the reminder page appears
                intent.getStringExtra(Notifier.EXTRA_IMAGE_PATH)?.let { path ->
                    try { imageLoader.enqueue(com.smrti.app.ui.reminderImageRequest(this, path)) } catch (_: Throwable) { }
                }
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
        } catch (_: Throwable) {
            // a bad tap intent must never take the app down; just open normally
        }
    }

    /** Whether the app is showing its dark theme right now (same rule as isAppDark, usable outside Compose). */
    private fun resolveDark(): Boolean = when (ThemeState.mode) {
        Prefs.THEME_LIGHT -> false
        Prefs.THEME_DARK -> true
        else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private var barsDark = true

    /**
     * Status / navigation bar icons: dark icons on the light theme, light icons on the dark theme.
     * Set straight on the window (and again on resume) so nothing can leave white icons on the cream background.
     */
    private fun applySystemBars(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    override fun onResume() {
        super.onResume()
        applySystemBars(barsDark)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // No artificial wait: the splash leaves as soon as the first frame is ready, with a very short fade
        // (and none at all when animations are reduced).
        splash.setOnExitAnimationListener { provider ->
            // Never let the splash animation crash the app (it can fail when launched from a notification)
            var removed = false
            val safeRemove = {
                if (!removed) {
                    removed = true
                    try { provider.remove() } catch (_: Throwable) { }
                }
            }
            try {
                if (reduceMotion) {
                    safeRemove()
                } else {
                    provider.view.animate().alpha(0f).setDuration(140)
                        .withEndAction { safeRemove() }.start()
                    // safety net: the splash is always removed, even if an animation is cancelled
                    Handler(Looper.getMainLooper()).postDelayed({ safeRemove() }, 400)
                }
            } catch (_: Throwable) {
                safeRemove()
            }
        }
        super.onCreate(savedInstanceState)
        ThemeState.mode = Prefs.themeMode(this)
        com.smrti.app.ui.CategoryIcons.custom = Prefs.customCategories(this).toMap()
        barsDark = resolveDark()
        applySystemBars(barsDark)

        val firstRun = !Prefs.isOnboarded(this)
        if (!firstRun && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (savedInstanceState == null) {
            handleNotificationIntent(intent)
            // first run: explain the permissions before Android asks for them (not when opened from a reminder)
            if (firstRun && Nav.page == null) Nav.open(Page.Onboarding)
        }

        setContent {
            // status / navigation bar icons follow the chosen theme
            val dark = isAppDark()
            SideEffect {
                barsDark = dark
                applySystemBars(dark)
            }
            SmrtiTheme { SmrtiRoot() }
        }
    }
}

private data class Tab(@androidx.annotation.StringRes val label: Int, val icon: @Composable () -> Painter)

private val tabs = listOf(
    Tab(R.string.home) { rememberVectorPainter(Icons.Default.Home) },
    Tab(R.string.quotes_title) { painterResource(R.drawable.ic_tab_quote) },
    Tab(R.string.images) { painterResource(R.drawable.ic_tab_image) },
    Tab(R.string.settings) { rememberVectorPainter(Icons.Default.Settings) }
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
                beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                  // the screen runs behind the floating bar; each screen adds this much space at its end
                  androidx.compose.runtime.CompositionLocalProvider(
                    com.smrti.app.ui.LocalNavInset provides (if (imeOpen) 0.dp else NavHeight + NavMargin * 2)
                  ) {
                    // on tablets and in landscape the content keeps a readable width instead of stretching edge to edge
                    Box(
                        Modifier.widthIn(max = com.smrti.app.ui.ContentMaxWidth).fillMaxSize()
                    ) {
                        when (page) {
                            0 -> HomeScreen()
                            1 -> QuotesScreen()
                            2 -> ImagesScreen()
                            else -> SettingsScreen()
                        }
                    }
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
            // lifted off the screen: a soft shadow, and a slightly see-through body so content shows behind it
            .shadow(14.dp, shape, ambientColor = androidx.compose.ui.graphics.Color.Black, spotColor = androidx.compose.ui.graphics.Color.Black)
            .clip(shape)
            .background(colors.surfaceContainerHigh.copy(alpha = 0.94f))
            .heightIn(min = NavHeight)
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
                    .heightIn(min = 48.dp)
                    .semantics { this.selected = active }
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
                    tab.icon(), contentDescription = androidx.compose.ui.res.stringResource(tab.label),
                    tint = if (active) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                AnimatedVisibility(
                    visible = active,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(tab.label),
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
