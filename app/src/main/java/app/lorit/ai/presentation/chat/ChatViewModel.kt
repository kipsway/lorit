package app.lorit.ai.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lorit.ai.core.common.HapticFeedbackHelper
import app.lorit.ai.core.network.service.AiForegroundService
import app.lorit.ai.core.security.SecureVault
import app.lorit.ai.domain.model.Attachment
import app.lorit.ai.domain.model.Chat
import app.lorit.ai.domain.model.Message
import app.lorit.ai.domain.model.MessageSender
import app.lorit.ai.domain.model.MessageStatus
import app.lorit.ai.domain.model.ModelInfo
import app.lorit.ai.domain.model.ProviderType
import app.lorit.ai.domain.repository.ChatRepository
import app.lorit.ai.domain.usecase.AiProviderFactory
import app.lorit.ai.domain.usecase.SendMessageEvent
import app.lorit.ai.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val currentChat: Chat? = null,
    val chats: List<Chat> = emptyList(),
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val isGlobeActive: Boolean = false,
    val pendingAttachments: List<Attachment> = emptyList(),
    val availableModels: List<ModelInfo> = emptyList(),
    val activeProvider: ProviderType = ProviderType.OPENAI,
    val activeModelId: String = "gpt-4o",
    val isAmoledTheme: Boolean = false,
    val errorMessage: String? = null
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val secureVault: SecureVault,
    private val sendMessageUseCase: SendMessageUseCase,
    private val providerFactory: AiProviderFactory,
    private val hapticHelper: HapticFeedbackHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            activeProvider = secureVault.getActiveProvider(),
            activeModelId = secureVault.getActiveModelId(secureVault.getActiveProvider()),
            isAmoledTheme = secureVault.isAmoledTheme(),
            isGlobeActive = secureVault.isGlobeWebSearchDefault()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var streamingMessageId: String? = null

    init {
        loadChats()
        loadModelsForActiveProvider()
    }

    private fun loadChats() {
        viewModelScope.launch {
            chatRepository.getAllChatsFlow().collectLatest { chats ->
                _uiState.update { it.copy(chats = chats) }
                if (_uiState.value.currentChat == null && chats.isNotEmpty()) {
                    selectChat(chats.first().id)
                }
            }
        }
    }

    fun selectChat(chatId: String) {
        viewModelScope.launch {
            val chat = chatRepository.getChatById(chatId) ?: return@launch
            _uiState.update { it.copy(currentChat = chat) }
            chatRepository.getMessagesFlow(chatId).collectLatest { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun startNewChat(isTemporary: Boolean = false) {
        viewModelScope.launch {
            val provider = _uiState.value.activeProvider
            val modelId = _uiState.value.activeModelId
            val title = if (isTemporary) "Временный чат (Инкогнито)" else "Новый диалог"
            val chat = chatRepository.createChat(
                title = title,
                provider = provider,
                modelId = modelId,
                isTemporary = isTemporary
            )
            _uiState.update {
                it.copy(
                    currentChat = chat,
                    messages = emptyList(),
                    pendingAttachments = emptyList(),
                    inputText = ""
                )
            }
            selectChat(chat.id)
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun toggleGlobe() {
        hapticHelper.performLightClick()
        val newState = !_uiState.value.isGlobeActive
        _uiState.update { it.copy(isGlobeActive = newState) }
        secureVault.setGlobeWebSearchDefault(newState)
    }

    fun addAttachment(attachment: Attachment) {
        if (_uiState.value.pendingAttachments.size >= 10) return
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + attachment) }
    }

    fun removeAttachment(attachment: Attachment) {
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments - attachment) }
    }

    fun changeProvider(provider: ProviderType) {
        secureVault.setActiveProvider(provider)
        val defaultModel = secureVault.getActiveModelId(provider)
        _uiState.update { it.copy(activeProvider = provider, activeModelId = defaultModel) }
        loadModelsForActiveProvider()
    }

    fun changeModel(model: ModelInfo) {
        secureVault.setActiveModelId(_uiState.value.activeProvider, model.id)
        _uiState.update { it.copy(activeModelId = model.id) }
    }

    fun toggleAmoledTheme(enabled: Boolean) {
        secureVault.setAmoledTheme(enabled)
        _uiState.update { it.copy(isAmoledTheme = enabled) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachments = _uiState.value.pendingAttachments
        if (text.isEmpty() && attachments.isEmpty()) return

        hapticHelper.performLightClick()

        viewModelScope.launch {
            var currentChat = _uiState.value.currentChat
            if (currentChat == null) {
                val title = text.take(30).ifEmpty { "Диалог" }
                currentChat = chatRepository.createChat(
                    title = title,
                    provider = _uiState.value.activeProvider,
                    modelId = _uiState.value.activeModelId
                )
                _uiState.update { it.copy(currentChat = currentChat) }
            }

            // Очищаем поле ввода и вложения
            _uiState.update { it.copy(inputText = "", pendingAttachments = emptyList(), isGenerating = true) }

            hapticHelper.performStreamStart()

            activeGenerationJob = launch {
                sendMessageUseCase.execute(
                    chatId = currentChat.id,
                    userText = text,
                    attachments = attachments,
                    providerType = _uiState.value.activeProvider,
                    modelId = _uiState.value.activeModelId
                ).collect { event ->
                    when (event) {
                        is SendMessageEvent.AssistantMessageCreated -> {
                            streamingMessageId = event.messageId
                        }
                        is SendMessageEvent.ThinkingUpdate, is SendMessageEvent.ContentUpdate -> {
                            // Ограничение частоты обновления кадров ~16 мс
                            delay(16)
                        }
                        is SendMessageEvent.Completed -> {
                            hapticHelper.performCompletion()
                            _uiState.update { it.copy(isGenerating = false) }
                        }
                        is SendMessageEvent.Error -> {
                            _uiState.update { it.copy(isGenerating = false, errorMessage = event.error) }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _uiState.update { it.copy(isGenerating = false) }
        hapticHelper.performLightClick()
    }

    fun regenerate() {
        val lastUserMessage = _uiState.value.messages.lastOrNull { it.sender == MessageSender.USER }
        if (lastUserMessage != null) {
            _uiState.update { it.copy(inputText = lastUserMessage.content) }
            sendMessage()
        }
    }

    /**
     * Сравнение ответа с другой моделью (например, Claude 3.7 vs GPT-4o vs DeepSeek R1).
     */
    fun compareWithModel(model: ModelInfo) {
        val lastUserMessage = _uiState.value.messages.lastOrNull { it.sender == MessageSender.USER } ?: return
        val currentChat = _uiState.value.currentChat ?: return

        hapticHelper.performLightClick()
        _uiState.update { it.copy(isGenerating = true) }
        hapticHelper.performStreamStart()

        activeGenerationJob = viewModelScope.launch {
            sendMessageUseCase.execute(
                chatId = currentChat.id,
                userText = "[Сравнение: ${model.name}]\n" + lastUserMessage.content,
                attachments = lastUserMessage.attachments,
                providerType = model.provider,
                modelId = model.id
            ).collect { event ->
                when (event) {
                    is SendMessageEvent.ThinkingUpdate, is SendMessageEvent.ContentUpdate -> {
                        delay(16)
                    }
                    is SendMessageEvent.Completed -> {
                        hapticHelper.performCompletion()
                        _uiState.update { it.copy(isGenerating = false) }
                    }
                    is SendMessageEvent.Error -> {
                        _uiState.update { it.copy(isGenerating = false, errorMessage = event.error) }
                    }
                    else -> {}
                }
            }
        }
    }

    fun getAllSupportedModels(): List<ModelInfo> {
        return listOf(
            ModelInfo("gpt-4o", "OpenAI GPT-4o", ProviderType.OPENAI, 128000),
            ModelInfo("o3-mini", "OpenAI o3-mini (Reasoning)", ProviderType.OPENAI, 200000, supportsReasoning = true),
            ModelInfo("claude-3-7-sonnet-20250219", "Claude 3.7 Sonnet (Thinking)", ProviderType.CLAUDE, 200000, supportsReasoning = true),
            ModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", ProviderType.CLAUDE, 200000),
            ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", ProviderType.GEMINI, 1048576),
            ModelInfo("deepseek-reasoner", "DeepSeek R1 (Reasoning)", ProviderType.DEEPSEEK, 64000, supportsReasoning = true),
            ModelInfo("deepseek-chat", "DeepSeek V3", ProviderType.DEEPSEEK, 64000)
        )
    }

    private fun loadModelsForActiveProvider() {
        viewModelScope.launch {
            val provider = providerFactory.getProvider(_uiState.value.activeProvider)
            val models = provider.fetchAvailableModels()
            _uiState.update { it.copy(availableModels = models) }
        }
    }
}
