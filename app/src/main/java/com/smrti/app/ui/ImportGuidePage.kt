package com.smrti.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import com.smrti.app.data.QuoteImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val exampleJson = """[
  {
    "text": "Small steps every day 🔥",
    "author": "Smrti",
    "category": "Motivation"
  },
  {
    "text": "We suffer more often in imagination than in reality.",
    "author": "Seneca",
    "category": "Quote"
  },
  {
    "text": "I'll be back.",
    "author": "The Terminator",
    "category": "Dialogue"
  }
]"""

/** Explains what file and structure the importer expects, then lets the user pick the file. Full page inside the app. */
@Composable
fun ImportGuidePage() {
    val colors = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val msg = try {
                val json = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                "Imported ${QuoteImporter.importJson(ctx, json)} new quotes"
            } catch (e: Exception) {
                "Couldn't read that file. Check it matches the structure in the import guide."
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                Nav.close()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Import quotes", "Add many at once from a file")

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            GuideTitle("What file")
            Text(
                "A plain text file ending in .json, saved on your phone (UTF-8, so emoji work). " +
                    "Quotes you already have are skipped, so importing twice is safe.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
            )

            GuideTitle("How")
            Step("1", "Create the file in the structure below. Any text editor works.")
            Step("2", "Tap Choose file and pick it from Files, Downloads or Drive.")
            Step("3", "Smrti tells you how many new quotes were added.")

            GuideTitle("Structure")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(exampleJson, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, softWrap = false)
            }

            Spacer(Modifier.height(12.dp))
            FieldRow("text", "required", "The quote, motivation or dialogue line.")
            FieldRow("author", "optional", "Person, character or movie. Shown under the quote.")
            FieldRow("category", "optional", "Quote, Motivation or Dialogue. Leave out for Quote. Any other word creates a new category.")

            GuideTitle("Also accepted")
            Text(
                "Just a list of strings:  [\"one\", \"two\"]\n" +
                    "A wrapper object:  { \"quotes\": [ … ] }\n" +
                    "Other field names: quote, content, message, dialogue for text; by, name, speaker, character, source for author; type, tag for category.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, lineHeight = 19.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tip: ask any AI chat \"Write 50 motivational quotes in this JSON format\" and paste the example above.",
                style = MaterialTheme.typography.bodySmall, color = colors.primary, lineHeight = 19.sp
            )

            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(exampleJson))
                    Toast.makeText(ctx, "Example copied", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Copy example") }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(56.dp)
        ) { Text("Choose file", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun GuideTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun Step(number: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(number, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun FieldRow(name: String, tag: String, description: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(84.dp)) {
            Text(name, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(tag, fontSize = 11.sp, color = if (tag == "required") colors.primary else colors.onSurfaceVariant)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, lineHeight = 19.sp)
    }
}
