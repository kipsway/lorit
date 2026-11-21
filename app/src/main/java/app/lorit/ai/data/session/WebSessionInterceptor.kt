package app.lorit.ai.data.session

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import app.lorit.ai.core.network.ProxiedOkHttpClient
import app.lorit.ai.core.security.SecureVault
import app.lorit.ai.domain.model.ProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * Изолированный перехватчик веб-сессий официальных аккаунтов.
 * Использует изолированные каталоги данных профиля (setDataDirectorySuffix),
 * чтобы куки разных аккаунтов никогда не смешивались.
 */
class WebSessionInterceptor(
    private val context: Context,
    private val secureVault: SecureVault,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val okHttpClient = ProxiedOkHttpClient.create(useProxy = true)

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UQ1A.240105.004) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36"
    }

    /**
     * Конфигурирует WebView для изолированной авторизации провайдера.
     */
    fun configureWebView(
        webView: WebView,
        provider: ProviderType,
        onAuthSuccess: (ProviderType) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebView.setDataDirectorySuffix("profile_${provider.name.lowercase()}")
            } catch (_: Exception) {
                // Если суффикс уже был назначен ранее процессом
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = USER_AGENT
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url == null) return

                val cookies = cookieManager.getCookie(url) ?: ""
                when (provider) {
                    ProviderType.CLAUDE -> handleClaudeAuth(url, cookies, onAuthSuccess)
                    ProviderType.OPENAI -> handleChatGptAuth(url, cookies, onAuthSuccess)
                    ProviderType.GEMINI -> handleGeminiAuth(view, url, cookies, onAuthSuccess)
                    ProviderType.DEEPSEEK -> handleDeepSeekAuth(view, onAuthSuccess)
                }
            }
        }

        val startUrl = when (provider) {
            ProviderType.CLAUDE -> "https://claude.ai/login"
            ProviderType.OPENAI -> "https://chatgpt.com/auth/login"
            ProviderType.GEMINI -> "https://gemini.google.com"
            ProviderType.DEEPSEEK -> "https://chat.deepseek.com"
        }
        webView.loadUrl(startUrl)
    }

    private fun handleClaudeAuth(url: String, cookies: String, onAuthSuccess: (ProviderType) -> Unit) {
        val sessionKey = extractCookieValue(cookies, "sessionKey")
        if (!sessionKey.isNullOrBlank() && !url.contains("/login")) {
            secureVault.setClaudeSessionKey(sessionKey)
            // Запрашиваем organization_id
            scope.launch {
                try {
                    val request = Request.Builder()
                        .url("https://claude.ai/api/organizations")
                        .header("Cookie", "sessionKey=$sessionKey")
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val orgs = json.parseToJsonElement(body).jsonArray
                        if (orgs.isNotEmpty()) {
                            val orgId = orgs[0].jsonObject["uuid"]?.jsonPrimitive?.content
                            secureVault.setClaudeOrgId(orgId)
                            onAuthSuccess(ProviderType.CLAUDE)
                        }
                    }
                } catch (_: Exception) {
                    onAuthSuccess(ProviderType.CLAUDE)
                }
            }
        }
    }

    private fun handleChatGptAuth(url: String, cookies: String, onAuthSuccess: (ProviderType) -> Unit) {
        val sessionToken = extractCookieValue(cookies, "__Secure-next-auth.session-token")
        if (!sessionToken.isNullOrBlank()) {
            secureVault.setChatGptSessionToken(sessionToken)
            scope.launch {
                try {
                    val request = Request.Builder()
                        .url("https://chatgpt.com/api/auth/session")
                        .header("Cookie", "__Secure-next-auth.session-token=$sessionToken")
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val root = json.parseToJsonElement(body).jsonObject
                        val token = root["accessToken"]?.jsonPrimitive?.content
                        if (!token.isNullOrBlank()) {
                            secureVault.setChatGptAccessToken(token)
                            onAuthSuccess(ProviderType.OPENAI)
                        }
                    }
                } catch (_: Exception) {
                    onAuthSuccess(ProviderType.OPENAI)
                }
            }
        }
    }

    private fun handleGeminiAuth(view: WebView?, url: String, cookies: String, onAuthSuccess: (ProviderType) -> Unit) {
        val sid = extractCookieValue(cookies, "SID")
        val secure1Psid = extractCookieValue(cookies, "__Secure-1PSID")

        if (!sid.isNullOrBlank() && !secure1Psid.isNullOrBlank() && url.contains("gemini.google.com")) {
            secureVault.setGeminiCookies(cookies)
            // Извлекаем SNlM0e из страницы
            view?.evaluateJavascript(
                "(function() { var match = document.documentElement.innerHTML.match(/\"SNlM0e\":\"(.*?)\"/); return match ? match[1] : ''; })();"
            ) { result ->
                val snlm0e = result?.replace("\"", "")?.trim()
                if (!snlm0e.isNullOrBlank() && snlm0e != "null") {
                    secureVault.setGeminiSnlm0e(snlm0e)
                }
                onAuthSuccess(ProviderType.GEMINI)
            }
        }
    }

    private fun handleDeepSeekAuth(view: WebView?, onAuthSuccess: (ProviderType) -> Unit) {
        view?.evaluateJavascript(
            "(function() { return localStorage.getItem('userToken') || ''; })();"
        ) { result ->
            val token = result?.replace("\"", "")?.trim()
            if (!token.isNullOrBlank() && token != "null") {
                secureVault.setDeepSeekToken(token)
                onAuthSuccess(ProviderType.DEEPSEEK)
            }
        }
    }

    private fun extractCookieValue(cookies: String, name: String): String? {
        val pairs = cookies.split(";")
        for (pair in pairs) {
            val parts = pair.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0] == name) {
                return parts[1]
            }
        }
        return null
    }
}
