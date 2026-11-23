package com.smrti.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.smrti.app.R
import com.smrti.app.data.Quote
import java.io.File

/** "my images" are a file (older copies) or a content:// uri (originals); album photos are content:// uris. */
fun imageModel(path: String): Any = if (path.startsWith("content://")) Uri.parse(path) else File(path)

/** Grid thumbnail: decoded at the size it is shown (not the full photo), with a placeholder and an error state. */
@Composable
fun Thumbnail(model: Any, modifier: Modifier = Modifier, sizePx: Int = 320, description: String? = null) {
    val ctx = LocalContext.current
    val request = remember(model, sizePx) {
        ImageRequest.Builder(ctx)
            .data(model)
            .size(Size(sizePx, sizePx))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) },
        error = { PictureMissing() }
    )
}

/** Shown where a photo can't be loaded (deleted, or access was removed). */
@Composable
fun PictureMissing(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.ic_broken_image), contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.size(28.dp)
        )
    }
}

/** The request used by the reminder page; the same one is started early when a notification is tapped. */
fun reminderImageRequest(context: Context, path: String): ImageRequest =
    ImageRequest.Builder(context).data(imageModel(path)).size(Size(1080, 1080)).build()

/** Pick several photos and keep access to the originals (so they don't have to be copied into the app). */
class PickPersistableImages : androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
}

/** Pick one photo from any album or app and keep long-term access to it. */
class PickPersistableImage : androidx.activity.result.contract.ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
}
