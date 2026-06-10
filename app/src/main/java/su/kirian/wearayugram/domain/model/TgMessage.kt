package su.kirian.wearayugram.domain.model

import androidx.compose.runtime.Immutable

// @Immutable lets Compose skip recomposition of unchanged list items.
@Immutable
data class TgMessage(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val senderName: String,
    val content: MessageContent,
    val date: Long,
    val isOutgoing: Boolean,
    val isEdited: Boolean,
    // null = never deleted, non-null = deleted locally by anti-revoke
    val deletedLocally: Boolean = false,
)

@Immutable
sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    data class Voice(val durationSeconds: Int, val localPath: String?) : MessageContent()
    data class Photo(val caption: String, val localPath: String?) : MessageContent()
    data class Sticker(val emoji: String, val localPath: String?) : MessageContent()
    data class Document(val fileName: String, val mimeType: String) : MessageContent()
    data object Unsupported : MessageContent()
}
