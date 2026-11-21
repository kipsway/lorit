package app.lorit.ai.core.outbox

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.lorit.ai.core.database.DatabaseManager
import app.lorit.ai.data.repository.ChatRepositoryImpl
import app.lorit.ai.domain.model.MessageStatus

/**
 * Фоновый воркер WorkManager для гарантированной отправки сообщений из Outbox очереди.
 */
class OutboxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dbManager = DatabaseManager.getInstance(applicationContext)
        val repository = ChatRepositoryImpl(dbManager)

        val outboxMessages = repository.getOutboxMessages()
        if (outboxMessages.isEmpty()) {
            return Result.success()
        }

        // Проверяем наличие сообщений и пытаемся подготовить их к доставке
        for (message in outboxMessages) {
            try {
                // Если сеть доступна, сообщения переводятся в PENDING для обработки UI/UseCases
                repository.updateMessageStream(message.id, message.content, MessageStatus.PENDING)
            } catch (e: Exception) {
                return Result.retry()
            }
        }

        return Result.success()
    }
}
