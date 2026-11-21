package app.lorit.ai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.lorit.ai.core.database.entity.BrowserTabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserTabDao {

    @Query("SELECT * FROM browser_tabs WHERE chat_id = :chatId ORDER BY order_index ASC")
    fun getTabsForChatFlow(chatId: String): Flow<List<BrowserTabEntity>>

    @Query("SELECT * FROM browser_tabs WHERE chat_id = :chatId ORDER BY order_index ASC")
    suspend fun getTabsForChat(chatId: String): List<BrowserTabEntity>

    @Query("SELECT * FROM browser_tabs WHERE chat_id = :chatId AND is_active = 1 LIMIT 1")
    suspend fun getActiveTab(chatId: String): BrowserTabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: BrowserTabEntity)

    @Update
    suspend fun updateTab(tab: BrowserTabEntity)

    @Query("UPDATE browser_tabs SET is_active = CASE WHEN id = :tabId THEN 1 ELSE 0 END WHERE chat_id = :chatId")
    suspend fun setActiveTab(chatId: String, tabId: String)

    @Query("DELETE FROM browser_tabs WHERE id = :id")
    suspend fun deleteTabById(id: String)

    @Query("DELETE FROM browser_tabs WHERE chat_id = :chatId")
    suspend fun clearTabsForChat(chatId: String)
}
