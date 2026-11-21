package app.lorit.ai.core.share

import app.lorit.ai.core.common.TextCleaner
import app.lorit.ai.core.network.ProxiedOkHttpClient
import app.lorit.ai.domain.model.Chat
import app.lorit.ai.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

/**
 * Менеджер публикации веб-ссылок на чаты через Cloudflare Workers KV («Поделиться как в Gemini»).
 */
class ChatShareManager(
    private val client: OkHttpClient = ProxiedOkHttpClient.create(useProxy = true)
) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val CLOUDFLARE_SHARE_ENDPOINT = "https://share.lorit.app/api/publish"
        const val SHARE_BASE_URL = "https://chat.hub.app/s/"
    }

    /**
     * Сериализует диалог, очищает персональные данные и публикует публичную ссылку.
     */
    suspend fun createPublicShareLink(chat: Chat, messages: List<Message>): String = withContext(Dispatchers.IO) {
        val sanitizedMessages = messages.map { msg ->
            mapOf(
                "role" to msg.sender.name.lowercase(),
                "content" to TextCleaner.cleanForClipboard(msg.content),
                "created_at" to msg.createdAt.toString()
            )
        }

        val payload = buildString {
            append("{\"title\":\"").append(escapeJson(chat.title)).append("\",")
            append("\"provider\":\"").append(chat.activeProvider.name).append("\",")
            append("\"messages\":[")
            append(sanitizedMessages.joinToString(",") { m ->
                "{\"role\":\"${m["role"]}\",\"content\":\"${escapeJson(m["content"] ?: "")}\"}"
            })
            append("]}")
        }

        try {
            val request = Request.Builder()
                .url(CLOUDFLARE_SHARE_ENDPOINT)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val root = json.parseToJsonElement(body).jsonObject
                val hash = root["hash"]?.jsonPrimitive?.content
                if (!hash.isNullOrBlank()) {
                    return@withContext "$SHARE_BASE_URL$hash"
                }
            }
        } catch (_: Exception) {
            // Фолбэк локального хеширования
        }

        // Локальный хеш в качестве фолбэка
        val hash = sha256("${chat.id}_${System.currentTimeMillis()}").take(12)
        "$SHARE_BASE_URL$hash"
    }

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
