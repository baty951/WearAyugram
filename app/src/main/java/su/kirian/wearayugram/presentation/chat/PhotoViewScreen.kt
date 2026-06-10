package su.kirian.wearayugram.presentation.chat

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import su.kirian.wearayugram.WearAyugramApp

/**
 * Fullscreen photo viewer. Zoom: rotary crown or double-tap; pan by dragging while
 * zoomed in. Exit is the standard swipe-to-dismiss from SwipeDismissableNavHost
 * (drag handling is skipped at 1x so the dismiss gesture stays reachable).
 */
@Composable
fun PhotoViewScreen(navController: NavController, chatId: Long, messageId: Long) {
    val app = navController.context.applicationContext as WearAyugramApp

    var path by remember { mutableStateOf<String?>(null) }
    var downloadDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        path = app.messageRepository.downloadPhotoFull(chatId, messageId)
        downloadDone = true
    }

    // Cap the decode at 2x the screen width: enough detail for the max zoom level
    // without risking the 32-bit heap on the original-size JPEG.
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = path?.let { PhotoDecoder.decode(it, screenWidthPx * 2) }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val maxPan = screenWidthPx.toFloat() // generous clamp; Crop never reveals edges far beyond this

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { event ->
                scale = (scale * (1f - event.verticalScrollPixels / 500f)).coerceIn(1f, 4f)
                if (scale == 1f) { offsetX = 0f; offsetY = 0f }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 2.5f
                    if (scale == 1f) { offsetX = 0f; offsetY = 0f }
                })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    // At 1x the drag must NOT be consumed — it's the swipe-to-dismiss.
                    if (scale > 1f) {
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(-maxPan, maxPan)
                        offsetY = (offsetY + dragAmount.y).coerceIn(-maxPan, maxPan)
                    }
                }
            }
    ) {
        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Фото",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
            )
            downloadDone && path == null -> Text("Не удалось загрузить", Modifier.align(Alignment.Center))
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
