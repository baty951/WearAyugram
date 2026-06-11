package su.kirian.wearayugram.domain.model

import androidx.compose.runtime.Immutable

// A saved pre-edit version of a message (anti-edit history, AyuGram-style).
@Immutable
data class TgMessageEdit(
    val id: Long,
    val text: String,
    val editedAt: Long,
)
