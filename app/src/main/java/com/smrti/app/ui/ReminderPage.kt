package com.smrti.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.smrti.app.R
import com.smrti.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/** The reminder the user tapped in the notification. */
data class OpenedReminder(
    val text: String,
    val author: String,
    val category: String,
    val imagePath: String?
)

/** Shows the tapped notification's quote (and picture) as a full page inside the app. */
@Composable
fun ReminderPage(reminder: OpenedReminder) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // the peak: picture, then the words, then the author arrive one after another
    var step by remember { mutableIntStateOf(0) }
    // the end: a warm closing beat before the page leaves
    var ending by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    // my images are files or content:// uris; wait (briefly) for the picture so it doesn't pop in late
    val imagePath = reminder.imagePath?.takeIf { it.startsWith("content://") || File(it).exists() }
    var imageReady by remember { mutableStateOf(imagePath == null) }
    var imageFailed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120)
        withTimeoutOrNull(900) { snapshotFlow { imageReady }.first { it } }
        step = 1
        delay(380); step = 2
        delay(380); step = 3
        delay(300); step = 4
    }
    LaunchedEffect(ending) {
        if (ending) { delay(1100); Nav.close() }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(reminder.category.ifBlank { stringResource(R.string.reminder) })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            if (imagePath != null && !imageFailed) {
                AsyncImage(
                    model = remember(imagePath) { reminderImageRequest(ctx, imagePath) },
                    contentDescription = null,
                    // show the whole picture, never cropped: full width, height follows the photo
                    contentScale = ContentScale.FillWidth,
                    onSuccess = { imageReady = true },
                    onError = { imageReady = true; imageFailed = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        // reserve room while it loads, so the words don't jump down
                        .heightIn(min = if (imageReady) 0.dp else 240.dp)
                        .reveal(step >= 1)
                        .clip(RoundedCornerShape(28.dp))
                )
                Spacer(Modifier.height(24.dp))
            }
            Text(
                reminder.text,
                fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 38.sp,
                modifier = Modifier.reveal(step >= 2)
            )
            if (reminder.author.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "— ${reminder.author}", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant,
                    modifier = Modifier.reveal(step >= 3)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).reveal(step >= 4),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val dao = AppDatabase.get(ctx).quotes()
                        val match = dao.find(reminder.text, reminder.author)
                        if (match != null) {
                            dao.setFavorite(match.id, true)
                            Toast.makeText(ctx, ctx.getString(R.string.notif_saved), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(painterResource(R.drawable.ic_star_outline), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.notif_save))
            }
        }

        Button(
            onClick = { ending = true },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .heightIn(min = 56.dp)
                .reveal(step >= 4)
        ) { Text(stringResource(R.string.done), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
    if (ending) ClosingMoment("✨", stringResource(R.string.carry_it_with_you), stringResource(R.string.until_the_next_one))
    }
}
