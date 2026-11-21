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

class GeminiProvider(
    private val secureVault: SecureVault,
    private val client: OkHttpClient = ProxiedOkHttpClient.create(useProxy = true)
) : BaseAiProvider {

    override val providerType: ProviderType = ProviderType.GEMINI
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun streamChat(
        messages: List<Message>,
        modelId: String,
        attachments: List<Attachment>,
        systemPrompt: String?
    ): Flow<StreamChunk> = flow {
        val apiKey = secureVault.getApiKey(ProviderType.GEMINI)
        if (apiKey.isNullOrBlank()) {
            emit(StreamChunk.Error("Не найден Google AI Studio API-ключ. Укажите его в настройках."))
            return@flow
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:streamGenerateContent?alt=sse&key=$apiKey"

        val contentsArray = mutableListOf<String>()
        for (i in messages.indices) {
            val msg = messages[i]
            val role = if (msg.sender == MessageSender.USER) "user" else "model"
            val text = if (i == messages.lastIndex && role == "user") {
                PromptBuilder.buildMessageWithAttachments(msg.content, attachments)
            } else {
                msg.content
            }
            contentsArray.add("{\"role\":\"$role\",\"parts\":[{\"text\":\"${escapeJson(text)}\"}]}")
        }

        val sysInstruction = systemPrompt ?: PromptBuilder.DEFAULT_SYSTEM_INSTRUCTIONS
        val payload = buildString {
            append("{\"system_instruction\":{\"parts\":[{\"text\":\"").append(escapeJson(sysInstruction)).append("\"}]},")
            append("\"contents\":[")
            append(contentsArray.joinToString(","))
            append("]}")
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val startTime = System.currentTimeMillis()
        val fullContent = StringBuilder()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorCode = response.code
                val errorBody = response.body?.string() ?: ""
                val isQuota = errorCode == 429 || errorBody.contains("RESOURCE_EXHAUSTED")
                emit(StreamChunk.Error("Ошибка Gemini ($errorCode): $errorBody", isQuotaExceeded = isQuota))
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
                    val candidates = root["candidates"]?.jsonArray
                    if (candidates != null && candidates.isNotEmpty()) {
                        val contentObj = candidates[0].jsonObject["content"]?.jsonObject
                        val parts = contentObj?.get("parts")?.jsonArray
                        if (parts != null && parts.isNotEmpty()) {
                            val text = parts[0].jsonObject["text"]?.jsonPrimitive?.content ?: ""
                            fullContent.append(text)
                            emit(StreamChunk.Content(text))
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val duration = System.currentTimeMillis() - startTime
            emit(StreamChunk.Completed(fullContent.toString(), null, duration))
        } catch (e: Exception) {
            emit(StreamChunk.Error("Сетевой сбой Gemini: ${e.localizedMessage}", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun fetchAvailableModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val apiKey = secureVault.getApiKey(ProviderType.GEMINI)
        if (!apiKey.isNullOrBlank()) {
            try {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                    .get()
                    .build()

                val resp = client.newCall(request).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val root = json.parseToJsonElement(body).jsonObject
                    val modelsArray = root["models"]?.jsonArray
                    if (modelsArray != null) {
                        val result = mutableListOf<ModelInfo>()
                        for (item in modelsArray) {
                            val rawName = item.jsonObject["name"]?.jsonPrimitive?.content ?: continue
                            val id = rawName.removePrefix("models/")
                            val displayName = item.jsonObject["displayName"]?.jsonPrimitive?.content ?: id
                            val inputTokenLimit = item.jsonObject["inputTokenLimit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1000000

                            if (id.contains("gemini")) {
                                result.add(
                                    ModelInfo(
                                        id = id,
                                        name = displayName,
                                        provider = ProviderType.GEMINI,
                                        contextTokenLimit = inputTokenLimit,
                                        supportsReasoning = id.contains("thinking")
                                    )
                                )
                            }
                        }
                        if (result.isNotEmpty()) return@withContext result
                    }
                }
            } catch (_: Exception) {
            }
        }

        listOf(
            ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash (Fast & Multimodal)", ProviderType.GEMINI, 1048576),
            ModelInfo("gemini-2.0-flash-thinking-exp", "Gemini 2.0 Flash Thinking", ProviderType.GEMINI, 1048576, supportsReasoning = true),
            ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro (2M Tokens)", ProviderType.GEMINI, 2097152)
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
