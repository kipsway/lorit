package app.lorit.ai.core.common

import app.lorit.ai.domain.model.Attachment

/**
 * Конструктор структурированных промптов в строгом соответствии с архитектурным планом.
 */
object PromptBuilder {

    const val DEFAULT_SYSTEM_INSTRUCTIONS = """Ты — персональный ИИ-ассистент в нативном клиенте Android.
Отвечай строго по делу, структурированно, грамотно и без лишних предисловий.
Если пользователю требуется программный код — форматируй его в стандартные Markdown-блоки с указанием языка.
При наличии этапа размышлений оформляй его строго в теги <think>...</think>."""

    /**
     * Формирует итоговый текст сообщения по утвержденному шаблону:
     * 
     * Вложение:
     * {содержимое прикрепленного файла или текст из поля вложения}
     * 
     * Сообщение пользователя:
     * {основной текст из поля ввода}
     * 
     * При работе учитывай и прикрепленные вложения и сообщение пользователя.
     */
    fun buildMessageWithAttachments(
        userMessage: String,
        attachments: List<Attachment>
    ): String {
        val textualAttachments = attachments.mapNotNull { att ->
            att.rawContent?.takeIf { it.isNotBlank() } ?: att.displayName.takeIf { it.isNotBlank() }
        }

        if (textualAttachments.isEmpty()) {
            return userMessage.trim()
        }

        val combinedAttachments = textualAttachments.joinToString("\n\n---\n\n")

        return buildString {
            append("Вложение:\n")
            append(combinedAttachments)
            append("\n\nСообщение пользователя:\n")
            append(userMessage.trim())
            append("\n\nПри работе учитывай и прикрепленные вложения и сообщение пользователя.")
        }
    }
}
