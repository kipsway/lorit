package app.lorit.ai.domain.model

enum class AttachmentType {
    IMAGE,
    FILE_DOCUMENT,
    RAW_TEXT;

    companion object {
        fun fromString(value: String): AttachmentType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FILE_DOCUMENT
    }
}
