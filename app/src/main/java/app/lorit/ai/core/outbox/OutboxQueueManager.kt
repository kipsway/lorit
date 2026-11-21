package app.lorit.ai.core.outbox

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.lorit.ai.core.network.NetworkMonitor
import app.lorit.ai.domain.model.Attachment
import app.lorit.ai.domain.model.Message
import app.lorit.ai.domain.model.MessageStatus
import app.lorit.ai.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Менеджер очереди исходящих сообщений (Outbox Pattern).
 * Гарантирует доставку сообщений при нестабильной связи.
 */
class OutboxQueueManager(
    private val context: Context,
    private val chatRepository: ChatRepository,
    private val networkMonitor: NetworkMonitor,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private var messageSenderCallback: (suspend (Message) -> Unit)? = null

    init {
        // Подписка на восстановление сети
        scope.launch {
            networkMonitor.isOnline.collectLatest { isOnline ->
                if (isOnline) {
                    processQueue()
                }
            }
        }
    }

    fun registerMessageSender(sender: suspend (Message) -> Unit) {
        this.messageSenderCallback = sender
    }

    /**
     * Постановка сообщения в оффлайн-очередь с сохранением в зашифрованной БД.
     */
    suspend fun enqueueMessage(
        chatId: String,
        content: String,
        attachments: List<Attachment> = emptyList()
    ): Message {
        val message = chatRepository.insertUserMessage(
            chatId = chatId,
            content = content,
            attachments = attachments,
            status = MessageStatus.OUTBOX_QUEUED
        )

        if (networkMonitor.isOnline.value) {
            processQueue()
        } else {
            scheduleBackgroundWork()
        }

        return message
    }

    /**
     * Обработка всех сообщений со статусом OUTBOX_QUEUED.
     */
    suspend fun processQueue() {
        val pendingMessages = chatRepository.getOutboxMessages()
        if (pendingMessages.isEmpty()) return

        val sender = messageSenderCallback
        for (msg in pendingMessages) {
            try {
                if (sender != null) {
                    sender(msg)
                    chatRepository.updateMessageStream(msg.id, msg.content, MessageStatus.COMPLETED)
                }
            } catch (e: Exception) {
                // Если отправка сорвалась, оставляем в очереди и планируем WorkManager
                scheduleBackgroundWork()
                break
            }
        }
    }

    private fun scheduleBackgroundWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    companion object {
        const val WORK_NAME = "lorit_outbox_sync_work"
    }
}
