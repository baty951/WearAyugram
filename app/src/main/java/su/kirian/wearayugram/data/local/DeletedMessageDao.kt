package su.kirian.wearayugram.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: DeletedMessage)

    @Query("SELECT * FROM deleted_messages WHERE chatId = :chatId ORDER BY date ASC")
    fun getForChat(chatId: Long): Flow<List<DeletedMessage>>

    @Query("SELECT * FROM deleted_messages WHERE id = :id")
    suspend fun getById(id: Long): DeletedMessage?
}
