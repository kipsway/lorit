package app.lorit.ai.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.lorit.ai.LoritApplication
import app.lorit.ai.core.common.HapticFeedbackHelper
import app.lorit.ai.core.theme.LoritTheme
import app.lorit.ai.domain.usecase.AiProviderFactory
import app.lorit.ai.domain.usecase.SendMessageUseCase
import app.lorit.ai.presentation.chat.ChatScreen
import app.lorit.ai.presentation.chat.ChatViewModel
import app.lorit.ai.presentation.settings.SettingsScreen

enum class AppDestination {
    CHAT,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var chatViewModel: ChatViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as LoritApplication
        val haptics = HapticFeedbackHelper(this)
        val providerFactory = AiProviderFactory(this, app.secureVault)
        val sendMessageUseCase = SendMessageUseCase(
            chatRepository = app.chatRepository,
            networkMonitor = app.networkMonitor,
            outboxQueueManager = app.outboxQueueManager,
            providerFactory = providerFactory
        )

        chatViewModel = ChatViewModel(
            chatRepository = app.chatRepository,
            secureVault = app.secureVault,
            sendMessageUseCase = sendMessageUseCase,
            providerFactory = providerFactory,
            hapticHelper = haptics
        )

        setContent {
            val uiState by chatViewModel.uiState.collectAsState()
            var currentDestination by remember { mutableStateOf(AppDestination.CHAT) }

            LoritTheme(isAmoled = uiState.isAmoledTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentDestination) {
                        AppDestination.CHAT -> {
                            ChatScreen(
                                viewModel = chatViewModel,
                                onNavigateToSettings = { currentDestination = AppDestination.SETTINGS }
                            )
                        }
                        AppDestination.SETTINGS -> {
                            SettingsScreen(
                                secureVault = app.secureVault,
                                onBack = { currentDestination = AppDestination.CHAT }
                            )
                        }
                    }
                }
            }
        }
    }
}
