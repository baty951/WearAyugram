package su.kirian.wearayugram.domain.repository

import kotlinx.coroutines.flow.Flow
import su.kirian.wearayugram.domain.model.TgChat
import su.kirian.wearayugram.domain.model.TgChatFolder
import su.kirian.wearayugram.domain.model.TgFoundMessage
import su.kirian.wearayugram.domain.model.TgTopic

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
    /** True if the chat is a forum supergroup (topics enabled). */
    suspend fun isForum(chatId: Long): Boolean
    /** Topics of a forum chat, in TDLib's display order. */
    suspend fun getTopics(chatId: Long): List<TgTopic>
    /** Chats matching the query: local results first, then public usernames. */
    suspend fun searchChats(query: String): List<TgChat>
    /** Global full-text message search across all chats. */
    suspend fun searchMessages(query: String): List<TgFoundMessage>
}
