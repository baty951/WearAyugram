package su.kirian.wearayugram.domain.model

import androidx.compose.runtime.Immutable

// @Immutable lets Compose skip recomposition of unchanged list items.
@Immutable
data class TgMessage(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val senderName: String,
    val content: MessageContent,
    val date: Long,
    val isOutgoing: Boolean,
    val isEdited: Boolean,
    // For outgoing messages: true once the recipient has read it (✓✓ vs ✓)
    val isRead: Boolean = false,
    // null = never deleted, non-null = deleted locally by anti-revoke
    val deletedLocally: Boolean = false,
)

@Immutable
sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    data class Voice(
        val durationSeconds: Int,
        val fileId: Int,
        val localPath: String?,
    ) : MessageContent()
    // width/height are the dimensions of the chat-size variant: the bubble draws a
    // placeholder with the same aspect ratio so item height doesn't jump on load.
    @Suppress("ArrayInDataClass")
    data class Photo(
        val caption: String,
        val fileId: Int,
        val fullFileId: Int,
        val width: Int,
        val height: Int,
        val miniThumb: ByteArray?,
        val localPath: String?,
    ) : MessageContent()
    // thumbPath — downloaded video thumbnail (small JPEG) for a sharp bubble preview;
    // miniThumb — instant blurry placeholder until it arrives.
    @Suppress("ArrayInDataClass")
    data class Video(
        val caption: String,
        val durationSeconds: Int,
        val fileId: Int,
        val thumbFileId: Int,
        val width: Int,
        val height: Int,
        val miniThumb: ByteArray?,
        val thumbPath: String?,
        val localPath: String?,
    ) : MessageContent()

    @Suppress("ArrayInDataClass")
    data class VideoNote(
        val durationSeconds: Int,
        val fileId: Int,
        val thumbFileId: Int,
        val miniThumb: ByteArray?,
        val thumbPath: String?,
        val localPath: String?,
    ) : MessageContent()

    // fileId points at the displayable file: the WEBP sticker itself for static
    // stickers, or the static thumbnail for animated (TGS/WEBM) ones.
    data class Sticker(
        val emoji: String,
        val fileId: Int,
        val width: Int,
        val height: Int,
        val localPath: String?,
    ) : MessageContent()
    data class Document(
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val fileId: Int,
        val localPath: String?,
    ) : MessageContent()
    data object Unsupported : MessageContent()
}
