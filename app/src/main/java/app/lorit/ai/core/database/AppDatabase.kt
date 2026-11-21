package app.lorit.ai.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.lorit.ai.core.database.dao.AttachmentDao
import app.lorit.ai.core.database.dao.BrowserTabDao
import app.lorit.ai.core.database.dao.ChatDao
import app.lorit.ai.core.database.dao.MessageDao
import app.lorit.ai.core.database.entity.AttachmentEntity
import app.lorit.ai.core.database.entity.BrowserTabEntity
import app.lorit.ai.core.database.entity.ChatEntity
import app.lorit.ai.core.database.entity.MessageEntity
import app.lorit.ai.core.database.entity.MessageFtsEntity

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class,
        AttachmentEntity::class,
        BrowserTabEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun browserTabDao(): BrowserTabDao
}
