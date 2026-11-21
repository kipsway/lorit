package app.lorit.ai.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "active_provider")
    val activeProvider: String,

    @ColumnInfo(name = "active_model_id")
    val activeModelId: String,

    @ColumnInfo(name = "is_temporary", defaultValue = "0")
    val isTemporary: Boolean = false
)
