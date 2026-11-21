package app.lorit.ai.domain.model

/**
 * Поддерживаемые провайдеры нейросетей.
 */
enum class ProviderType(val displayName: String, val supportsReasoning: Boolean) {
    OPENAI("OpenAI", true),
    CLAUDE("Anthropic Claude", true),
    GEMINI("Google Gemini", false),
    DEEPSEEK("DeepSeek", true);

    companion object {
        fun fromString(value: String): ProviderType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OPENAI
    }
}
