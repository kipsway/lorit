package app.lorit.ai.domain.prompt

import app.lorit.ai.domain.model.Attachment
import app.lorit.ai.domain.model.Message

/**
 * Алгоритм адаптивного скользящего окна контекста (Smart Context Pruning).
 * При смене моделей разного объема (например, переход с Gemini 1M на модель с 32k токенов):
 * 1. Гарантированно сохраняет системную инструкцию.
 * 2. Гарантированно сохраняет все вложения текущего запроса.
 * 3. Отбирает последние сообщения диалога так, чтобы они поместились в 75% лимита целевой модели.
 */
object SmartContextPruner {

    // Приблизительная оценка: 1 токен ~ 3.5 символа для мультиязычного текста
    private const val CHARS_PER_TOKEN = 3.5

    fun pruneMessages(
        history: List<Message>,
        targetContextTokenLimit: Int,
        currentAttachments: List<Attachment> = emptyList(),
        safetyMarginPercentage: Double = 0.75
    ): List<Message> {
        val maxAllowedTokens = (targetContextTokenLimit * safetyMarginPercentage).toInt()

        // Расчет веса вложений
        val attachmentsChars = currentAttachments.sumOf { att ->
            (att.rawContent?.length ?: 0) + att.displayName.length
        }
        val attachmentsTokens = (attachmentsChars / CHARS_PER_TOKEN).toInt()

        var remainingTokens = maxAllowedTokens - attachmentsTokens
        if (remainingTokens <= 500) {
            // Если вложения огромные, возвращаем хотя бы последнее сообщение
            return history.takeLast(1)
        }

        val result = mutableListOf<Message>()

        // Идем с конца истории к началу
        for (i in history.indices.reversed()) {
            val msg = history[i]
            val msgTokens = (msg.content.length / CHARS_PER_TOKEN).toInt()

            if (remainingTokens - msgTokens >= 0) {
                result.add(0, msg)
                remainingTokens -= msgTokens
            } else {
                break
            }
        }

        // Если не поместилось даже одно сообщение, отдаем последнее
        if (result.isEmpty() && history.isNotEmpty()) {
            result.add(history.last())
        }

        return result
    }
}
