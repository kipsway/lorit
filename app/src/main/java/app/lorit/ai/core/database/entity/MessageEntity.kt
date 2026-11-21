package app.lorit.ai.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chat_id"]),
        Index(value = ["status"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "chat_id")
    val chatId: String,

    @ColumnInfo(name = "sender")
    val sender: String, // 'USER' | 'ASSISTANT' | 'SYSTEM'

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "thinking_process")
    val thinkingProcess: String? = null,

    @ColumnInfo(name = "thinking_duration_ms", defaultValue = "0")
    val thinkingDurationMs: Long = 0L,

    @ColumnInfo(name = "status")
    val status: String, // 'PENDING' | 'STREAMING' | 'COMPLETED' | 'ERROR' | 'OUTBOX_QUEUED'

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
