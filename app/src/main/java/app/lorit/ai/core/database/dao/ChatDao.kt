package app.lorit.ai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.lorit.ai.core.database.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats WHERE is_temporary = 0 ORDER BY updated_at DESC")
    fun getAllChatsFlow(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY updated_at DESC")
    fun getAllIncludingTemporaryChatsFlow(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChatById(id: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE id = :id")
    fun getChatByIdFlow(id: String): Flow<ChatEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("UPDATE chats SET updated_at = :timestamp WHERE id = :id")
    suspend fun updateChatTimestamp(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET title = :title, updated_at = :timestamp WHERE id = :id")
    suspend fun updateChatTitle(id: String, title: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteChatById(id: String)

    @Query("DELETE FROM chats WHERE is_temporary = 1")
    suspend fun deleteTemporaryChats()

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()
}
