package su.kirian.wearayugram.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_messages")
data class DeletedMessage(
    @PrimaryKey val id: Long,
    val chatId: Long,
    val text: String,
    val senderName: String,
    val date: Long,
    val deletedAt: Long = System.currentTimeMillis()
)
