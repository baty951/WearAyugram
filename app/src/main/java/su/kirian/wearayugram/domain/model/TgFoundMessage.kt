package su.kirian.wearayugram.domain.model

import androidx.compose.runtime.Immutable

// A global message search hit: enough to render a result row and open its chat.
@Immutable
data class TgFoundMessage(
    val chatId: Long,
    val messageId: Long,
    val topicId: Int,
    val chatTitle: String,
    val preview: String,
    val date: Long,
)
