package app.lorit.ai.data.repository

import app.lorit.ai.core.database.DatabaseManager
import app.lorit.ai.core.database.entity.AttachmentEntity
import app.lorit.ai.core.database.entity.BrowserTabEntity
import app.lorit.ai.core.database.entity.ChatEntity
import app.lorit.ai.core.database.entity.MessageEntity
import app.lorit.ai.domain.model.Attachment
import app.lorit.ai.domain.model.AttachmentType
import app.lorit.ai.domain.model.BrowserTab
import app.lorit.ai.domain.model.Chat
import app.lorit.ai.domain.model.Message
import app.lorit.ai.domain.model.MessageSender
import app.lorit.ai.domain.model.MessageStatus
import app.lorit.ai.domain.model.ProviderType
import app.lorit.ai.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class ChatRepositoryImpl(
    private val databaseManager: DatabaseManager
) : ChatRepository {

    private val db get() = databaseManager.getEncryptedDatabase()

    override fun getAllChatsFlow(): Flow<List<Chat>> {
        return db.chatDao().getAllChatsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getChatById(chatId: String): Chat? {
        val entity = db.chatDao().getChatById(chatId) ?: return null
        return entity.toDomain()
    }

    override fun getChatFlow(chatId: String): Flow<Chat?> {
        return db.chatDao().getChatByIdFlow(chatId).map { it?.toDomain() }
    }

    override suspend fun createChat(
        title: String,
        provider: ProviderType,
        modelId: String,
        isTemporary: Boolean
    ): Chat {
        val targetDb = databaseManager.getDatabaseFor(isTemporary)
        val now = System.currentTimeMillis()
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            activeProvider = provider.name,
            activeModelId = modelId,
            isTemporary = isTemporary
        )
        targetDb.chatDao().insertChat(chat)
        return chat.toDomain()
    }

    override suspend fun updateChatTitle(chatId: String, title: String) {
        db.chatDao().updateChatTitle(chatId, title)
    }

    override suspend fun deleteChat(chatId: String) {
        db.chatDao().deleteChatById(chatId)
    }

    override suspend fun purgeIncognitoSession() {
        databaseManager.purgeInMemoryDatabase()
    }

    override fun getMessagesFlow(chatId: String): Flow<List<Message>> {
        val messagesFlow = db.messageDao().getMessagesForChatFlow(chatId)
        return messagesFlow.map { list ->
            list.map { msgEntity ->
                val attachments = db.attachmentDao().getAttachmentsForMessage(msgEntity.id)
                msgEntity.toDomain(attachments.map { it.toDomain() })
            }
        }
    }

    override suspend fun getMessages(chatId: String): List<Message> {
        val list = db.messageDao().getMessagesForChat(chatId)
        return list.map { msgEntity ->
            val attachments = db.attachmentDao().getAttachmentsForMessage(msgEntity.id)
            msgEntity.toDomain(attachments.map { it.toDomain() })
        }
    }

    override suspend fun insertUserMessage(
        chatId: String,
        content: String,
        attachments: List<Attachment>,
        status: MessageStatus
    ): Message {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val msgEntity = MessageEntity(
            id = messageId,
            chatId = chatId,
            sender = MessageSender.USER.name,
            content = content,
            status = status.name,
            createdAt = now
        )
        db.messageDao().insertMessage(msgEntity)

        if (attachments.isNotEmpty()) {
            val attachmentEntities = attachments.map { att ->
                AttachmentEntity(
                    id = att.id.ifEmpty { UUID.randomUUID().toString() },
                    messageId = messageId,
                    type = att.type.name,
                    displayName = att.displayName,
                    rawContent = att.rawContent,
                    localFileUri = att.localFileUri,
                    fileSizeBytes = att.fileSizeBytes
                )
            }
            db.attachmentDao().insertAttachments(attachmentEntities)
        }

        db.chatDao().updateChatTimestamp(chatId, now)
        return msgEntity.toDomain(attachments)
    }

    override suspend fun insertAssistantPlaceholder(
        chatId: String,
        initialContent: String,
        status: MessageStatus
    ): Message {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val msgEntity = MessageEntity(
            id = messageId,
            chatId = chatId,
            sender = MessageSender.ASSISTANT.name,
            content = initialContent,
            status = status.name,
            createdAt = now
        )
        db.messageDao().insertMessage(msgEntity)
        db.chatDao().updateChatTimestamp(chatId, now)
        return msgEntity.toDomain(emptyList())
    }

    override suspend fun updateMessageStream(
        messageId: String,
        content: String,
        status: MessageStatus
    ) {
        db.messageDao().updateMessageContent(messageId, content, status.name)
    }

    override suspend fun updateMessageThinking(
        messageId: String,
        thinking: String,
        durationMs: Long
    ) {
        db.messageDao().updateMessageThinking(messageId, thinking, durationMs)
    }

    override suspend fun getOutboxMessages(): List<Message> {
        val entities = db.messageDao().getOutboxMessages()
        return entities.map { msg ->
            val attachments = db.attachmentDao().getAttachmentsForMessage(msg.id).map { it.toDomain() }
            msg.toDomain(attachments)
        }
    }

    override fun searchMessages(query: String): Flow<List<Message>> {
        val cleanQuery = query.trim()
        val ftsQuery = if (cleanQuery.endsWith("*")) cleanQuery else "$cleanQuery*"
        return db.messageDao().searchMessagesFts(ftsQuery).map { list ->
            list.map { it.toDomain(emptyList()) }
        }
    }

    override fun getBrowserTabsFlow(chatId: String): Flow<List<BrowserTab>> {
        return db.browserTabDao().getTabsForChatFlow(chatId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun addBrowserTab(chatId: String, url: String, title: String): BrowserTab {
        val existing = db.browserTabDao().getTabsForChat(chatId)
        val tabId = UUID.randomUUID().toString()
        val newOrder = existing.size
        val tabEntity = BrowserTabEntity(
            id = tabId,
            chatId = chatId,
            url = url,
            title = title,
            isActive = true,
            orderIndex = newOrder
        )
        db.browserTabDao().setActiveTab(chatId, "") // сброс активного
        db.browserTabDao().insertTab(tabEntity)
        return tabEntity.toDomain()
    }

    override suspend fun setActiveBrowserTab(chatId: String, tabId: String) {
        db.browserTabDao().setActiveTab(chatId, tabId)
    }

    override suspend fun closeBrowserTab(tabId: String) {
        db.browserTabDao().deleteTabById(tabId)
    }

    // Mappers
    private fun ChatEntity.toDomain() = Chat(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        activeProvider = ProviderType.fromString(activeProvider),
        activeModelId = activeModelId,
        isTemporary = isTemporary
    )

    private fun MessageEntity.toDomain(attachments: List<Attachment>) = Message(
        id = id,
        chatId = chatId,
        sender = MessageSender.fromString(sender),
        content = content,
        thinkingProcess = thinkingProcess,
        thinkingDurationMs = thinkingDurationMs,
        status = MessageStatus.fromString(status),
        createdAt = createdAt,
        attachments = attachments
    )

    private fun AttachmentEntity.toDomain() = Attachment(
        id = id,
        messageId = messageId,
        type = AttachmentType.fromString(type),
        displayName = displayName,
        rawContent = rawContent,
        localFileUri = localFileUri,
        fileSizeBytes = fileSizeBytes
    )

    private fun BrowserTabEntity.toDomain() = BrowserTab(
        id = id,
        chatId = chatId,
        url = url,
        title = title,
        isActive = isActive,
        orderIndex = orderIndex
    )
}
