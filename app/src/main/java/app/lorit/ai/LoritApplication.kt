package app.lorit.ai

import android.app.Application
import app.lorit.ai.core.database.DatabaseManager
import app.lorit.ai.core.network.NetworkMonitor
import app.lorit.ai.core.network.worker.SubscriptionUpdateWorker
import app.lorit.ai.core.outbox.OutboxQueueManager
import app.lorit.ai.core.security.EncryptedPreferencesVault
import app.lorit.ai.core.security.SecureVault
import app.lorit.ai.core.vpn.VpnCoreManager
import app.lorit.ai.data.repository.ChatRepositoryImpl
import app.lorit.ai.domain.repository.ChatRepository

/**
 * Базовый класс приложения Lorit.
 * Инициализирует сетевое ядро, защищенное хранилище, базу данных и фоновые сервисы.
 */
class LoritApplication : Application() {

    lateinit var vpnCoreManager: VpnCoreManager
        private set

    lateinit var secureVault: SecureVault
        private set

    lateinit var databaseManager: DatabaseManager
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var networkMonitor: NetworkMonitor
        private set

    lateinit var outboxQueueManager: OutboxQueueManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Инициализация синглтона сетевого ядра sing-box
        vpnCoreManager = VpnCoreManager.getInstance(this)

        // Инициализация аппаратного хранилища ключей Android KeyStore + EncryptedSharedPreferences
        secureVault = EncryptedPreferencesVault.getInstance(this)

        // Инициализация зашифрованной базы данных Room SQLCipher
        databaseManager = DatabaseManager.getInstance(this)

        // Репозиторий диалогов
        chatRepository = ChatRepositoryImpl(databaseManager)

        // Сетевой монитор и Outbox-очередь
        networkMonitor = NetworkMonitor(this)
        outboxQueueManager = OutboxQueueManager(
            context = this,
            chatRepository = chatRepository,
            networkMonitor = networkMonitor
        )

        // Запуск 24-часового планировщика обновления подписки
        SubscriptionUpdateWorker.schedule(
            context = this,
            url = secureVault.getSubscriptionUrl()
        )
    }
}
