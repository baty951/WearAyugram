package su.kirian.wearayugram.domain.repository

import kotlinx.coroutines.flow.Flow
import su.kirian.wearayugram.domain.model.TgMessage

interface MessageRepository {
    fun messages(chatId: Long): Flow<List<TgMessage>>
    suspend fun loadHistory(chatId: Long, fromMessageId: Long = 0, limit: Int = 30)
    suspend fun sendText(chatId: Long, text: String)
    suspend fun sendVoice(chatId: Long, filePath: String, durationSeconds: Int)
    suspend fun markAsRead(chatId: Long, messageIds: LongArray)
    /** Downloads the chat-size photo of the message and returns the local path. */
    suspend fun downloadPhoto(chatId: Long, messageId: Long): String?
    /** Downloads the fullscreen-size photo (chat-size on low-RAM devices). */
    suspend fun downloadPhotoFull(chatId: Long, messageId: Long): String?
    /** Downloads the voice note file of the message and returns the local path. */
    suspend fun downloadVoice(chatId: Long, messageId: Long): String?
    fun setOpenChat(chatId: Long)
}
