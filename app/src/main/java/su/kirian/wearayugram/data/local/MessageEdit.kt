package su.kirian.wearayugram.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// One pre-edit version of a message: saved right before the edited text replaces it.
@Entity(tableName = "message_edits")
data class MessageEdit(
    @PrimaryKey(autoGenerate = true) val editId: Long = 0,
    val messageId: Long,
    val chatId: Long,
    val oldText: String,
    val editedAt: Long = System.currentTimeMillis(),
)
