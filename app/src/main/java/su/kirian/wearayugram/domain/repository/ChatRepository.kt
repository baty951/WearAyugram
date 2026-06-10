package su.kirian.wearayugram.domain.repository

import kotlinx.coroutines.flow.Flow
import su.kirian.wearayugram.domain.model.TgChat
import su.kirian.wearayugram.domain.model.TgChatFolder

interface ChatRepository {
    val chatList: Flow<List<TgChat>>
    /** User-defined chat folders; empty when the account has none. */
    val folders: Flow<List<TgChatFolder>>
    /** Currently shown folder id, null = the main chat list. */
    val selectedFolderId: Flow<Int?>
    suspend fun loadChats(limit: Int = 20)
    /** Switches the chat list to the given folder (null = main) and reloads it. */
    suspend fun selectFolder(folderId: Int?)
    suspend fun getChatById(chatId: Long): TgChat?
}
