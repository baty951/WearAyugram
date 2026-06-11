package su.kirian.wearayugram.domain.repository

import kotlinx.coroutines.flow.Flow
import su.kirian.wearayugram.domain.model.TgMessage
import su.kirian.wearayugram.domain.model.TgMessageEdit

// topicId: forum topic id for forum supergroups, 0 for regular chats.
interface MessageRepository {
    fun messages(chatId: Long, topicId: Int = 0): Flow<List<TgMessage>>
    suspend fun loadHistory(chatId: Long, fromMessageId: Long = 0, limit: Int = 30, topicId: Int = 0)
    suspend fun sendText(chatId: Long, text: String, topicId: Int = 0)
    suspend fun sendVoice(chatId: Long, filePath: String, durationSeconds: Int, topicId: Int = 0)
    suspend fun markAsRead(chatId: Long, messageIds: LongArray)
    /** Downloads the chat-size photo of the message and returns the local path. */
    suspend fun downloadPhoto(chatId: Long, messageId: Long, topicId: Int = 0): String?
    /** Downloads the fullscreen-size photo (chat-size on low-RAM devices). */
    suspend fun downloadPhotoFull(chatId: Long, messageId: Long, topicId: Int = 0): String?
    /** Downloads the voice note file of the message and returns the local path. */
    suspend fun downloadVoice(chatId: Long, messageId: Long, topicId: Int = 0): String?
    /** Downloads the small thumbnail of a video / video note for the bubble preview. */
    suspend fun downloadVideoThumb(chatId: Long, messageId: Long, topicId: Int = 0): String?
    /** Downloads the full video / video note file and returns the local path. */
    suspend fun downloadVideo(chatId: Long, messageId: Long, topicId: Int = 0): String?
    /** Downloads the displayable sticker image (WEBP or animated-sticker thumbnail). */
    suspend fun downloadSticker(chatId: Long, messageId: Long, topicId: Int = 0): String?
    /** Downloads a document file and returns the local path. */
    suspend fun downloadDocument(chatId: Long, messageId: Long, topicId: Int = 0): String?
    /** Saved pre-edit versions of the message, oldest first. */
    fun editHistory(messageId: Long): Flow<List<TgMessageEdit>>
    fun setOpenChat(chatId: Long)
}
