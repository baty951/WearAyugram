package su.kirian.wearayugram.domain.model

import androidx.compose.runtime.Immutable

// A forum topic inside a supergroup with topics enabled. id is TDLib's forumTopicId.
@Immutable
data class TgTopic(
    val id: Int,
    val chatId: Long,
    val title: String,
    val unreadCount: Int,
    val lastMessage: String?,
    val lastMessageTime: Long,
    val isPinned: Boolean,
    val isGeneral: Boolean,
)
