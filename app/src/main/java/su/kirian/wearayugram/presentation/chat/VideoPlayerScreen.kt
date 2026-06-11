package su.kirian.wearayugram.presentation.chat

import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import su.kirian.wearayugram.WearAyugramApp

/**
 * Fullscreen player for videos and video notes. Downloads the file first (videos can
 * take a while on the watch radio — spinner until then), then plays via VideoView:
 * the system MediaPlayer pipeline, no extra dependencies. Tap = pause/resume, exit =
 * swipe-to-dismiss.
 */
@Composable
fun VideoPlayerScreen(
    navController: NavController,
    chatId: Long,
    messageId: Long,
    topicId: Int = 0,
    loop: Boolean = false, // GIF-анимации зацикливаются
) {
    val app = navController.context.applicationContext as WearAyugramApp

    var path by remember { mutableStateOf<String?>(null) }
    var downloadDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        path = app.messageRepository.downloadVideo(chatId, messageId, topicId)
        downloadDone = true
    }

    var videoView by remember { mutableStateOf<VideoView?>(null) }
    DisposableEffect(Unit) {
        onDispose { videoView?.stopPlayback() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val p = path
        when {
            p != null -> AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(p)
                        setOnPreparedListener {
                            it.isLooping = loop
                            it.start()
                        }
                        // Setting a click listener makes the view clickable.
                        setOnClickListener { if (isPlaying) pause() else start() }
                        videoView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            downloadDone -> Text("Не удалось загрузить", Modifier.align(Alignment.Center))
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
