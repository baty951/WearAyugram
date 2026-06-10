package su.kirian.wearayugram.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DeletedMessage::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deletedMessageDao(): DeletedMessageDao
}
