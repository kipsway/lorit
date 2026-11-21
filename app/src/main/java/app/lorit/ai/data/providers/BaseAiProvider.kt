package app.lorit.ai.data.providers

import app.lorit.ai.domain.model.Attachment
import app.lorit.ai.domain.model.Message
import app.lorit.ai.domain.model.ModelInfo
import app.lorit.ai.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow

/**
 * События потоковой генерации (Server-Sent Events / SSE).
 */
sealed class StreamChunk {
    data class Thinking(val text: String) : StreamChunk()
    data class Content(val text: String) : StreamChunk()
    data class Completed(val fullContent: String, val thinking: String? = null, val durationMs: Long = 0L) : StreamChunk()
    data class Error(val message: String, val isQuotaExceeded: Boolean = false, val throwable: Throwable? = null) : StreamChunk()
}

data class QuotaInfo(
    val usedPercentage: Int,
    val resetTimeTimestamp: Long? = null,
    val remainingMessages: Int? = null
)

/**
 * Базовый контракт для всех провайдеров нейросетей (API и Web-сессий).
 */
interface BaseAiProvider {
    val providerType: ProviderType

    /**
     * Потоковая генерация ответа модели через SSE.
     */
    fun streamChat(
        messages: List<Message>,
        modelId: String,
        attachments: List<Attachment> = emptyList(),
        systemPrompt: String? = null
    ): Flow<StreamChunk>

    /**
     * 100% динамический опрос доступных моделей учетной записи пользователя.
     */
    suspend fun fetchAvailableModels(): List<ModelInfo>

    /**
     * Опрос текущего состояния квот и лимитов (если поддерживается провайдером).
     */
    suspend fun getQuotaStatus(modelId: String): QuotaInfo?
}
