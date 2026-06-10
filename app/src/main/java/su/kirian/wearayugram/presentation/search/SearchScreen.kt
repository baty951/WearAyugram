package su.kirian.wearayugram.presentation.search

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import su.kirian.wearayugram.domain.model.TgChat
import su.kirian.wearayugram.domain.model.TgFoundMessage
import su.kirian.wearayugram.presentation.Routes

@Composable
fun SearchScreen(navController: NavController) {
    val viewModel: SearchViewModel = viewModel()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()

    val queryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val replies = RemoteInput.getResultsFromIntent(result.data)
            val text = replies?.getCharSequence(KEY_QUERY)?.toString()?.trim()
            if (!text.isNullOrBlank()) viewModel.search(text)
        }
    }

    fun launchQueryInput() {
        val remoteInput = RemoteInput.Builder(KEY_QUERY).setLabel("Поиск").build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        queryLauncher.launch(intent)
    }

    fun openChat(chatId: Long, topicId: Int = 0) {
        if (topicId != 0) {
            navController.navigate(Routes.chatTopic(chatId, topicId))
            return
        }
        viewModel.openChat(chatId) { isForum ->
            navController.navigate(
                if (isForum) Routes.topics(chatId) else Routes.chat(chatId)
            )
        }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("Поиск")
                }
            }
            item {
                FilledTonalButton(
                    onClick = { launchQueryInput() },
                    label = {
                        Text(
                            text = if (query.isEmpty()) "🔍 Ввести запрос" else "🔍 $query",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (searching) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Поиск…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (chats.isNotEmpty()) {
                item {
                    ListHeader(modifier = Modifier.fillMaxWidth()) {
                        Text("Чаты", style = MaterialTheme.typography.labelMedium)
                    }
                }
                items(chats, key = { "c${it.id}" }) { chat ->
                    ChatResult(chat = chat, onClick = { openChat(chat.id) })
                }
            }
            if (messages.isNotEmpty()) {
                item {
                    ListHeader(modifier = Modifier.fillMaxWidth()) {
                        Text("Сообщения", style = MaterialTheme.typography.labelMedium)
                    }
                }
                items(messages, key = { "m${it.chatId}_${it.messageId}" }) { msg ->
                    MessageResult(msg = msg, onClick = { openChat(msg.chatId, msg.topicId) })
                }
            }
            if (!searching && query.isNotEmpty() && chats.isEmpty() && messages.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ничего не найдено", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatResult(chat: TgChat, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
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

@Composable
private fun MessageResult(msg: TgFoundMessage, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = msg.chatTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = msg.preview,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private const val KEY_QUERY = "search_query"
