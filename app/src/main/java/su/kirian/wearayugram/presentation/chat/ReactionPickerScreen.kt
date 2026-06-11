package su.kirian.wearayugram.presentation.chat

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import su.kirian.wearayugram.WearAyugramApp

// Telegram's default reaction set; chats with a restricted set just reject the call.
private val REACTION_EMOJIS = listOf(
    "👍", "👎", "❤", "🔥",
    "🥰", "👏", "😁", "🤔",
    "🤯", "😱", "😢", "🎉",
)

/**
 * Reaction picker, opened by long-pressing a message bubble. Tap an emoji to add it
 * (or remove it if it is already yours) and return to the chat.
 */
@Composable
fun ReactionPickerScreen(navController: NavController, chatId: Long, messageId: Long, topicId: Int = 0) {
    val app = navController.context.applicationContext as WearAyugramApp
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Highlight the user's current reaction on this message.
    val messages by remember { app.messageRepository.messages(chatId, topicId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val chosen = messages.firstOrNull { it.id == messageId }
        ?.reactions?.filter { it.isChosen }?.map { it.emoji }.orEmpty()

    fun toggle(emoji: String) {
        scope.launch {
            app.messageRepository.toggleReaction(chatId, messageId, emoji, topicId)
            navController.popBackStack()
        }
    }

    val replyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val replies = RemoteInput.getResultsFromIntent(result.data)
            val text = replies?.getCharSequence(KEY_REPLY)?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                scope.launch {
                    app.messageRepository.sendText(chatId, text, topicId, replyToMessageId = messageId)
                    navController.popBackStack()
                }
            }
        }
    }

    fun launchReplyInput() {
        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel("Ответ").build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        replyLauncher.launch(intent)
    }

    // Plain LazyColumn (not TransformingLazyColumn): a Row works as an item root here.
    ScreenScaffold(scrollState = listState) { contentPadding ->
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("Действия")
                }
            }
            item {
                FilledTonalButton(
                    onClick = { launchReplyInput() },
                    label = { Text("↩ Ответить") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(REACTION_EMOJIS.chunked(4).size) { rowIndex ->
                val row = REACTION_EMOJIS.chunked(4)[rowIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    for (emoji in row) {
                        Button(
                            onClick = { toggle(emoji) },
                            modifier = Modifier.size(40.dp),
                            colors = if (emoji in chosen) ButtonDefaults.buttonColors()
                            else ButtonDefaults.filledTonalButtonColors(),
                        ) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

private const val KEY_REPLY = "reply_text"
