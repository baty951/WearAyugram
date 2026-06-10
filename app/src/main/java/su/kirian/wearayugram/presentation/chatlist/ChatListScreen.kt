package su.kirian.wearayugram.presentation.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import su.kirian.wearayugram.domain.model.TgChat
import su.kirian.wearayugram.presentation.Routes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatListScreen(navController: NavController) {
    val viewModel: ChatListViewModel = viewModel()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                Text("⚙")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("WearAyugram")
                }
            }
            if (chats.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Загрузка чатов…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                items(chats, key = { it.id }) { chat ->
                    ChatItem(chat = chat, onClick = { navController.navigate(Routes.chat(chat.id)) })
                }
            }
        }
    }
}

@Composable
private fun ChatItem(chat: TgChat, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ChatAvatar(title = chat.title, id = chat.id)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!chat.lastMessage.isNullOrEmpty()) {
                    Text(
                        text = chat.lastMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (chat.lastMessageTime > 0) {
                    Text(
                        text = formatChatTime(chat.lastMessageTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (chat.isMuted) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.primary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (chat.unreadCount > 99) "…" else "${chat.unreadCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatAvatar(title: String, id: Long) {
    val color = avatarColor(id)
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )
    }
}

private fun avatarColor(id: Long): Color {
    val colors = listOf(
        Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350),
        Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFFFF7043),
        Color(0xFF66BB6A), Color(0xFF8D6E63)
    )
    return colors[(id % colors.size).toInt().coerceAtLeast(0)]
}

// Cached: allocating a SimpleDateFormat per item per recomposition causes GC churn
// during scroll. Only ever touched from the main thread (composition).
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

private fun formatChatTime(unixSeconds: Long): String {
    val now = System.currentTimeMillis() / 1000
    return if (now - unixSeconds < 86400) {
        timeFormat.format(Date(unixSeconds * 1000))
    } else {
        dateFormat.format(Date(unixSeconds * 1000))
    }
}
