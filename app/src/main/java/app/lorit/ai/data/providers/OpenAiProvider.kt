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

class OpenAiProvider(
    private val secureVault: SecureVault,
    private val client: OkHttpClient = ProxiedOkHttpClient.create(useProxy = true)
) : BaseAiProvider {

    override val providerType: ProviderType = ProviderType.OPENAI
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun streamChat(
        messages: List<Message>,
        modelId: String,
        attachments: List<Attachment>,
        systemPrompt: String?
    ): Flow<StreamChunk> = flow {
        val apiKey = secureVault.getApiKey(ProviderType.OPENAI)
        val sessionToken = secureVault.getChatGptAccessToken()

        if (apiKey.isNullOrBlank() && sessionToken.isNullOrBlank()) {
            emit(StreamChunk.Error("Не найден API-ключ OpenAI или веб-сессия. Авторизуйтесь в настройках."))
            return@flow
        }

        val url = if (!apiKey.isNullOrBlank()) {
            "https://api.openai.com/v1/chat/completions"
        } else {
            "https://chatgpt.com/backend-api/conversation"
        }

        val requestMessages = mutableListOf<Map<String, String>>()
        val sysInstruction = systemPrompt ?: PromptBuilder.DEFAULT_SYSTEM_INSTRUCTIONS
        requestMessages.add(mapOf("role" to "system", "content" to sysInstruction))

        for (i in messages.indices) {
            val msg = messages[i]
            val role = when (msg.sender) {
                MessageSender.USER -> "user"
                MessageSender.ASSISTANT -> "assistant"
                MessageSender.SYSTEM -> "system"
            }
            val content = if (i == messages.lastIndex && role == "user") {
                PromptBuilder.buildMessageWithAttachments(msg.content, attachments)
            } else {
                msg.content
            }
            requestMessages.add(mapOf("role" to role, "content" to content))
        }

        val payload = buildString {
            append("{\"model\":\"").append(modelId).append("\",")
            append("\"stream\":true,")
            append("\"messages\":[")
            append(requestMessages.joinToString(",") { m ->
                val escapedContent = escapeJson(m["content"] ?: "")
                "{\"role\":\"${m["role"]}\",\"content\":\"$escapedContent\"}"
            })
            append("]}")
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json".toMediaType()))

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        } else {
            requestBuilder.header("Authorization", "Bearer $sessionToken")
            secureVault.getChatGptSessionToken()?.let {
                requestBuilder.header("Cookie", "__Secure-next-auth.session-token=$it")
            }
        }

        val request = requestBuilder.build()
        val startTime = System.currentTimeMillis()
        val fullContent = StringBuilder()
        val thinkingContent = StringBuilder()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorCode = response.code
                val errorBody = response.body?.string() ?: ""
                val isQuota = errorCode == 429 || errorBody.contains("insufficient_quota") || errorBody.contains("quota")
                emit(StreamChunk.Error("Ошибка OpenAI ($errorCode): $errorBody", isQuotaExceeded = isQuota))
                return@flow
            }

            val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: return@flow))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val curLine = line ?: continue
                if (!curLine.startsWith("data: ")) continue
                val data = curLine.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                try {
                    val root = json.parseToJsonElement(data).jsonObject
                    val choices = root["choices"]?.jsonArray
                    if (choices != null && choices.isNotEmpty()) {
                        val delta = choices[0].jsonObject["delta"]?.jsonObject
                        val deltaReasoning = delta?.get("reasoning_content")?.jsonPrimitive?.content
                        val deltaText = delta?.get("content")?.jsonPrimitive?.content

                        if (!deltaReasoning.isNullOrEmpty()) {
                            thinkingContent.append(deltaReasoning)
                            emit(StreamChunk.Thinking(deltaReasoning))
                        }
                        if (!deltaText.isNullOrEmpty()) {
                            fullContent.append(deltaText)
                            emit(StreamChunk.Content(deltaText))
                        }
                    }
                } catch (_: Exception) {
                    // Игнорируем промежуточные служебные чанки
                }
            }
            val duration = System.currentTimeMillis() - startTime
            val thinkingResult = thinkingContent.toString().ifEmpty { null }
            emit(StreamChunk.Completed(fullContent.toString(), thinkingResult, duration))
        } catch (e: Exception) {
            emit(StreamChunk.Error("Сетевая ошибка генерации: ${e.localizedMessage}", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun fetchAvailableModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val apiKey = secureVault.getApiKey(ProviderType.OPENAI)
        val sessionToken = secureVault.getChatGptAccessToken()

        if (apiKey.isNullOrBlank() && sessionToken.isNullOrBlank()) {
            return@withContext listOf(
                ModelInfo("gpt-4o", "GPT-4o (Default)", ProviderType.OPENAI, 128000, supportsReasoning = false),
                ModelInfo("gpt-4o-mini", "GPT-4o Mini", ProviderType.OPENAI, 128000, supportsReasoning = false),
                ModelInfo("o1", "o1 (Reasoning)", ProviderType.OPENAI, 200000, supportsReasoning = true)
            )
        }

        try {
            val url = if (!apiKey.isNullOrBlank()) {
                "https://api.openai.com/v1/models"
            } else {
                "https://chatgpt.com/backend-api/models"
            }

            val reqBuilder = Request.Builder().url(url).get()
            if (!apiKey.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $apiKey")
            } else {
                reqBuilder.header("Authorization", "Bearer $sessionToken")
            }

            val response = client.newCall(reqBuilder.build()).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray
                if (data != null) {
                    val models = mutableListOf<ModelInfo>()
                    for (item in data) {
                        val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: continue
                        if (id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("chatgpt")) {
                            val isReasoning = id.startsWith("o1") || id.startsWith("o3")
                            models.add(
                                ModelInfo(
                                    id = id,
                                    name = id,
                                    provider = ProviderType.OPENAI,
                                    contextTokenLimit = if (isReasoning) 200000 else 128000,
                                    supportsReasoning = isReasoning
                                )
                            )
                        }
                    }
                    if (models.isNotEmpty()) return@withContext models
                }
            }
        } catch (_: Exception) {
            // Фолбэк при сбое сети
        }

        listOf(
            ModelInfo("gpt-4o", "GPT-4o", ProviderType.OPENAI, 128000),
            ModelInfo("gpt-4o-mini", "GPT-4o Mini", ProviderType.OPENAI, 128000),
            ModelInfo("o3-mini", "o3-mini", ProviderType.OPENAI, 200000, supportsReasoning = true)
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
