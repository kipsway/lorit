package app.lorit.ai.data.providers

import app.lorit.ai.core.common.PromptBuilder
import app.lorit.ai.core.network.ProxiedOkHttpClient
import app.lorit.ai.core.security.SecureVault
import app.lorit.ai.domain.model.Attachment
import app.lorit.ai.domain.model.Message
import app.lorit.ai.domain.model.MessageSender
import app.lorit.ai.domain.model.ModelInfo
import app.lorit.ai.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader

class ClaudeProvider(
    private val secureVault: SecureVault,
    private val client: OkHttpClient = ProxiedOkHttpClient.create(useProxy = true)
) : BaseAiProvider {

    override val providerType: ProviderType = ProviderType.CLAUDE
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun streamChat(
        messages: List<Message>,
        modelId: String,
        attachments: List<Attachment>,
        systemPrompt: String?
    ): Flow<StreamChunk> = flow {
        val apiKey = secureVault.getApiKey(ProviderType.CLAUDE)
        val sessionKey = secureVault.getClaudeSessionKey()

        if (apiKey.isNullOrBlank() && sessionKey.isNullOrBlank()) {
            emit(StreamChunk.Error("Не найден ключ Anthropic или сессия Claude. Авторизуйтесь в настройках."))
            return@flow
        }

        val requestMessages = mutableListOf<Map<String, String>>()
        for (i in messages.indices) {
            val msg = messages[i]
            if (msg.sender == MessageSender.SYSTEM) continue
            val role = if (msg.sender == MessageSender.USER) "user" else "assistant"
            val content = if (i == messages.lastIndex && role == "user") {
                PromptBuilder.buildMessageWithAttachments(msg.content, attachments)
            } else {
                msg.content
            }
            requestMessages.add(mapOf("role" to role, "content" to content))
        }

        val sysInstruction = systemPrompt ?: PromptBuilder.DEFAULT_SYSTEM_INSTRUCTIONS
        val payload = buildString {
            append("{\"model\":\"").append(modelId).append("\",")
            append("\"max_tokens\":8192,")
            append("\"stream\":true,")
            append("\"system\":\"").append(escapeJson(sysInstruction)).append("\",")
            append("\"messages\":[")
            append(requestMessages.joinToString(",") { m ->
                val escapedContent = escapeJson(m["content"] ?: "")
                "{\"role\":\"${m["role"]}\",\"content\":\"$escapedContent\"}"
            })
            append("]}")
        }

        val reqBuilder = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("anthropic-version", "2023-06-01")

        if (!apiKey.isNullOrBlank()) {
            reqBuilder.header("x-api-key", apiKey)
        } else {
            reqBuilder.header("Cookie", "sessionKey=$sessionKey")
        }

        val startTime = System.currentTimeMillis()
        val fullContent = StringBuilder()
        val thinkingContent = StringBuilder()

        try {
            val response = client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                val errorCode = response.code
                val errorBody = response.body?.string() ?: ""
                val isQuota = errorCode == 429 || errorBody.contains("rate_limit") || errorBody.contains("overloaded")
                emit(StreamChunk.Error("Ошибка Claude ($errorCode): $errorBody", isQuotaExceeded = isQuota))
                return@flow
            }

            val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: return@flow))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val curLine = line ?: continue
                if (!curLine.startsWith("data: ")) continue
                val data = curLine.removePrefix("data: ").trim()

                try {
                    val root = json.parseToJsonElement(data).jsonObject
                    val type = root["type"]?.jsonPrimitive?.content

                    if (type == "content_block_delta") {
                        val delta = root["delta"]?.jsonObject
                        val deltaType = delta?.get("type")?.jsonPrimitive?.content
                        if (deltaType == "thinking_delta") {
                            val thinking = delta["thinking"]?.jsonPrimitive?.content ?: ""
                            thinkingContent.append(thinking)
                            emit(StreamChunk.Thinking(thinking))
                        } else if (deltaType == "text_delta") {
                            val text = delta["text"]?.jsonPrimitive?.content ?: ""
                            fullContent.append(text)
                            emit(StreamChunk.Content(text))
                        }
                    } else if (type == "message_stop") {
                        break
                    }
                } catch (_: Exception) {
                }
            }

            val duration = System.currentTimeMillis() - startTime
            val thinkingResult = thinkingContent.toString().ifEmpty { null }
            emit(StreamChunk.Completed(fullContent.toString(), thinkingResult, duration))
        } catch (e: Exception) {
            emit(StreamChunk.Error("Сетевой сбой Claude: ${e.localizedMessage}", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun fetchAvailableModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val orgId = secureVault.getClaudeOrgId()
        val sessionKey = secureVault.getClaudeSessionKey()

        if (!orgId.isNullOrBlank() && !sessionKey.isNullOrBlank()) {
            try {
                val request = Request.Builder()
                    .url("https://claude.ai/api/organizations/$orgId/models")
                    .header("Cookie", "sessionKey=$sessionKey")
                    .get()
                    .build()

                val resp = client.newCall(request).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val root = json.parseToJsonElement(body).jsonArray
                    val models = mutableListOf<ModelInfo>()
                    for (elem in root) {
                        val id = elem.jsonObject["id"]?.jsonPrimitive?.content ?: continue
                        val name = elem.jsonObject["display_name"]?.jsonPrimitive?.content ?: id
                        models.add(
                            ModelInfo(
                                id = id,
                                name = name,
                                provider = ProviderType.CLAUDE,
                                contextTokenLimit = 200000,
                                supportsReasoning = id.contains("3-7") || id.contains("thinking"),
                                isOfficialWebSession = true
                            )
                        )
                    }
                    if (models.isNotEmpty()) return@withContext models
                }
            } catch (_: Exception) {
            }
        }

        listOf(
            ModelInfo("claude-3-7-sonnet-20250219", "Claude 3.7 Sonnet (Hybrid/Thinking)", ProviderType.CLAUDE, 200000, supportsReasoning = true),
            ModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", ProviderType.CLAUDE, 200000, supportsReasoning = false),
            ModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", ProviderType.CLAUDE, 200000, supportsReasoning = false)
        )
    }

    override suspend fun getQuotaStatus(modelId: String): QuotaInfo? = null

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
