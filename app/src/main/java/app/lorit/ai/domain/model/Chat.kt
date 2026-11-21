package app.lorit.ai.domain.model

data class Attachment(
    val id: String,
    val messageId: String,
    val type: AttachmentType,
    val displayName: String,
    val rawContent: String? = null,
    val localFileUri: String? = null,
    val fileSizeBytes: Long = 0L
)

data class Message(
    val id: String,
    val chatId: String,
    val sender: MessageSender,
    val content: String,
    val thinkingProcess: String? = null,
    val thinkingDurationMs: Long = 0L,
    val status: MessageStatus = MessageStatus.COMPLETED,
    val createdAt: Long = System.currentTimeMillis(),
    val attachments: List<Attachment> = emptyList()
)

data class BrowserTab(
    val id: String,
    val chatId: String,
    val url: String,
    val title: String,
    val isActive: Boolean = false,
    val orderIndex: Int = 0
)

data class Chat(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val activeProvider: ProviderType = ProviderType.OPENAI,
    val activeModelId: String = "gpt-4o",
    val isTemporary: Boolean = false,
    val messages: List<Message> = emptyList(),
    val browserTabs: List<BrowserTab> = emptyList()
)

data class ModelInfo(
    val id: String,
    val name: String,
    val provider: ProviderType,
    val contextTokenLimit: Int = 128000,
    val supportsReasoning: Boolean = false,
    val quotaUsedPercentage: Int? = null,
    val isOfficialWebSession: Boolean = false
)
