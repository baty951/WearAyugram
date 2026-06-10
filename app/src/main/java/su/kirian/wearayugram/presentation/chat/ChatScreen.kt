package su.kirian.wearayugram.presentation.chat

import android.Manifest
import android.app.Activity
import android.app.RemoteInput
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.domain.model.MessageContent
import su.kirian.wearayugram.domain.model.TgMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(navController: NavController, chatId: Long) {
    val app = navController.context.applicationContext as WearAyugramApp
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                handle["chatId"] = chatId
                ChatViewModel(app, handle)
            }
        }
    )

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    // Plain LazyColumn: TransformingLazyColumn's morphing measurement is expensive on
    // watch hardware and we don't use its effects in the message list.
    val listState = rememberLazyListState()

    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var voiceFile by remember { mutableStateOf<java.io.File?>(null) }

    DisposableEffect(Unit) {
        onDispose { if (voiceRecorder.isRecording) voiceRecorder.cancel() }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceFile = voiceRecorder.start()
            isRecording = true
        }
    }

    val textLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val replies = RemoteInput.getResultsFromIntent(result.data)
            val text = replies?.getCharSequence(KEY_TEXT)?.toString()?.trim()
            if (!text.isNullOrBlank()) viewModel.sendText(text)
        }
    }

    // Jump to the newest message once, after the first batch has been laid out.
    var didInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (!didInitialScroll && messages.isNotEmpty()) {
            didInitialScroll = true
            runCatching {
                kotlinx.coroutines.delay(250)
                listState.scrollToItem(messages.size - 1)
            }
        }
    }

    // Only send ViewMessages for ids we haven't reported yet — re-sending the whole
    // list on every messages emission floods TDLib while new messages arrive.
    val reportedReadIds = remember { mutableSetOf<Long>() }
    LaunchedEffect(messages) {
        val newIds = messages.asSequence()
            .filter { !it.isOutgoing && it.id !in reportedReadIds }
            .map { it.id }
            .toList()
        if (newIds.isNotEmpty()) {
            reportedReadIds.addAll(newIds)
            viewModel.markRead(newIds.toLongArray())
        }
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            // NOTE: EdgeButton must be a direct child of the edgeButton slot — it does
            // not support intrinsic measurement, so nesting it in a Row crashes the
            // ScreenScaffold layout with "Cannot round NaN value". Use plain Buttons.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice record button
                Button(
                    onClick = {
                        if (isRecording) {
                            val duration = voiceRecorder.stop()
                            isRecording = false
                            voiceFile?.let { viewModel.sendVoice(it.absolutePath, duration) }
                            voiceFile = null
                        } else {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                voiceFile = voiceRecorder.start()
                                isRecording = true
                            } else {
                                audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    colors = if (isRecording) ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ) else ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text(if (isRecording) "⏹" else "🎤", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.size(8.dp))
                // Text input button (plain Button, NOT EdgeButton)
                Button(
                    onClick = {
                        if (isRecording) { voiceRecorder.cancel(); isRecording = false; voiceFile = null }
                        val remoteInput = RemoteInput.Builder(KEY_TEXT).setLabel("Сообщение").build()
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                        textLauncher.launch(intent)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("✏", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: TgMessage) {
    val isOut = message.isOutgoing
    // A single Text node renders reliably as a TransformingLazyColumn item, while a
    // raw Column collapses to one visible item. So the whole bubble is ONE Text built
    // from an AnnotatedString: sender name, body and time as differently-styled spans.
    val cs = MaterialTheme.colorScheme
    val textColor = when {
        message.deletedLocally -> cs.onSurface.copy(alpha = 0.55f)
        isOut -> cs.onPrimaryContainer
        else -> cs.onSurface
    }
    val bubbleColor = when {
        message.deletedLocally -> cs.surfaceContainer.copy(alpha = 0.6f)
        isOut -> cs.primaryContainer
        else -> cs.surfaceContainerHigh
    }
    val senderColor = cs.tertiary
    val timeColor = textColor.copy(alpha = 0.55f)

    // Built once per message content (TgMessage is a data class, so the key covers
    // edits and delete marks) instead of on every scroll-frame recomposition.
    val annotated = remember(message) {
        val bodyText = if (message.deletedLocally) {
            val original = (message.content as? MessageContent.Text)?.text ?: "медиа"
            "🚫 $original"
        } else when (val c = message.content) {
            is MessageContent.Text -> c.text
            is MessageContent.Voice -> "🎤 ${c.durationSeconds}″"
            is MessageContent.Photo -> "📷 ${c.caption.ifEmpty { "Фото" }}"
            is MessageContent.Sticker -> c.emoji
            is MessageContent.Document -> "📎 ${c.fileName}"
            is MessageContent.Unsupported -> "…"
        }
        buildAnnotatedString {
            if (!isOut && message.senderName.isNotEmpty()) {
                withStyle(SpanStyle(color = senderColor, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)) {
                    append(message.senderName)
                }
                append("\n")
            }
            withStyle(SpanStyle(color = textColor, fontSize = 14.sp)) {
                append(bodyText)
            }
            withStyle(SpanStyle(color = timeColor, fontSize = 9.sp)) {
                append("   " + formatMsgTime(message.date) + if (isOut) " ✓" else "")
            }
        }
    }

    // Single Text node (renders reliably in TransformingLazyColumn). The left/right
    // bubble look comes from an asymmetric outer gap before the coloured background:
    // outgoing sits to the right, incoming to the left, with a tail-style corner.
    Text(
        text = annotated,
        textAlign = if (isOut) TextAlign.End else TextAlign.Start,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isOut) 32.dp else 8.dp,
                end = if (isOut) 8.dp else 32.dp,
                top = 3.dp,
                bottom = 3.dp
            )
            .clip(
                RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isOut) 16.dp else 4.dp,
                    bottomEnd = if (isOut) 4.dp else 16.dp
                )
            )
            .background(bubbleColor)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

// Cached: allocating a SimpleDateFormat per item per recomposition causes GC churn
// during scroll. Only ever touched from the main thread (composition).
private val msgTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatMsgTime(unixSeconds: Long): String =
    msgTimeFormat.format(Date(unixSeconds * 1000))

private const val KEY_TEXT = "msg_text"
