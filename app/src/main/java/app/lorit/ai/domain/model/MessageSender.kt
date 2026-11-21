package app.lorit.ai.domain.model

enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM;

    companion object {
        fun fromString(value: String): MessageSender =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: USER
    }
}
