package app.lorit.ai.domain.repository

import app.lorit.ai.domain.model.Attachment
import app.lorit.ai.domain.model.BrowserTab
import app.lorit.ai.domain.model.Chat
import app.lorit.ai.domain.model.Message
import app.lorit.ai.domain.model.MessageStatus
import app.lorit.ai.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllChatsFlow(): Flow<List<Chat>>
    suspend fun getChatById(chatId: String): Chat?
    fun getChatFlow(chatId: String): Flow<Chat?>
    suspend fun createChat(
        title: String,
        provider: ProviderType,
        modelId: String,
        isTemporary: Boolean = false
    ): Chat
    suspend fun updateChatTitle(chatId: String, title: String)
    suspend fun deleteChat(chatId: String)
    suspend fun purgeIncognitoSession()

    fun getMessagesFlow(chatId: String): Flow<List<Message>>
    suspend fun getMessages(chatId: String): List<Message>
    suspend fun insertUserMessage(
        chatId: String,
        content: String,
        attachments: List<Attachment> = emptyList(),
        status: MessageStatus = MessageStatus.COMPLETED
    ): Message
    suspend fun insertAssistantPlaceholder(
        chatId: String,
        initialContent: String = "",
        status: MessageStatus = MessageStatus.PENDING
    ): Message
    suspend fun updateMessageStream(
        messageId: String,
        content: String,
        status: MessageStatus
    )
    suspend fun updateMessageThinking(
        messageId: String,
        thinking: String,
        durationMs: Long
    )
    suspend fun getOutboxMessages(): List<Message>
    fun searchMessages(query: String): Flow<List<Message>>

    fun getBrowserTabsFlow(chatId: String): Flow<List<BrowserTab>>
    suspend fun addBrowserTab(chatId: String, url: String, title: String): BrowserTab
    suspend fun setActiveBrowserTab(chatId: String, tabId: String)
    suspend fun closeBrowserTab(tabId: String)
}
