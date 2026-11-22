package com.smrti.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    // the peak: picture, then the words, then the author arrive one after another
    var step by remember { mutableIntStateOf(0) }
    // the end: a warm closing beat before the page leaves
    var ending by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120); step = 1
        delay(380); step = 2
        delay(380); step = 3
        delay(300); step = 4
    }
    LaunchedEffect(ending) {
        if (ending) { delay(1100); Nav.close() }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(reminder.category.ifBlank { "Reminder" })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // my images are files in app storage, album images are content:// uris
            val image: Any? = reminder.imagePath?.let {
                if (it.startsWith("content://")) Uri.parse(it) else File(it).takeIf { f -> f.exists() }
            }
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    // show the whole picture, never cropped: full width, height follows the photo
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
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

        Button(
            onClick = { ending = true },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(56.dp)
                .reveal(step >= 4)
        ) { Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
    if (ending) ClosingMoment("✨", "Carry it with you", "Until the next one")
    }
}
