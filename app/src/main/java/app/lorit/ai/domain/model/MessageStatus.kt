package app.lorit.ai.domain.model

/**
 * Статусы жизненного цикла сообщения.
 */
enum class MessageStatus {
    PENDING,
    STREAMING,
    COMPLETED,
    ERROR,
    OUTBOX_QUEUED;

    companion object {
        fun fromString(value: String): MessageStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: COMPLETED
    }
}
