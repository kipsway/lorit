package app.lorit.ai.core.common

/**
 * Алгоритм очистки текста при копировании (WYSIWYG Clean Copy).
 * Удаляет технические теги <think>, служебные метаданные и артефакты форматирования.
 */
object TextCleaner {

    private val THINK_REGEX = Regex("(?s)<think>.*?</think>")
    private val TAG_REGEX = Regex("(?s)<.*?>")
    private val HEADER_REGEX = Regex("(?m)^#{1,6}\\s*")
    private val BOLD_REGEX = Regex("\\*\\*(.*?)\\*\\*")
    private val ITALIC_REGEX = Regex("\\*(.*?)\\*")
    private val STRIKE_REGEX = Regex("~~(.*?)~~")
    private val CODE_BLOCK_REGEX = Regex("```[a-zA-Z]*\\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
    private val INLINE_CODE_REGEX = Regex("`([^`]+)`")

    /**
     * Извлекает процесс размышления из сырого ответа модели.
     */
    fun extractThinking(rawText: String): Pair<String?, String> {
        val match = THINK_REGEX.find(rawText) ?: return Pair(null, rawText)
        val thinkingBlock = match.value
            .removePrefix("<think>")
            .removeSuffix("</think>")
            .trim()
        val cleanedText = THINK_REGEX.replace(rawText, "").trim()
        return Pair(thinkingBlock.ifEmpty { null }, cleanedText)
    }

    /**
     * Форматирует отображение спойлера размышлений (например: "Размышления • 8 сек").
     */
    fun formatThinkingHeader(durationMs: Long): String {
        val seconds = (durationMs / 1000).coerceAtLeast(1)
        return "Размышления • $seconds сек"
    }

    /**
     * Очищает текст для стандартного буфера обмена (WYSIWYG: сохраняет Markdown, удаляет <think>).
     */
    fun cleanForClipboard(rawText: String): String {
        val withoutThinking = THINK_REGEX.replace(rawText, "")
        return withoutThinking.trim()
    }

    /**
     * Очищает текст до чистого человеческого Plain Text (без Markdown-разметки).
     */
    fun cleanToPlainText(rawText: String): String {
        var text = cleanForClipboard(rawText)
        text = CODE_BLOCK_REGEX.replace(text, "$1")
        text = INLINE_CODE_REGEX.replace(text, "$1")
        text = BOLD_REGEX.replace(text, "$1")
        text = ITALIC_REGEX.replace(text, "$1")
        text = STRIKE_REGEX.replace(text, "$1")
        text = HEADER_REGEX.replace(text, "")
        text = TAG_REGEX.replace(text, "")
        return text.trim()
    }
}
