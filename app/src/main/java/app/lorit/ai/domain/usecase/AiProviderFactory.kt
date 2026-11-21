package app.lorit.ai.domain.usecase

import android.content.Context
import app.lorit.ai.core.security.SecureVault
import app.lorit.ai.data.providers.BaseAiProvider
import app.lorit.ai.data.providers.ClaudeProvider
import app.lorit.ai.data.providers.DeepSeekProvider
import app.lorit.ai.data.providers.GeminiProvider
import app.lorit.ai.data.providers.OpenAiProvider
import app.lorit.ai.domain.model.ProviderType

/**
 * Фабрика инстансов BaseAiProvider.
 */
class AiProviderFactory(
    private val context: Context,
    private val secureVault: SecureVault
) {
    private val openAiProvider by lazy { OpenAiProvider(secureVault) }
    private val claudeProvider by lazy { ClaudeProvider(secureVault) }
    private val geminiProvider by lazy { GeminiProvider(secureVault) }
    private val deepSeekProvider by lazy { DeepSeekProvider(secureVault) }

    fun getProvider(type: ProviderType): BaseAiProvider = when (type) {
        ProviderType.OPENAI -> openAiProvider
        ProviderType.CLAUDE -> claudeProvider
        ProviderType.GEMINI -> geminiProvider
        ProviderType.DEEPSEEK -> deepSeekProvider
    }

    /**
     * Возвращает резервную легковесную модель для авто-даунгрейда при исчерпании лимитов.
     */
    fun getFallbackModel(provider: ProviderType, currentModelId: String): String = when (provider) {
        ProviderType.OPENAI -> if (currentModelId != "gpt-4o-mini") "gpt-4o-mini" else "gpt-3.5-turbo"
        ProviderType.CLAUDE -> if (currentModelId != "claude-3-5-haiku-20241022") "claude-3-5-haiku-20241022" else currentModelId
        ProviderType.GEMINI -> if (currentModelId != "gemini-2.0-flash") "gemini-2.0-flash" else currentModelId
        ProviderType.DEEPSEEK -> if (currentModelId != "deepseek-chat") "deepseek-chat" else currentModelId
    }
}
