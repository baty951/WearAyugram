package su.kirian.wearayugram.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageEditDao {
    @Insert
    suspend fun insert(edit: MessageEdit)

    @Query("SELECT * FROM message_edits WHERE messageId = :messageId ORDER BY editedAt ASC")
    fun getForMessage(messageId: Long): Flow<List<MessageEdit>>
}
