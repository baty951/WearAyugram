package su.kirian.wearayugram.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DeletedMessage::class, MessageEdit::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deletedMessageDao(): DeletedMessageDao
    abstract fun messageEditDao(): MessageEditDao

    companion object {
        // v2: edit-history table. Hand-written so anti-revoke data survives the upgrade.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_edits` (" +
                        "`editId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`messageId` INTEGER NOT NULL, " +
                        "`chatId` INTEGER NOT NULL, " +
                        "`oldText` TEXT NOT NULL, " +
                        "`editedAt` INTEGER NOT NULL)"
                )
            }
        }
    }
}
