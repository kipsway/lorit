package app.lorit.ai.domain.usecase

import app.lorit.ai.core.network.NetworkMonitor
import app.lorit.ai.core.outbox.OutboxQueueManager
import app.lorit.ai.data.providers.BaseAiProvider
import app.lorit.ai.data.providers.StreamChunk
import app.lorit.ai.domain.model.Attachment
import app.lorit.ai.domain.model.Message
import app.lorit.ai.domain.model.MessageSender
import app.lorit.ai.domain.model.MessageStatus
import app.lorit.ai.domain.model.ProviderType
import app.lorit.ai.domain.prompt.SmartContextPruner
import app.lorit.ai.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class SendMessageEvent {
    data class QueuedOffline(val message: Message) : SendMessageEvent()
    data class UserMessageSaved(val message: Message) : SendMessageEvent()
    data class AssistantMessageCreated(val messageId: String) : SendMessageEvent()
    data class ThinkingUpdate(val text: String) : SendMessageEvent()
    data class ContentUpdate(val text: String) : SendMessageEvent()
    data class ModelDowngraded(val originalModel: String, val fallbackModel: String, val reason: String) : SendMessageEvent()
    data class Completed(val message: Message, val durationMs: Long) : SendMessageEvent()
    data class Error(val error: String) : SendMessageEvent()
}

class SendMessageUseCase(
    private val chatRepository: ChatRepository,
    private val networkMonitor: NetworkMonitor,
    private val outboxQueueManager: OutboxQueueManager,
    private val providerFactory: AiProviderFactory
) {

    fun execute(
        chatId: String,
        userText: String,
        attachments: List<Attachment> = emptyList(),
        providerType: ProviderType,
        modelId: String,
        contextTokenLimit: Int = 128000,
        systemPrompt: String? = null
    ): Flow<SendMessageEvent> = flow {
        // Проверяем доступность сети
        if (!networkMonitor.isOnline.value) {
            val queuedMessage = outboxQueueManager.enqueueMessage(chatId, userText, attachments)
            emit(SendMessageEvent.QueuedOffline(queuedMessage))
            return@flow
        }

        // Сохраняем сообщение пользователя
        val userMessage = chatRepository.insertUserMessage(
            chatId = chatId,
            content = userText,
            attachments = attachments,
            status = MessageStatus.COMPLETED
        )
        emit(SendMessageEvent.UserMessageSaved(userMessage))

        // Создаем плейсхолдер для ответа ассистента
        val assistantMessage = chatRepository.insertAssistantPlaceholder(
            chatId = chatId,
            initialContent = "",
            status = MessageStatus.STREAMING
        )
        emit(SendMessageEvent.AssistantMessageCreated(assistantMessage.id))

        // Извлекаем историю диалога и применяем Smart Context Pruning
        val rawHistory = chatRepository.getMessages(chatId)
        val prunedHistory = SmartContextPruner.pruneMessages(
            history = rawHistory,
            targetContextTokenLimit = contextTokenLimit,
            currentAttachments = attachments
        )

        val provider = providerFactory.getProvider(providerType)
        var currentModel = modelId
        var fullContent = StringBuilder()
        var fullThinking = StringBuilder()
        var totalDuration = 0L

        var streamFlow = provider.streamChat(
            messages = prunedHistory,
            modelId = currentModel,
            attachments = attachments,
            systemPrompt = systemPrompt
        )

        var streamSucceeded = false
        var retryWithFallback = false

        streamFlow.collect { chunk ->
            when (chunk) {
                is StreamChunk.Thinking -> {
                    fullThinking.append(chunk.text)
                    emit(SendMessageEvent.ThinkingUpdate(chunk.text))
                }
                is StreamChunk.Content -> {
                    fullContent.append(chunk.text)
                    emit(SendMessageEvent.ContentUpdate(chunk.text))
                }
                is StreamChunk.Completed -> {
                    totalDuration += chunk.durationMs
                    streamSucceeded = true
                }
                is StreamChunk.Error -> {
                    if (chunk.isQuotaExceeded) {
                        // Активируем авто-даунгрейд модели
                        val fallback = providerFactory.getFallbackModel(providerType, currentModel)
                        if (fallback != currentModel) {
                            emit(
                                SendMessageEvent.ModelDowngraded(
                                    originalModel = currentModel,
                                    fallbackModel = fallback,
                                    reason = chunk.message
                                )
                            )
                            currentModel = fallback
                            retryWithFallback = true
                        } else {
                            emit(SendMessageEvent.Error(chunk.message))
                        }
                    } else {
                        emit(SendMessageEvent.Error(chunk.message))
                    }
                }
            }
        }

        // Если исчерпан лимит и доступен даунгрейд — допечатываем ответ на резервной модели
        if (retryWithFallback) {
            val fallbackFlow = provider.streamChat(
                messages = prunedHistory,
                modelId = currentModel,
                attachments = attachments,
                systemPrompt = systemPrompt
            )
            fallbackFlow.collect { chunk ->
                when (chunk) {
                    is StreamChunk.Thinking -> {
                        fullThinking.append(chunk.text)
                        emit(SendMessageEvent.ThinkingUpdate(chunk.text))
                    }
                    is StreamChunk.Content -> {
                        fullContent.append(chunk.text)
                        emit(SendMessageEvent.ContentUpdate(chunk.text))
                    }
                    is StreamChunk.Completed -> {
                        totalDuration += chunk.durationMs
                        streamSucceeded = true
                    }
                    is StreamChunk.Error -> {
                        emit(SendMessageEvent.Error(chunk.message))
                    }
                }
            }
        }

        val finalContent = fullContent.toString()
        val finalThinking = fullThinking.toString().ifEmpty { null }

        // Фиксируем финальный результат в зашифрованной БД
        chatRepository.updateMessageStream(
            messageId = assistantMessage.id,
            content = finalContent,
            status = if (streamSucceeded) MessageStatus.COMPLETED else MessageStatus.ERROR
        )

        if (finalThinking != null) {
            chatRepository.updateMessageThinking(
                messageId = assistantMessage.id,
                thinking = finalThinking,
                durationMs = totalDuration
            )
        }

        val completedMessage = assistantMessage.copy(
            content = finalContent,
            thinkingProcess = finalThinking,
            thinkingDurationMs = totalDuration,
            status = if (streamSucceeded) MessageStatus.COMPLETED else MessageStatus.ERROR
        )
        emit(SendMessageEvent.Completed(completedMessage, totalDuration))
    }
}
