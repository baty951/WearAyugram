package su.kirian.wearayugram.domain.model

import androidx.compose.runtime.Immutable

// @Immutable lets Compose skip recomposition of unchanged list items.
@Immutable
data class TgChat(
    val id: Long,
    val title: String,
    val lastMessage: String?,
    val lastMessageTime: Long,
    val unreadCount: Int,
    val isMuted: Boolean,
    val photoPath: String?,
    val type: ChatType,
)

enum class ChatType { Private, Group, Supergroup, Channel, Bot }
