package su.kirian.wearayugram.domain.repository

import kotlinx.coroutines.flow.Flow
import su.kirian.wearayugram.domain.model.TgChat

interface ChatRepository {
    val chatList: Flow<List<TgChat>>
    suspend fun loadChats(limit: Int = 20)
    suspend fun getChatById(chatId: Long): TgChat?
}
