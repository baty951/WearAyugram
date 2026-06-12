package su.kirian.wearayugram.data.tdlib

import org.drinkless.tdlib.TdApi
import su.kirian.wearayugram.ayugram.LocalPremiumPatcher
import su.kirian.wearayugram.ayugram.LocalPremiumPatcher.withLocalPremium
import su.kirian.wearayugram.domain.model.ChatType
import su.kirian.wearayugram.domain.model.MessageContent
import su.kirian.wearayugram.domain.model.TgChat
import su.kirian.wearayugram.domain.model.TgChatFolder
import su.kirian.wearayugram.domain.model.TgMessage
import su.kirian.wearayugram.domain.model.TgPollOption
import su.kirian.wearayugram.domain.model.TgReaction
import su.kirian.wearayugram.domain.model.TgTopic
import su.kirian.wearayugram.domain.model.TgUser

// isMuted must be resolved by the caller (ChatMuteResolver): a chat with
// useDefaultMuteFor=true follows its category default, which a plain
// notificationSettings.muteFor check can't see.
fun TdApi.Chat.toDomain(isMuted: Boolean): TgChat = TgChat(
    id = id,
    title = title,
    lastMessage = lastMessage?.content?.toPreviewText(),
    lastMessageTime = lastMessage?.date?.toLong() ?: 0L,
    unreadCount = unreadCount,
    isMuted = isMuted,
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
    isRead = isOutgoing && id <= lastReadOutboxMessageId,
    reactions = interactionInfo?.reactions.toDomainReactions(),
    replyToMessageId = (replyTo as? TdApi.MessageReplyToMessage)?.messageId ?: 0,
    // Quote or inline content (TDLib attaches them when the original is remote);
    // otherwise the repository resolves the preview from the loaded history.
    replyPreview = (replyTo as? TdApi.MessageReplyToMessage)?.let { r ->
        r.quote?.text?.text?.takeIf { it.isNotBlank() } ?: r.content?.toPreviewText()
    }
)

// Custom-emoji reactions need sticker rendering, so only plain emoji ones are shown.
fun TdApi.MessageReactions?.toDomainReactions(): List<TgReaction> =
    this?.reactions.orEmpty().mapNotNull { r ->
        (r.type as? TdApi.ReactionTypeEmoji)?.let {
            TgReaction(emoji = it.emoji, count = r.totalCount, isChosen = r.isChosen)
        }
    }

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
    is TdApi.MessageVideoNote -> "⭕ Кружок"
    is TdApi.MessageVoiceNote -> "🎤 Голосовое"
    is TdApi.MessageAudio -> "🎵 Аудио"
    is TdApi.MessageDocument -> "📎 ${document.fileName}"
    is TdApi.MessageSticker -> "${sticker.emoji} Стикер"
    is TdApi.MessageAnimation -> "GIF"
    is TdApi.MessageCall -> "📞 Звонок"
    is TdApi.MessagePoll -> "📊 ${poll.question?.text ?: "Опрос"}"
    is TdApi.MessageLocation -> "📍 Геопозиция"
    is TdApi.MessageVenue -> "📍 ${venue.title}"
    is TdApi.MessageContact -> "👤 Контакт"
    is TdApi.MessageDice -> emoji
    is TdApi.MessageAnimatedEmoji -> emoji
    is TdApi.MessageContactRegistered -> "Вступил в Telegram"
    else -> "Сообщение"
}

fun TdApi.MessageContent.toDomainContent(): MessageContent = when (this) {
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
    is TdApi.MessageVideo -> MessageContent.Video(
        caption = caption?.text ?: "",
        durationSeconds = video.duration,
        fileId = video.video.id,
        thumbFileId = video.thumbnail?.file?.id ?: 0,
        width = video.width,
        height = video.height,
        miniThumb = video.minithumbnail?.data,
        thumbPath = video.thumbnail?.file?.local?.path
            ?.takeIf { video.thumbnail?.file?.local?.isDownloadingCompleted == true },
        localPath = video.video.local.path
            .takeIf { video.video.local.isDownloadingCompleted }
    )
    is TdApi.MessageAnimation -> MessageContent.Animation(
        caption = caption?.text ?: "",
        fileId = animation.animation.id,
        thumbFileId = animation.thumbnail?.file?.id ?: 0,
        width = animation.width,
        height = animation.height,
        miniThumb = animation.minithumbnail?.data,
        thumbPath = animation.thumbnail?.file?.local?.path
            ?.takeIf { animation.thumbnail?.file?.local?.isDownloadingCompleted == true },
        localPath = animation.animation.local.path
            .takeIf { animation.animation.local.isDownloadingCompleted }
    )
    is TdApi.MessageVideoNote -> MessageContent.VideoNote(
        durationSeconds = videoNote.duration,
        fileId = videoNote.video.id,
        thumbFileId = videoNote.thumbnail?.file?.id ?: 0,
        miniThumb = videoNote.minithumbnail?.data,
        thumbPath = videoNote.thumbnail?.file?.local?.path
            ?.takeIf { videoNote.thumbnail?.file?.local?.isDownloadingCompleted == true },
        localPath = videoNote.video.local.path
            .takeIf { videoNote.video.local.isDownloadingCompleted }
    )
    is TdApi.MessageSticker -> {
        // Static WEBP renders directly; animated TGS/WEBM would need a Lottie/video
        // pipeline, so they show their static thumbnail instead.
        val displayFile =
            if (sticker.format is TdApi.StickerFormatWebp) sticker.sticker
            else sticker.thumbnail?.file
        MessageContent.Sticker(
            emoji = sticker.emoji,
            fileId = displayFile?.id ?: 0,
            width = sticker.width,
            height = sticker.height,
            localPath = displayFile?.local?.path
                ?.takeIf { displayFile.local.isDownloadingCompleted }
        )
    }
    is TdApi.MessageDocument -> MessageContent.Document(
        fileName = document.fileName,
        mimeType = document.mimeType,
        sizeBytes = document.document.size.takeIf { it > 0 } ?: document.document.expectedSize,
        fileId = document.document.id,
        localPath = document.document.local.path
            .takeIf { document.document.local.isDownloadingCompleted }
    )
    is TdApi.MessagePoll -> MessageContent.Poll(
        question = poll.question?.text ?: "",
        options = poll.options.map {
            TgPollOption(
                text = it.text?.text ?: "",
                votePercentage = it.votePercentage,
                isChosen = it.isChosen,
            )
        },
        totalVoterCount = poll.totalVoterCount,
        isAnonymous = poll.isAnonymous,
        isQuiz = poll.type is TdApi.PollTypeQuiz,
        isClosed = poll.isClosed,
        allowsMultipleAnswers = poll.allowsMultipleAnswers,
    )
    is TdApi.MessageLocation -> MessageContent.Location(
        latitude = location.latitude,
        longitude = location.longitude,
        isLive = livePeriod > 0,
    )
    is TdApi.MessageVenue -> MessageContent.Venue(venue.title, venue.address)
    is TdApi.MessageContact -> MessageContent.Contact(
        name = "${contact.firstName} ${contact.lastName}".trim(),
        phoneNumber = contact.phoneNumber,
    )
    is TdApi.MessageDice -> MessageContent.Dice(emoji, value)
    is TdApi.MessageCall -> MessageContent.Call(isVideo, duration)
    is TdApi.MessageAnimatedEmoji -> MessageContent.AnimatedEmoji(emoji)
    // Service events — text only, rendered as a centered system line.
    is TdApi.MessageChatChangeTitle -> MessageContent.Service("Название: $title")
    is TdApi.MessageChatChangePhoto -> MessageContent.Service("Фото чата обновлено")
    is TdApi.MessageChatAddMembers -> MessageContent.Service("Участники добавлены")
    is TdApi.MessageChatJoinByLink -> MessageContent.Service("Вступление по ссылке")
    is TdApi.MessageChatJoinByRequest -> MessageContent.Service("Заявка на вступление принята")
    is TdApi.MessageChatDeleteMember -> MessageContent.Service("Участник покинул чат")
    is TdApi.MessagePinMessage -> MessageContent.Service("Сообщение закреплено")
    is TdApi.MessageBasicGroupChatCreate -> MessageContent.Service("Группа создана")
    is TdApi.MessageSupergroupChatCreate -> MessageContent.Service("Группа создана")
    is TdApi.MessageForumTopicCreated -> MessageContent.Service("Тема создана: $name")
    is TdApi.MessageForumTopicEdited ->
        MessageContent.Service(if (name.isNotEmpty()) "Тема переименована: $name" else "Тема изменена")
    is TdApi.MessageVideoChatStarted -> MessageContent.Service("Видеочат начат")
    is TdApi.MessageVideoChatEnded -> MessageContent.Service("Видеочат завершён")
    is TdApi.MessageContactRegistered -> MessageContent.Service("Вступление в Telegram")
    is TdApi.MessageScreenshotTaken -> MessageContent.Service("Сделан скриншот")
    is TdApi.MessageChatSetMessageAutoDeleteTime -> MessageContent.Service("Таймер автоудаления изменён")
    else -> MessageContent.Unsupported
}

private fun TdApi.ChatType.toChatType(): ChatType = when (this) {
    is TdApi.ChatTypePrivate -> ChatType.Private
    is TdApi.ChatTypeBasicGroup -> ChatType.Group
    is TdApi.ChatTypeSupergroup -> if (isChannel) ChatType.Channel else ChatType.Supergroup
    is TdApi.ChatTypeSecret -> ChatType.Private
    else -> ChatType.Private
}
