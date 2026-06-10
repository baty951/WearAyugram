package su.kirian.wearayugram.domain.repository

import kotlinx.coroutines.flow.Flow
import su.kirian.wearayugram.domain.model.TgMessage

interface MessageRepository {
    fun messages(chatId: Long): Flow<List<TgMessage>>
    suspend fun loadHistory(chatId: Long, fromMessageId: Long = 0, limit: Int = 30)
    suspend fun sendText(chatId: Long, text: String)
    suspend fun sendVoice(chatId: Long, filePath: String, durationSeconds: Int)
    suspend fun markAsRead(chatId: Long, messageIds: LongArray)
    fun setOpenChat(chatId: Long)
}
