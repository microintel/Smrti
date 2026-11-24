package com.smrti.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Height the floating bottom bar covers. Screens run behind the bar and add this much space at their end. */
val LocalNavInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 8.dp)
    )
}

/** Flat rounded panel, no outline: it is told apart from the page by its soft fill alone. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        content = content
    )
}

/** Large, obvious choice button. Filled when selected, outlined when not. */
@Composable
fun OptionTile(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) colors.onPrimary else colors.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

/** Fades and lifts an element into place when [visible] turns true. Used to stage the reveal of a reminder. */
@Composable
fun Modifier.reveal(visible: Boolean): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(650, easing = FastOutSlowInEasing),
        label = "reveal"
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 48f
    }
}

/** A friendly empty screen: a softly floating icon, what's missing, and one clear next step. */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    verticalPadding: Dp = 32.dp,
    onAction: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val bob = rememberInfiniteTransition(label = "bob")
    val still = reduceMotion
    val lift by bob.animateFloat(
        initialValue = 0f, targetValue = if (still) 0f else -10f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lift"
    )
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .graphicsLayer { translationY = lift }
                .size(88.dp)
                .clip(CircleShape)
                .background(colors.secondaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 40.sp) }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message, style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant, textAlign = TextAlign.Center
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                modifier = Modifier.heightIn(min = 52.dp)
            ) { Text(actionLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** The warm last beat of a task: a bouncy pop of an emoji and a short line, shown just before closing a page. */
@Composable
fun ClosingMoment(emoji: String, title: String, subtitle: String? = null) {
    val colors = MaterialTheme.colorScheme
    val scale = remember { Animatable(0.4f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            fade.snapTo(1f); scale.snapTo(1f)
        } else {
            launch { fade.animateTo(1f, tween(250)) }
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = fade.value },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 72.sp)
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}


// ---- Quiet controls: soft fills instead of outlines ------------------------------------------------

/** A button with a soft tonal fill and no outline (replaces the outlined button). */
@Composable
fun QuietButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) = androidx.compose.material3.FilledTonalButton(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = shape,
    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    content = content
)

/** A filter chip that is just a soft pill: filled when chosen, faint when not, never outlined. */
@Composable
fun QuietFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    androidx.compose.material3.FilterChip(
        selected = selected, onClick = onClick, label = label,
        border = null,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = c.surfaceVariant,
            selectedContainerColor = c.secondaryContainer,
            selectedLabelColor = c.onSecondaryContainer
        )
    )
}

/** Text-field colours with a soft filled box and no outline (a faint ring only while typing; red when invalid). */
@Composable
fun quietFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    errorBorderColor = MaterialTheme.colorScheme.error,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    errorContainerColor = MaterialTheme.colorScheme.surfaceVariant
)
