package su.kirian.wearayugram.domain.model

import androidx.compose.runtime.Immutable

// A user-defined chat folder (Telegram "chat folders"). id is TDLib's chatFolderId.
@Immutable
data class TgChatFolder(
    val id: Int,
    val title: String,
)
