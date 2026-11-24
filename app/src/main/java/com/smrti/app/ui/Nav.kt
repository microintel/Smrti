package com.smrti.app.ui

import com.smrti.app.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Widest the content gets on tablets and in landscape. */
val ContentMaxWidth = 720.dp

/** Full-screen pages that open inside the app (instead of pop-up dialogs). */
sealed interface Page {
    data class Reminder(val reminder: OpenedReminder) : Page
    data object AddQuote : Page
    data object ImportGuide : Page
    data object Albums : Page
    data object About : Page
    data class EditQuote(val quote: com.smrti.app.data.Quote) : Page
    data object Onboarding : Page
    data object Reliability : Page
    data object ScrollGuard : Page
}

object Nav {
    var page by mutableStateOf<Page?>(null)
        private set

    fun open(p: Page) { page = p }
    fun close() { page = null }
}

/** Slides the current [Page] in over the whole app, bottom bar included. */
@Composable
fun PageHost(modifier: Modifier = Modifier) {
    val current = Nav.page
    // keep the last page around so it stays visible while it slides out
    var last by remember { mutableStateOf<Page?>(null) }
    if (current != null) last = current

    BackHandler(enabled = current != null) { Nav.close() }

    AnimatedVisibility(
        visible = current != null,
        enter = if (reduceMotion) EnterTransition.None else slideInHorizontally { it / 3 } + fadeIn(),
        exit = if (reduceMotion) ExitTransition.None else slideOutHorizontally { it / 3 } + fadeOut(),
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // swallow touches so nothing underneath reacts
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            // on tablets and in landscape the page stays a readable width, centred
            Box(Modifier.align(Alignment.TopCenter).widthIn(max = ContentMaxWidth).fillMaxSize()) {
                when (val p = last) {
                    is Page.Reminder -> ReminderPage(p.reminder)
                    Page.AddQuote -> AddQuotePage()
                    is Page.EditQuote -> AddQuotePage(editing = p.quote)
                    Page.ImportGuide -> ImportGuidePage()
                    Page.Albums -> AlbumsPage()
                    Page.About -> AboutPage()
                    Page.Onboarding -> OnboardingPage()
                    Page.Reliability -> ReliabilityPage()
                    Page.ScrollGuard -> ScrollGuardPage()
                    null -> {}
                }
            }
        }
    }
}

@Composable
fun PageHeader(title: String, subtitle: String? = null, onBack: () -> Unit = { Nav.close() }) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
