package su.kirian.wearayugram.presentation.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import su.kirian.wearayugram.WearAyugramApp

/**
 * Target-chat picker for forwarding. Shows the loaded chat list; tapping a chat
 * forwards the message there and returns to the conversation.
 */
@Composable
fun ForwardPickerScreen(navController: NavController, fromChatId: Long, messageId: Long) {
    val app = navController.context.applicationContext as WearAyugramApp
    val scope = rememberCoroutineScope()
    val chats by remember { app.chatRepository.chatList }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberTransformingLazyColumnState()

    // Guards against double taps and shows a failure note instead of silently closing.
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun forwardTo(toChatId: Long) {
        if (sending) return
        sending = true
        failed = false
        scope.launch {
            val ok = app.messageRepository.forwardMessage(toChatId, fromChatId, messageId)
            if (ok) {
                navController.popBackStack()
            } else {
                sending = false
                failed = true
            }
        }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text(if (sending) "Пересылка…" else "Переслать в")
                }
            }
            if (failed) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Не удалось переслать",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            items(chats, key = { it.id }) { chat ->
                FilledTonalButton(
                    onClick = { forwardTo(chat.id) },
                    label = {
                        Text(
                            text = chat.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
