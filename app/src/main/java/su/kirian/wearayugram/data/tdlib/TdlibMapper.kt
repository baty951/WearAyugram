package su.kirian.wearayugram.data.tdlib

import org.drinkless.tdlib.TdApi
import su.kirian.wearayugram.ayugram.LocalPremiumPatcher
import su.kirian.wearayugram.ayugram.LocalPremiumPatcher.withLocalPremium
import su.kirian.wearayugram.domain.model.ChatType
import su.kirian.wearayugram.domain.model.MessageContent
import su.kirian.wearayugram.domain.model.TgChat
import su.kirian.wearayugram.domain.model.TgChatFolder
import su.kirian.wearayugram.domain.model.TgMessage
import su.kirian.wearayugram.domain.model.TgTopic
import su.kirian.wearayugram.domain.model.TgUser

fun TdApi.Chat.toDomain(): TgChat = TgChat(
    id = id,
    title = title,
    lastMessage = lastMessage?.content?.toPreviewText(),
    lastMessageTime = lastMessage?.date?.toLong() ?: 0L,
    unreadCount = unreadCount,
    isMuted = notificationSettings.muteFor > 0,
    photoPath = photo?.small?.local?.path
        ?.takeIf { photo?.small?.local?.isDownloadingCompleted == true },
    type = type.toChatType()
)

fun TdApi.Message.toDomain(senderName: String, lastReadOutboxMessageId: Long = 0): TgMessage = TgMessage(
    id = id,
    chatId = chatId,
    senderId = (senderId as? TdApi.MessageSenderUser)?.userId ?: 0L,
    senderName = senderName,
    content = content.toDomainContent(),
    date = date.toLong(),
    isOutgoing = isOutgoing,
    isEdited = editDate > 0,
    isRead = isOutgoing && id <= lastReadOutboxMessageId
)

fun TdApi.ForumTopic.toDomain(): TgTopic = TgTopic(
    id = info.forumTopicId,
    chatId = info.chatId,
    title = info.name,
    unreadCount = unreadCount,
    lastMessage = lastMessage?.content?.toPreviewText(),
    lastMessageTime = lastMessage?.date?.toLong() ?: 0L,
    isPinned = isPinned,
    isGeneral = info.isGeneral,
)

fun TdApi.ChatFolderInfo.toDomain(): TgChatFolder = TgChatFolder(
    id = id,
    title = name?.text?.text ?: ""
)

fun TdApi.User.toDomain(): TgUser = TgUser(
    id = id,
    firstName = firstName,
    lastName = lastName,
    username = usernames?.activeUsernames?.firstOrNull() ?: "",
    phoneNumber = phoneNumber,
    isBot = type is TdApi.UserTypeBot,
    isPremium = isPremium,
    isVerified = false,
    profilePhotoPath = profilePhoto?.small?.local?.path
        ?.takeIf { profilePhoto?.small?.local?.isDownloadingCompleted == true }
).withLocalPremium()

fun TdApi.MessageContent.toPreviewText(): String = when (this) {
    is TdApi.MessageText -> text.text.take(80)
    is TdApi.MessagePhoto -> "📷 Фото"
    is TdApi.MessageVideo -> "📹 Видео"
    is TdApi.MessageVoiceNote -> "🎤 Голосовое"
    is TdApi.MessageAudio -> "🎵 Аудио"
    is TdApi.MessageDocument -> "📎 ${document.fileName}"
    is TdApi.MessageSticker -> "${sticker.emoji} Стикер"
    is TdApi.MessageAnimation -> "GIF"
    is TdApi.MessageCall -> "📞 Звонок"
    is TdApi.MessageContactRegistered -> "Вступил в Telegram"
    else -> "Сообщение"
}

private fun TdApi.MessageContent.toDomainContent(): MessageContent = when (this) {
    is TdApi.MessageText -> MessageContent.Text(text.text)
    is TdApi.MessageVoiceNote -> MessageContent.Voice(
        durationSeconds = voiceNote.duration,
        fileId = voiceNote.voice.id,
        localPath = voiceNote.voice.local.path
            .takeIf { voiceNote.voice.local.isDownloadingCompleted }
    )
    is TdApi.MessagePhoto -> {
        // Watch screens are ~450px: the smallest size >= 320 ("m") is enough for the
        // bubble; >= 800 ("x") for the fullscreen viewer. lastOrNull() would pick the
        // original-size variant — megabytes over the watch's connection.
        val sorted = photo.sizes.sortedBy { it.width }
        val chatSize = sorted.firstOrNull { it.width >= 320 } ?: sorted.lastOrNull()
        val fullSize = sorted.firstOrNull { it.width >= 800 } ?: sorted.lastOrNull()
        MessageContent.Photo(
            caption = caption?.text ?: "",
            fileId = chatSize?.photo?.id ?: 0,
            fullFileId = fullSize?.photo?.id ?: 0,
            width = chatSize?.width ?: 0,
            height = chatSize?.height ?: 0,
            miniThumb = photo.minithumbnail?.data,
            localPath = chatSize?.photo?.local?.path
                ?.takeIf { chatSize.photo.local.isDownloadingCompleted }
        )
    }
    is TdApi.MessageSticker -> MessageContent.Sticker(
        emoji = sticker.emoji,
        localPath = sticker.sticker.local.path
            .takeIf { sticker.sticker.local.isDownloadingCompleted }
    )
    is TdApi.MessageDocument -> MessageContent.Document(
        fileName = document.fileName,
        mimeType = document.mimeType
    )
    else -> MessageContent.Unsupported
}

private fun TdApi.ChatType.toChatType(): ChatType = when (this) {
    is TdApi.ChatTypePrivate -> ChatType.Private
    is TdApi.ChatTypeBasicGroup -> ChatType.Group
    is TdApi.ChatTypeSupergroup -> if (isChannel) ChatType.Channel else ChatType.Supergroup
    is TdApi.ChatTypeSecret -> ChatType.Private
    else -> ChatType.Private
}
