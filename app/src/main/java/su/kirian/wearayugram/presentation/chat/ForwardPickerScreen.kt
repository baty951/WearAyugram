package su.kirian.wearayugram.presentation.chat

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.wear.input.RemoteInputIntentHelper
import kotlinx.coroutines.launch
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.domain.model.TgChat

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

    // Search across users/groups (local + public usernames). Empty query = chat list.
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<TgChat>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    val searchLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val replies = RemoteInput.getResultsFromIntent(result.data)
            val text = replies?.getCharSequence(KEY_FORWARD_QUERY)?.toString()?.trim().orEmpty()
            query = text
            if (text.isEmpty()) {
                searchResults = emptyList()
            } else {
                searching = true
                scope.launch {
                    searchResults = app.chatRepository.searchChats(text)
                    searching = false
                }
            }
        }
    }

    fun launchSearchInput() {
        val remoteInput = RemoteInput.Builder(KEY_FORWARD_QUERY).setLabel("Кому переслать?").build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        searchLauncher.launch(intent)
    }

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
            item {
                FilledTonalButton(
                    onClick = { launchSearchInput() },
                    label = {
                        Text(
                            text = when {
                                searching -> "Поиск…"
                                query.isEmpty() -> "🔍 Поиск людей и групп"
                                else -> "🔍 $query"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty() && !searching && searchResults.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ничего не найдено", style = MaterialTheme.typography.bodySmall)
                    }
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
            items(if (query.isEmpty()) chats else searchResults, key = { it.id }) { chat ->
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

private const val KEY_FORWARD_QUERY = "forward_query"
