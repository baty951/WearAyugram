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
import kotlinx.coroutines.flow.map
import su.kirian.wearayugram.data.local.AppDatabase
import su.kirian.wearayugram.data.local.DeletedMessage
import su.kirian.wearayugram.data.local.MessageEdit
import su.kirian.wearayugram.domain.model.TgMessageEdit
import su.kirian.wearayugram.data.tdlib.FileDownloader
import su.kirian.wearayugram.data.tdlib.TelegramClient
import su.kirian.wearayugram.data.tdlib.toDomain
import su.kirian.wearayugram.data.tdlib.toDomainContent
import su.kirian.wearayugram.data.tdlib.toDomainReactions
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
    private val editDao = database.messageEditDao()

    // Keyed by chat + forum topic; topicId 0 = a regular (non-forum) chat. Forum
    // supergroups get one flow per topic, so each topic screen sees only its thread.
    private val flows = mutableMapOf<Pair<Long, Int>, MutableStateFlow<List<TgMessage>>>()
    private val dao = database.deletedMessageDao()

    private fun flowOf(chatId: Long, topicId: Int): MutableStateFlow<List<TgMessage>> =
        synchronized(flows) { flows.getOrPut(chatId to topicId) { MutableStateFlow(emptyList()) } }

    private fun flowsOfChat(chatId: Long): List<MutableStateFlow<List<TgMessage>>> =
        synchronized(flows) { flows.filterKeys { it.first == chatId }.values.toList() }

    private fun topicOf(message: TdApi.Message): Int =
        (message.topicId as? TdApi.MessageTopicForum)?.forumTopicId ?: 0

    // reply-message-id -> "Sender: preview". Replies usually point at messages from
    // the same loaded batch; only misses go to GetRepliedMessage, cached afterwards.
    private val replyPreviewCache = java.util.concurrent.ConcurrentHashMap<Long, String>()

    private fun previewOf(msg: TgMessage): String {
        val body = when (val c = msg.content) {
            is MessageContent.Text -> c.text.take(60)
            is MessageContent.Voice -> "🎤 Голосовое"
            is MessageContent.Photo -> "📷 ${c.caption.ifEmpty { "Фото" }}".take(60)
            is MessageContent.Video -> "📹 ${c.caption.ifEmpty { "Видео" }}".take(60)
            is MessageContent.VideoNote -> "⭕ Кружок"
            is MessageContent.Animation -> "GIF"
            is MessageContent.Sticker -> "${c.emoji} Стикер"
            is MessageContent.Document -> "📎 ${c.fileName}"
            is MessageContent.Poll -> "📊 ${c.question}".take(60)
            is MessageContent.Location -> "📍 Геопозиция"
            is MessageContent.Venue -> "📍 ${c.title}"
            is MessageContent.Contact -> "👤 ${c.name}"
            is MessageContent.Dice -> c.emoji
            is MessageContent.Call -> "📞 Звонок"
            is MessageContent.AnimatedEmoji -> c.emoji
            is MessageContent.Service -> c.text
            is MessageContent.Unsupported -> "Сообщение"
        }
        return if (msg.senderName.isNotEmpty()) "${msg.senderName}: $body" else body
    }

    /** Fills replyPreview for a reply message: local batch first, then TDLib. */
    private suspend fun resolveReplyPreview(msg: TgMessage, batch: Map<Long, TgMessage>): TgMessage {
        if (msg.replyToMessageId == 0L || msg.replyPreview != null) return msg
        replyPreviewCache[msg.replyToMessageId]?.let { return msg.copy(replyPreview = it) }
        val preview = batch[msg.replyToMessageId]?.let { previewOf(it) }
            ?: runCatching {
                val original = client.send(TdApi.GetRepliedMessage(msg.chatId, msg.id))
                previewOf(original.toDomain(resolveSenderName(original)))
            }.getOrNull()
        if (preview != null) replyPreviewCache[msg.replyToMessageId] = preview
        return if (preview != null) msg.copy(replyPreview = preview) else msg
    }

    // The editable text of a message: the body for text messages, the caption for
    // captioned media. Everything else has no text worth versioning.
    private fun textOf(c: MessageContent): String? = when (c) {
        is MessageContent.Text -> c.text
        is MessageContent.Photo -> c.caption
        is MessageContent.Video -> c.caption
        is MessageContent.Animation -> c.caption
        else -> null
    }

    private var openChatId: Long = -1L

    // chatId -> lastReadOutboxMessageId: outgoing messages with id <= this are read
    // by the recipient. Seeded from GetChat in loadHistory, kept live by
    // UpdateChatReadOutbox.
    private val readOutbox = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    // chatId -> muted: mute is account-wide (synced from other devices via
    // notificationSettings), so muted chats must not fire watch notifications.
    // Seeded lazily from GetChat, kept live by UpdateChatNotificationSettings.
    private val mutedChats = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()

    private fun isMutedSettings(s: TdApi.ChatNotificationSettings): Boolean =
        // useDefaultMuteFor=true means "follow the scope default", which is unmuted
        // unless the user muted a whole chat category — not the per-chat mute we
        // are asked to respect here.
        !s.useDefaultMuteFor && s.muteFor > 0

    private suspend fun isChatMuted(chatId: Long): Boolean =
        mutedChats[chatId] ?: runCatching {
            isMutedSettings(client.send(TdApi.GetChat(chatId)).notificationSettings)
        }.getOrDefault(false).also { mutedChats[chatId] = it }

    override fun setOpenChat(chatId: Long) { openChatId = chatId }

    override fun editHistory(messageId: Long): Flow<List<TgMessageEdit>> =
        editDao.getForMessage(messageId).map { list ->
            list.map { TgMessageEdit(id = it.editId, text = it.oldText, editedAt = it.editedAt) }
        }

    init {
        client.updatesOf<TdApi.UpdateChatNotificationSettings>()
            .onEach { mutedChats[it.chatId] = isMutedSettings(it.notificationSettings) }
            .launchIn(scope)

        // Inline content changes: poll vote counts, media replacing placeholders etc.
        client.updatesOf<TdApi.UpdateMessageContent>()
            .onEach { update ->
                val newContent = runCatching { update.newContent.toDomainContent() }.getOrNull()
                    ?: return@onEach
                flowsOfChat(update.chatId).forEach { flow ->
                    flow.update { list ->
                        list.map {
                            if (it.id == update.messageId) it.copy(content = newContent) else it
                        }
                    }
                }
            }
            .launchIn(scope)

        client.updatesOf<TdApi.UpdateMessageInteractionInfo>()
            .onEach { update ->
                val reactions = update.interactionInfo?.reactions.toDomainReactions()
                flowsOfChat(update.chatId).forEach { flow ->
                    flow.update { list ->
                        list.map {
                            if (it.id == update.messageId) it.copy(reactions = reactions) else it
                        }
                    }
                }
            }
            .launchIn(scope)

        client.updatesOf<TdApi.UpdateChatReadOutbox>()
            .onEach { update ->
                readOutbox[update.chatId] = update.lastReadOutboxMessageId
                flowsOfChat(update.chatId).forEach { flow ->
                    flow.update { list ->
                        list.map { msg ->
                            if (msg.isOutgoing && !msg.isRead && msg.id <= update.lastReadOutboxMessageId)
                                msg.copy(isRead = true)
                            else msg
                        }
                    }
                }
            }
            .launchIn(scope)

        client.updatesOf<TdApi.UpdateNewMessage>()
            .onEach { update ->
                val chatId = update.message.chatId
                val name = resolveSenderName(update.message)
                val flow = flowOf(chatId, topicOf(update.message))
                val domainMsg = resolveReplyPreview(
                    update.message.toDomain(name, readOutbox[chatId] ?: 0),
                    flow.value.associateBy { it.id },
                )
                flow.update { it + domainMsg }
                // Notify for incoming messages not in the currently open chat;
                // chats muted anywhere on the account stay silent on the watch too.
                if (!update.message.isOutgoing && chatId != openChatId && !isChatMuted(chatId)) {
                    val text = when (val c = domainMsg.content) {
                        is MessageContent.Text -> c.text
                        is MessageContent.Voice -> "🎤 Голосовое"
                        is MessageContent.Photo -> "📷 Фото"
                        is MessageContent.Video -> "📹 Видео"
                        is MessageContent.VideoNote -> "⭕ Кружок"
                        is MessageContent.Animation -> "GIF"
                        is MessageContent.Sticker -> c.emoji
                        is MessageContent.Document -> "📎 ${c.fileName}"
                        is MessageContent.Poll -> "📊 ${c.question}"
                        is MessageContent.Location -> "📍 Геопозиция"
                        is MessageContent.Venue -> "📍 ${c.title}"
                        is MessageContent.Contact -> "👤 ${c.name}"
                        is MessageContent.Dice -> c.emoji
                        is MessageContent.Call -> "📞 Звонок"
                        is MessageContent.AnimatedEmoji -> c.emoji
                        is MessageContent.Service -> c.text
                        is MessageContent.Unsupported -> "Сообщение"
                    }
                    showMessageNotification(name, text)
                }
            }
            .launchIn(scope)

        client.updatesOf<TdApi.UpdateMessageEdited>()
            .onEach { update ->
                val chatFlows = flowsOfChat(update.chatId)
                if (chatFlows.isEmpty()) return@onEach
                val refreshed = runCatching {
                    client.send(TdApi.GetMessage(update.chatId, update.messageId))
                }.getOrNull() ?: return@onEach
                val name = resolveSenderName(refreshed)
                val lastRead = readOutbox[update.chatId] ?: 0
                val newDomain = refreshed.toDomain(name, lastRead)

                // Edit history: TDLib never keeps old versions, so the copy we still
                // hold in the flow is the only chance to save what the text was.
                val oldText = chatFlows
                    .firstNotNullOfOrNull { f -> f.value.firstOrNull { it.id == update.messageId } }
                    ?.content?.let { textOf(it) }
                val newText = textOf(newDomain.content)
                if (!oldText.isNullOrBlank() && oldText != newText) {
                    scope.launch {
                        runCatching {
                            editDao.insert(
                                MessageEdit(
                                    messageId = update.messageId,
                                    chatId = update.chatId,
                                    oldText = oldText,
                                )
                            )
                        }
                    }
                }

                chatFlows.forEach { flow ->
                    flow.update { list ->
                        list.map { if (it.id == update.messageId) newDomain else it }
                    }
                }
            }
            .launchIn(scope)

        client.updatesOf<TdApi.UpdateDeleteMessages>()
            .onEach { update ->
                // TDLib fires this both for real deletions and for plain cache
                // eviction. Only isPermanent && !fromCache is an actual deletion;
                // fromCache=true just means TDLib unloaded the messages locally.
                if (!update.isPermanent || update.fromCache) return@onEach
                val chatFlows = flowsOfChat(update.chatId)
                if (chatFlows.isEmpty()) return@onEach
                val antiRevoke = runCatching { AntiRevokeManager.isEnabled.first() }.getOrDefault(true)
                val deleteNotify = runCatching { DeleteNotifyManager.isEnabled.first() }.getOrDefault(true)
                val deleted = update.messageIds.toHashSet()

                chatFlows.forEach { flow ->
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
                                if (deleteNotify && !msg.isOutgoing && msg.chatId != openChatId &&
                                    mutedChats[msg.chatId] != true
                                ) {
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
            }
            .launchIn(scope)
    }

    override fun messages(chatId: Long, topicId: Int): Flow<List<TgMessage>> =
        flowOf(chatId, topicId).asStateFlow()

    override suspend fun loadHistory(chatId: Long, fromMessageId: Long, limit: Int, topicId: Int) {
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
                if (topicId != 0)
                    client.send(TdApi.GetForumTopicHistory(chatId, topicId, fromMessageId, 0, limit))
                else
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
        val mapped = rawMessages
            .mapNotNull { msg ->
                val name = resolveSenderName(msg)
                runCatching { msg.toDomain(name, lastReadOutbox) }.getOrNull()
            }
            .reversed()
        val batchById = mapped.associateBy { it.id }
        val messages = mapped.map { resolveReplyPreview(it, batchById) }

        // Merge with any locally-deleted messages from Room
        val deletedInRoom = runCatching {
            dao.getForChat(chatId).first().associateBy { it.id }
        }.getOrDefault(emptyMap())

        val flow = flowOf(chatId, topicId)
        flow.update { existing ->
            val existingIds = existing.map { it.id }.toHashSet()
            val merged = messages.map { msg ->
                if (msg.id in deletedInRoom) msg.copy(deletedLocally = true) else msg
            }
            merged.filter { it.id !in existingIds } + existing
        }
    }

    override suspend fun sendText(chatId: Long, text: String, topicId: Int, replyToMessageId: Long) {
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
            if (topicId != 0) this.topicId = TdApi.MessageTopicForum(topicId)
            if (replyToMessageId != 0L) {
                this.replyTo = TdApi.InputMessageReplyToMessage().apply {
                    this.messageId = replyToMessageId
                }
            }
            this.inputMessageContent = content
        }
        client.send(request)
    }

    override suspend fun sendVoice(chatId: Long, filePath: String, durationSeconds: Int, topicId: Int) {
        val inputFile = TdApi.InputFileLocal(filePath)
        val voiceNote = TdApi.InputMessageVoiceNote().apply {
            this.voiceNote = inputFile
            this.duration = durationSeconds
            this.waveform = ByteArray(0)
        }
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            if (topicId != 0) this.topicId = TdApi.MessageTopicForum(topicId)
            this.inputMessageContent = voiceNote
        }
        client.send(request)
    }

    override suspend fun downloadPhoto(chatId: Long, messageId: Long, topicId: Int): String? {
        val flow = flowOf(chatId, topicId)
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

    private fun updateContent(
        chatId: Long,
        topicId: Int,
        messageId: Long,
        transform: (MessageContent) -> MessageContent,
    ) {
        flowOf(chatId, topicId).update { list ->
            list.map { if (it.id == messageId) it.copy(content = transform(it.content)) else it }
        }
    }

    override suspend fun downloadVideoThumb(chatId: Long, messageId: Long, topicId: Int): String? {
        val content = flowOf(chatId, topicId).value.firstOrNull { it.id == messageId }?.content
        val thumbFileId = when (content) {
            is MessageContent.Video -> content.thumbPath?.let { return it } ?: content.thumbFileId
            is MessageContent.VideoNote -> content.thumbPath?.let { return it } ?: content.thumbFileId
            is MessageContent.Animation -> content.thumbPath?.let { return it } ?: content.thumbFileId
            else -> return null
        }
        val path = fileDownloader.download(thumbFileId) ?: return null
        updateContent(chatId, topicId, messageId) { c ->
            when (c) {
                is MessageContent.Video -> c.copy(thumbPath = path)
                is MessageContent.VideoNote -> c.copy(thumbPath = path)
                is MessageContent.Animation -> c.copy(thumbPath = path)
                else -> c
            }
        }
        return path
    }

    override suspend fun downloadVideo(chatId: Long, messageId: Long, topicId: Int): String? {
        val content = flowOf(chatId, topicId).value.firstOrNull { it.id == messageId }?.content
        // Video sent as a file plays in the same player.
        if (content is MessageContent.Document && content.mimeType.startsWith("video/")) {
            return downloadDocument(chatId, messageId, topicId)
        }
        val fileId = when (content) {
            is MessageContent.Video -> content.localPath?.let { return it } ?: content.fileId
            is MessageContent.VideoNote -> content.localPath?.let { return it } ?: content.fileId
            is MessageContent.Animation -> content.localPath?.let { return it } ?: content.fileId
            else -> return null
        }
        val path = fileDownloader.download(fileId, FileDownloader.VIDEO_TIMEOUT_MS) ?: return null
        updateContent(chatId, topicId, messageId) { c ->
            when (c) {
                is MessageContent.Video -> c.copy(localPath = path)
                is MessageContent.VideoNote -> c.copy(localPath = path)
                is MessageContent.Animation -> c.copy(localPath = path)
                else -> c
            }
        }
        return path
    }

    override suspend fun downloadDocument(chatId: Long, messageId: Long, topicId: Int): String? {
        val doc = flowOf(chatId, topicId).value.firstOrNull { it.id == messageId }
            ?.content as? MessageContent.Document ?: return null
        doc.localPath?.let { return it }

        // Documents can be arbitrarily large — use the long (video) timeout.
        val path = fileDownloader.download(doc.fileId, FileDownloader.VIDEO_TIMEOUT_MS) ?: return null
        updateContent(chatId, topicId, messageId) { c ->
            if (c is MessageContent.Document) c.copy(localPath = path) else c
        }
        return path
    }

    override suspend fun downloadSticker(chatId: Long, messageId: Long, topicId: Int): String? {
        val sticker = flowOf(chatId, topicId).value.firstOrNull { it.id == messageId }
            ?.content as? MessageContent.Sticker ?: return null
        sticker.localPath?.let { return it }

        val path = fileDownloader.download(sticker.fileId) ?: return null
        updateContent(chatId, topicId, messageId) { c ->
            if (c is MessageContent.Sticker) c.copy(localPath = path) else c
        }
        return path
    }

    override suspend fun downloadVoice(chatId: Long, messageId: Long, topicId: Int): String? {
        val flow = flowOf(chatId, topicId)
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

    override suspend fun downloadPhotoFull(chatId: Long, messageId: Long, topicId: Int): String? {
        val content = flowOf(chatId, topicId).value.firstOrNull { it.id == messageId }?.content
        // Image sent as a file: the viewer opens the document itself.
        if (content is MessageContent.Document && content.mimeType.startsWith("image/")) {
            return downloadDocument(chatId, messageId, topicId)
        }
        val photo = content as? MessageContent.Photo ?: return null
        val fileId =
            if (AyugramSettings.isLowRamDevice || photo.fullFileId == 0) photo.fileId
            else photo.fullFileId
        // Fall back to the already-downloaded chat-size file rather than nothing.
        return fileDownloader.download(fileId) ?: photo.localPath
    }

    override suspend fun toggleReaction(chatId: Long, messageId: Long, emoji: String, topicId: Int) {
        val chosen = flowOf(chatId, topicId).value.firstOrNull { it.id == messageId }
            ?.reactions?.firstOrNull { it.emoji == emoji }?.isChosen == true
        // The resulting UpdateMessageInteractionInfo refreshes the bubble; the chat
        // may forbid this reaction (restricted set) — then the call just fails.
        runCatching {
            if (chosen) {
                client.send(TdApi.RemoveMessageReaction(chatId, messageId, TdApi.ReactionTypeEmoji(emoji)))
            } else {
                client.send(TdApi.AddMessageReaction(chatId, messageId, TdApi.ReactionTypeEmoji(emoji), false, true))
            }
        }
    }

    override suspend fun votePoll(chatId: Long, messageId: Long, optionIds: IntArray, topicId: Int) {
        // Multi-answer polls require the whole set in one call, so the UI decides
        // the final selection and we just submit it (empty = retract).
        runCatching { client.send(TdApi.SetPollAnswer(chatId, messageId, optionIds)) }
    }

    override suspend fun forwardMessage(toChatId: Long, fromChatId: Long, messageId: Long): Boolean =
        runCatching {
            client.send(
                TdApi.ForwardMessages().apply {
                    this.chatId = toChatId
                    this.fromChatId = fromChatId
                    this.messageIds = longArrayOf(messageId)
                }
            )
        }.isSuccess

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
