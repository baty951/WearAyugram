package su.kirian.wearayugram.data.repository

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import su.kirian.wearayugram.Constants
import su.kirian.wearayugram.R
import su.kirian.wearayugram.ayugram.AntiRevokeManager
import su.kirian.wearayugram.ayugram.AyugramSettings
import su.kirian.wearayugram.ayugram.DeleteNotifyManager
import su.kirian.wearayugram.ayugram.GhostModeManager
import su.kirian.wearayugram.data.local.AppDatabase
import su.kirian.wearayugram.data.local.DeletedMessage
import su.kirian.wearayugram.data.tdlib.FileDownloader
import su.kirian.wearayugram.data.tdlib.TelegramClient
import su.kirian.wearayugram.data.tdlib.toDomain
import su.kirian.wearayugram.domain.model.MessageContent
import su.kirian.wearayugram.domain.model.TgMessage
import su.kirian.wearayugram.domain.repository.MessageRepository

class MessageRepositoryImpl(
    private val client: TelegramClient,
    private val context: Context,
    private val database: AppDatabase,
    private val fileDownloader: FileDownloader,
) : MessageRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flows = mutableMapOf<Long, MutableStateFlow<List<TgMessage>>>()
    private val dao = database.deletedMessageDao()

    private var openChatId: Long = -1L

    // chatId -> lastReadOutboxMessageId: outgoing messages with id <= this are read
    // by the recipient. Seeded from GetChat in loadHistory, kept live by
    // UpdateChatReadOutbox.
    private val readOutbox = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    override fun setOpenChat(chatId: Long) { openChatId = chatId }

    init {
        client.updatesOf<TdApi.UpdateChatReadOutbox>()
            .onEach { update ->
                readOutbox[update.chatId] = update.lastReadOutboxMessageId
                flows[update.chatId]?.update { list ->
                    list.map { msg ->
                        if (msg.isOutgoing && !msg.isRead && msg.id <= update.lastReadOutboxMessageId)
                            msg.copy(isRead = true)
                        else msg
                    }
                }
            }
            .launchIn(scope)

        client.updatesOf<TdApi.UpdateNewMessage>()
            .onEach { update ->
                val chatId = update.message.chatId
                val name = resolveSenderName(update.message)
                val domainMsg = update.message.toDomain(name, readOutbox[chatId] ?: 0)
                val flow = flows.getOrPut(chatId) { MutableStateFlow(emptyList()) }
                flow.update { it + domainMsg }
                // Notify for incoming messages not in the currently open chat
                if (!update.message.isOutgoing && chatId != openChatId) {
                    val text = when (val c = domainMsg.content) {
                        is MessageContent.Text -> c.text
                        is MessageContent.Voice -> "🎤 Голосовое"
                        is MessageContent.Photo -> "📷 Фото"
                        is MessageContent.Sticker -> c.emoji
                        is MessageContent.Document -> "📎 ${c.fileName}"
                        is MessageContent.Unsupported -> "Сообщение"
                    }
                    showMessageNotification(name, text)
                }
            }
            .launchIn(scope)

        client.updatesOf<TdApi.UpdateMessageEdited>()
            .onEach { update ->
                val flow = flows[update.chatId] ?: return@onEach
                val refreshed = runCatching {
                    client.send(TdApi.GetMessage(update.chatId, update.messageId))
                }.getOrNull() ?: return@onEach
                val name = resolveSenderName(refreshed)
                val lastRead = readOutbox[update.chatId] ?: 0
                flow.update { list ->
                    list.map { if (it.id == update.messageId) refreshed.toDomain(name, lastRead) else it }
                }
            }
            .launchIn(scope)

        client.updatesOf<TdApi.UpdateDeleteMessages>()
            .onEach { update ->
                // TDLib fires this both for real deletions and for plain cache
                // eviction. Only isPermanent && !fromCache is an actual deletion;
                // fromCache=true just means TDLib unloaded the messages locally.
                if (!update.isPermanent || update.fromCache) return@onEach
                val flow = flows[update.chatId] ?: return@onEach
                val antiRevoke = runCatching { AntiRevokeManager.isEnabled.first() }.getOrDefault(true)
                val deleteNotify = runCatching { DeleteNotifyManager.isEnabled.first() }.getOrDefault(true)
                val deleted = update.messageIds.toHashSet()

                flow.update { list ->
                    list.map { msg ->
                        if (msg.id !in deleted) return@map msg
                        // Save to Room for anti-revoke
                        if (antiRevoke) {
                            val originalText = when (val c = msg.content) {
                                is MessageContent.Text -> c.text
                                else -> "[медиа]"
                            }
                            scope.launch {
                                dao.insert(
                                    DeletedMessage(
                                        id = msg.id,
                                        chatId = msg.chatId,
                                        text = originalText,
                                        senderName = msg.senderName,
                                        date = msg.date,
                                    )
                                )
                            }
                            // Send delete notification if incoming and not in open chat
                            if (deleteNotify && !msg.isOutgoing && msg.chatId != openChatId) {
                                val originalText2 = when (val c = msg.content) {
                                    is MessageContent.Text -> c.text
                                    else -> "[медиа]"
                                }
                                showDeleteNotification(msg.senderName, originalText2)
                            }
                            // Mark as deleted locally — keep in list with flag
                            msg.copy(deletedLocally = true)
                        } else {
                            null // will be filtered below
                        }
                    }.filterNotNull()
                }

                // If anti-revoke disabled, remove messages normally
                if (!antiRevoke) {
                    flow.update { list -> list.filter { it.id !in deleted } }
                }
            }
            .launchIn(scope)
    }

    override fun messages(chatId: Long): Flow<List<TgMessage>> =
        flows.getOrPut(chatId) { MutableStateFlow(emptyList()) }.asStateFlow()

    override suspend fun loadHistory(chatId: Long, fromMessageId: Long, limit: Int) {
        // TDLib's GetChatHistory only returns messages already in its local DB.
        // On first open only the last message is cached, so we must OpenChat to
        // trigger a server fetch, then retry until a real batch arrives.
        runCatching { client.send(TdApi.OpenChat(chatId)) }

        // Seed the outbox-read watermark so history maps ✓/✓✓ correctly even before
        // the first UpdateChatReadOutbox arrives.
        runCatching {
            readOutbox[chatId] = client.send(TdApi.GetChat(chatId)).lastReadOutboxMessageId
        }

        var rawMessages: Array<TdApi.Message> = emptyArray()
        var attempt = 0
        while (attempt < 8) {
            val result = runCatching {
                client.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false))
            }.getOrNull()
            val msgs = result?.messages ?: emptyArray()
            if (msgs.size > rawMessages.size) rawMessages = msgs
            // Got a meaningful batch (more than just the cached last message) — stop.
            if (rawMessages.size >= 2 || (attempt >= 2 && rawMessages.isNotEmpty())) break
            kotlinx.coroutines.delay(400)
            attempt++
        }

        val lastReadOutbox = readOutbox[chatId] ?: 0
        val messages = rawMessages
            .mapNotNull { msg ->
                val name = resolveSenderName(msg)
                runCatching { msg.toDomain(name, lastReadOutbox) }.getOrNull()
            }
            .reversed()

        // Merge with any locally-deleted messages from Room
        val deletedInRoom = runCatching {
            dao.getForChat(chatId).first().associateBy { it.id }
        }.getOrDefault(emptyMap())

        val flow = flows.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        flow.update { existing ->
            val existingIds = existing.map { it.id }.toHashSet()
            val merged = messages.map { msg ->
                if (msg.id in deletedInRoom) msg.copy(deletedLocally = true) else msg
            }
            merged.filter { it.id !in existingIds } + existing
        }
    }

    override suspend fun sendText(chatId: Long, text: String) {
        val formatted = TdApi.FormattedText().apply {
            this.text = text
            this.entities = emptyArray()
        }
        val content = TdApi.InputMessageText().apply {
            this.text = formatted
            this.clearDraft = true
        }
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = content
        }
        client.send(request)
    }

    override suspend fun sendVoice(chatId: Long, filePath: String, durationSeconds: Int) {
        val inputFile = TdApi.InputFileLocal(filePath)
        val voiceNote = TdApi.InputMessageVoiceNote().apply {
            this.voiceNote = inputFile
            this.duration = durationSeconds
            this.waveform = ByteArray(0)
        }
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = voiceNote
        }
        client.send(request)
    }

    override suspend fun downloadPhoto(chatId: Long, messageId: Long): String? {
        val flow = flows[chatId] ?: return null
        val photo = flow.value.firstOrNull { it.id == messageId }
            ?.content as? MessageContent.Photo ?: return null
        photo.localPath?.let { return it }

        val path = fileDownloader.download(photo.fileId) ?: return null
        // The bubble observes this flow, so updating localPath re-renders it.
        flow.update { list ->
            list.map { msg ->
                if (msg.id == messageId && msg.content is MessageContent.Photo)
                    msg.copy(content = msg.content.copy(localPath = path))
                else msg
            }
        }
        return path
    }

    override suspend fun downloadVoice(chatId: Long, messageId: Long): String? {
        val flow = flows[chatId] ?: return null
        val voice = flow.value.firstOrNull { it.id == messageId }
            ?.content as? MessageContent.Voice ?: return null
        voice.localPath?.let { return it }

        val path = fileDownloader.download(voice.fileId) ?: return null
        flow.update { list ->
            list.map { msg ->
                if (msg.id == messageId && msg.content is MessageContent.Voice)
                    msg.copy(content = msg.content.copy(localPath = path))
                else msg
            }
        }
        return path
    }

    override suspend fun downloadPhotoFull(chatId: Long, messageId: Long): String? {
        val photo = flows[chatId]?.value?.firstOrNull { it.id == messageId }
            ?.content as? MessageContent.Photo ?: return null
        val fileId =
            if (AyugramSettings.isLowRamDevice || photo.fullFileId == 0) photo.fileId
            else photo.fullFileId
        // Fall back to the already-downloaded chat-size file rather than nothing.
        return fileDownloader.download(fileId) ?: photo.localPath
    }

    override suspend fun markAsRead(chatId: Long, messageIds: LongArray) {
        val ghostMode = runCatching { GhostModeManager.isEnabled.first() }.getOrDefault(false)
        if (ghostMode) return
        val request = TdApi.ViewMessages().apply {
            this.chatId = chatId
            this.messageIds = messageIds
            this.forceRead = false
        }
        client.send(request)
    }

    private fun showMessageNotification(senderName: String, text: String) {
        val notif = NotificationCompat.Builder(context, Constants.CHANNEL_MESSAGES)
            .setContentTitle(senderName)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(("msg_${System.currentTimeMillis()}").hashCode(), notif)
        }
    }

    private fun showDeleteNotification(senderName: String, text: String) {
        val notif = NotificationCompat.Builder(context, Constants.CHANNEL_DELETE_NOTIFY)
            .setContentTitle("$senderName удалил сообщение")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), notif)
        }
    }

    // userId/chatId -> display name. Without this every message costs a GetUser
    // JNI round-trip (30 sequential calls per history load, one per incoming update).
    // User ids are positive and chat ids negative, so one map holds both.
    private val senderNameCache = java.util.concurrent.ConcurrentHashMap<Long, String>()

    private suspend fun resolveSenderName(message: TdApi.Message): String {
        val key = when (val sender = message.senderId) {
            is TdApi.MessageSenderUser -> sender.userId
            is TdApi.MessageSenderChat -> sender.chatId
            else -> return ""
        }
        senderNameCache[key]?.let { return it }
        val name = when (val sender = message.senderId) {
            is TdApi.MessageSenderUser -> runCatching {
                val user = client.send(TdApi.GetUser(sender.userId))
                "${user.firstName} ${user.lastName}".trim()
            }.getOrDefault("")
            is TdApi.MessageSenderChat -> runCatching {
                client.send(TdApi.GetChat(sender.chatId)).title
            }.getOrDefault("")
            else -> ""
        }
        if (name.isNotEmpty()) senderNameCache[key] = name
        return name
    }
}
