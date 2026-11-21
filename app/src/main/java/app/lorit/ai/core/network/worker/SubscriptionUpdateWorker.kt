package app.lorit.ai.core.network.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lorit.ai.core.vpn.SubscriptionParser
import app.lorit.ai.core.vpn.VpnCoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Фоновый системный воркер для обновления серверов подписки раз в 24 часа.
 */
class SubscriptionUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val parser = SubscriptionParser()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val subscriptionUrl = inputData.getString(KEY_SUB_URL) 
            ?: "https://s.wistfalls.cc/fPLkfFpVXdgS3Fkp"

        try {
            val request = Request.Builder()
                .url(subscriptionUrl)
                .header("User-Agent", "v2rayNG/1.8.19")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotEmpty()) {
                    val nodes = parser.parse(body)
                    if (nodes.isNotEmpty()) {
                        val vpnManager = VpnCoreManager.getInstance(applicationContext)
                        vpnManager.router.updateNodes(nodes)
                        return@withContext Result.success()
                    }
                }
            }
            Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "SubscriptionUpdateWorker"
        const val KEY_SUB_URL = "KEY_SUB_URL"

        fun schedule(context: Context, url: String) {
            val workRequest = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
