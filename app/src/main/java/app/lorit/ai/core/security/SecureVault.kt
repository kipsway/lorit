package app.lorit.ai.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.lorit.ai.domain.model.ProviderType
import java.security.SecureRandom

interface SecureVault {
    fun getDatabasePassphrase(): ByteArray
    fun getApiKey(provider: ProviderType): String?
    fun setApiKey(provider: ProviderType, key: String?)
    
    // Веб-сессии
    fun getClaudeSessionKey(): String?
    fun setClaudeSessionKey(sessionKey: String?)
    fun getClaudeOrgId(): String?
    fun setClaudeOrgId(orgId: String?)

    fun getChatGptAccessToken(): String?
    fun setChatGptAccessToken(token: String?)
    fun getChatGptSessionToken(): String?
    fun setChatGptSessionToken(token: String?)

    fun getGeminiCookies(): String?
    fun setGeminiCookies(cookies: String?)
    fun getGeminiSnlm0e(): String?
    fun setGeminiSnlm0e(token: String?)

    fun getDeepSeekToken(): String?
    fun setDeepSeekToken(token: String?)

    // Настройки
    fun getActiveProvider(): ProviderType
    fun setActiveProvider(provider: ProviderType)
    fun getActiveModelId(provider: ProviderType): String
    fun setActiveModelId(provider: ProviderType, modelId: String)
    fun isAmoledTheme(): Boolean
    fun setAmoledTheme(enabled: Boolean)
    fun isGlobeWebSearchDefault(): Boolean
    fun setGlobeWebSearchDefault(enabled: Boolean)
    fun getSubscriptionUrl(): String
    fun setSubscriptionUrl(url: String)
    fun clearAllAuth()
}

class EncryptedPreferencesVault(private val context: Context) : SecureVault {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getDatabasePassphrase(): ByteArray {
        var keyHex = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (keyHex == null) {
            val random = SecureRandom()
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            keyHex = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_DB_PASSPHRASE, keyHex).apply()
        }
        return hexStringToByteArray(keyHex)
    }

    override fun getApiKey(provider: ProviderType): String? {
        return prefs.getString("${KEY_API_PREFIX}${provider.name}", null)
    }

    override fun setApiKey(provider: ProviderType, key: String?) {
        prefs.edit().putString("${KEY_API_PREFIX}${provider.name}", key).apply()
    }

    override fun getClaudeSessionKey(): String? = prefs.getString(KEY_CLAUDE_SESSION_KEY, null)
    override fun setClaudeSessionKey(sessionKey: String?) {
        prefs.edit().putString(KEY_CLAUDE_SESSION_KEY, sessionKey).apply()
    }

    override fun getClaudeOrgId(): String? = prefs.getString(KEY_CLAUDE_ORG_ID, null)
    override fun setClaudeOrgId(orgId: String?) {
        prefs.edit().putString(KEY_CLAUDE_ORG_ID, orgId).apply()
    }

    override fun getChatGptAccessToken(): String? = prefs.getString(KEY_CHATGPT_ACCESS_TOKEN, null)
    override fun setChatGptAccessToken(token: String?) {
        prefs.edit().putString(KEY_CHATGPT_ACCESS_TOKEN, token).apply()
    }

    override fun getChatGptSessionToken(): String? = prefs.getString(KEY_CHATGPT_SESSION_TOKEN, null)
    override fun setChatGptSessionToken(token: String?) {
        prefs.edit().putString(KEY_CHATGPT_SESSION_TOKEN, token).apply()
    }

    override fun getGeminiCookies(): String? = prefs.getString(KEY_GEMINI_COOKIES, null)
    override fun setGeminiCookies(cookies: String?) {
        prefs.edit().putString(KEY_GEMINI_COOKIES, cookies).apply()
    }

    override fun getGeminiSnlm0e(): String? = prefs.getString(KEY_GEMINI_SNLM0E, null)
    override fun setGeminiSnlm0e(token: String?) {
        prefs.edit().putString(KEY_GEMINI_SNLM0E, token).apply()
    }

    override fun getDeepSeekToken(): String? = prefs.getString(KEY_DEEPSEEK_TOKEN, null)
    override fun setDeepSeekToken(token: String?) {
        prefs.edit().putString(KEY_DEEPSEEK_TOKEN, token).apply()
    }

    override fun getActiveProvider(): ProviderType {
        val name = prefs.getString(KEY_ACTIVE_PROVIDER, ProviderType.OPENAI.name) ?: ProviderType.OPENAI.name
        return ProviderType.fromString(name)
    }

    override fun setActiveProvider(provider: ProviderType) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
    }

    override fun getActiveModelId(provider: ProviderType): String {
        return prefs.getString("${KEY_ACTIVE_MODEL_PREFIX}${provider.name}", defaultModelFor(provider))
            ?: defaultModelFor(provider)
    }

    override fun setActiveModelId(provider: ProviderType, modelId: String) {
        prefs.edit().putString("${KEY_ACTIVE_MODEL_PREFIX}${provider.name}", modelId).apply()
    }

    override fun isAmoledTheme(): Boolean = prefs.getBoolean(KEY_AMOLED_THEME, false)
    override fun setAmoledTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_THEME, enabled).apply()
    }

    override fun isGlobeWebSearchDefault(): Boolean = prefs.getBoolean(KEY_GLOBE_SEARCH, false)
    override fun setGlobeWebSearchDefault(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GLOBE_SEARCH, enabled).apply()
    }

    override fun getSubscriptionUrl(): String =
        prefs.getString(KEY_SUBSCRIPTION_URL, DEFAULT_SUBSCRIPTION_URL) ?: DEFAULT_SUBSCRIPTION_URL

    override fun setSubscriptionUrl(url: String) {
        prefs.edit().putString(KEY_SUBSCRIPTION_URL, url).apply()
    }

    override fun clearAllAuth() {
        prefs.edit()
            .remove(KEY_CLAUDE_SESSION_KEY)
            .remove(KEY_CLAUDE_ORG_ID)
            .remove(KEY_CHATGPT_ACCESS_TOKEN)
            .remove(KEY_CHATGPT_SESSION_TOKEN)
            .remove(KEY_GEMINI_COOKIES)
            .remove(KEY_GEMINI_SNLM0E)
            .remove(KEY_DEEPSEEK_TOKEN)
            .apply()
    }

    private fun defaultModelFor(provider: ProviderType): String = when (provider) {
        ProviderType.OPENAI -> "gpt-4o"
        ProviderType.CLAUDE -> "claude-3-7-sonnet"
        ProviderType.GEMINI -> "gemini-2.0-flash"
        ProviderType.DEEPSEEK -> "deepseek-chat"
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    companion object {
        private const val PREFS_FILE_NAME = "lorit_secure_vault"
        private const val KEY_DB_PASSPHRASE = "db_passphrase_hex"
        private const val KEY_API_PREFIX = "api_key_"
        private const val KEY_CLAUDE_SESSION_KEY = "claude_session_key"
        private const val KEY_CLAUDE_ORG_ID = "claude_org_id"
        private const val KEY_CHATGPT_ACCESS_TOKEN = "chatgpt_access_token"
        private const val KEY_CHATGPT_SESSION_TOKEN = "chatgpt_session_token"
        private const val KEY_GEMINI_COOKIES = "gemini_cookies"
        private const val KEY_GEMINI_SNLM0E = "gemini_snlm0e"
        private const val KEY_DEEPSEEK_TOKEN = "deepseek_token"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_ACTIVE_MODEL_PREFIX = "active_model_"
        private const val KEY_AMOLED_THEME = "amoled_theme"
        private const val KEY_GLOBE_SEARCH = "globe_web_search"
        private const val KEY_SUBSCRIPTION_URL = "subscription_url"
        const val DEFAULT_SUBSCRIPTION_URL = "https://s.wistfalls.cc/fPLkfFpVXdgS3Fkp"

        @Volatile
        private var instance: EncryptedPreferencesVault? = null

        fun getInstance(context: Context): EncryptedPreferencesVault {
            return instance ?: synchronized(this) {
                instance ?: EncryptedPreferencesVault(context.applicationContext).also { instance = it }
            }
        }
    }
}
