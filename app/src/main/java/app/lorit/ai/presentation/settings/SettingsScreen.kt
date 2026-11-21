package app.lorit.ai.presentation.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lorit.ai.core.security.SecureVault
import app.lorit.ai.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    secureVault: SecureVault,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var openAiKey by remember { mutableStateOf(secureVault.getApiKey(ProviderType.OPENAI) ?: "") }
    var claudeKey by remember { mutableStateOf(secureVault.getApiKey(ProviderType.CLAUDE) ?: "") }
    var geminiKey by remember { mutableStateOf(secureVault.getApiKey(ProviderType.GEMINI) ?: "") }
    var deepSeekKey by remember { mutableStateOf(secureVault.getApiKey(ProviderType.DEEPSEEK) ?: "") }
    var subUrl by remember { mutableStateOf(secureVault.getSubscriptionUrl()) }
    var isAmoled by remember { mutableStateOf(secureVault.isAmoledTheme()) }

    var isPinging by remember { mutableStateOf(false) }
    var pingResults by remember { mutableStateOf<Map<String, Long>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки Lorit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Раздел оформления
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Оформление и тема", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("True AMOLED Black", style = MaterialTheme.typography.bodyLarge)
                    Text("Глубокий чистый черный (#000000) для OLED экранов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isAmoled,
                    onCheckedChange = {
                        isAmoled = it
                        secureVault.setAmoledTheme(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Раздел ключей API
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ключи API нейросетей", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            Text(
                text = "Ключи надежно шифруются аппаратным модулем Android KeyStore (AES-256).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = openAiKey,
                onValueChange = {
                    openAiKey = it
                    secureVault.setApiKey(ProviderType.OPENAI, it)
                },
                label = { Text("OpenAI API Key (sk-...)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = claudeKey,
                onValueChange = {
                    claudeKey = it
                    secureVault.setApiKey(ProviderType.CLAUDE, it)
                },
                label = { Text("Anthropic Claude API Key (sk-ant-...)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = geminiKey,
                onValueChange = {
                    geminiKey = it
                    secureVault.setApiKey(ProviderType.GEMINI, it)
                },
                label = { Text("Google AI Studio Key (AIzaSy...)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = deepSeekKey,
                onValueChange = {
                    deepSeekKey = it
                    secureVault.setApiKey(ProviderType.DEEPSEEK, it)
                },
                label = { Text("DeepSeek API Key (sk-...)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Раздел сетевого ядра и VPN
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Rounded.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Встроенный In-App VPN (sing-box)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = subUrl,
                onValueChange = {
                    subUrl = it
                    secureVault.setSubscriptionUrl(it)
                },
                label = { Text("URL подписки V2Ray / Xray") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Проверка задержки до серверов ИИ
            OutlinedButton(
                onClick = {
                    isPinging = true
                    scope.launch {
                        val results = withContext(Dispatchers.IO) {
                            mapOf(
                                "OpenAI" to measurePing("api.openai.com", 443),
                                "Anthropic" to measurePing("api.anthropic.com", 443),
                                "Google" to measurePing("generativelanguage.googleapis.com", 443),
                                "DeepSeek" to measurePing("api.deepseek.com", 443)
                            )
                        }
                        pingResults = results
                        isPinging = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPinging
            ) {
                if (isPinging) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Замер задержки...")
                } else {
                    Icon(imageVector = Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Проверить пинг до серверов ИИ")
                }
            }

            pingResults?.let { results ->
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    results.forEach { (name, ms) ->
                        val status = if (ms >= 0) "$ms мс" else "Недоступен"
                        Text(
                            text = "• $name: $status",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ms in 0..1500) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Очистка сессий
            Button(
                onClick = {
                    secureVault.clearAllAuth()
                    Toast.makeText(context, "Все сессии и куки очищены", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Очистить все авторизованные веб-сессии")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить и вернуться")
            }
        }
    }
}

private fun measurePing(host: String, port: Int): Long {
    return try {
        val start = System.currentTimeMillis()
        Socket().use { it.connect(InetSocketAddress(host, port), 2500) }
        System.currentTimeMillis() - start
    } catch (_: Exception) {
        -1L
    }
}
