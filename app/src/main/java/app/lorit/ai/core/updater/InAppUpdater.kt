package app.lorit.ai.core.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import app.lorit.ai.core.network.ProxiedOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val changelog: String,
    val downloadUrl: String
)

/**
 * Автономный In-App обновлятор по GitHub Releases API.
 * Позволяет обновлять приложение в 1 клик без маркетов.
 */
class InAppUpdater(
    private val context: Context,
    private val client: OkHttpClient = ProxiedOkHttpClient.create(useProxy = true)
) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val REPO_OWNER = "kipsway"
        const val REPO_NAME = "lorit"
    }

    /**
     * Проверяет наличие обновлений на GitHub Releases.
     */
    suspend fun checkForUpdates(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Lorit-AI-Hub-Android")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val root = json.parseToJsonElement(body).jsonObject
                val tagName = root["tag_name"]?.jsonPrimitive?.content ?: ""
                val changelog = root["body"]?.jsonPrimitive?.content ?: ""
                val assets = root["assets"]?.jsonArray

                var apkDownloadUrl = ""
                if (assets != null) {
                    for (asset in assets) {
                        val name = asset.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: ""
                            break
                        }
                    }
                }

                val hasNewVersion = isNewerVersion(currentVersion, tagName)
                return@withContext UpdateInfo(
                    hasUpdate = hasNewVersion && apkDownloadUrl.isNotEmpty(),
                    latestVersion = tagName,
                    changelog = changelog,
                    downloadUrl = apkDownloadUrl
                )
            }
        } catch (_: Exception) {
        }

        UpdateInfo(hasUpdate = false, latestVersion = currentVersion, changelog = "", downloadUrl = "")
    }

    /**
     * Скачивает APK через системный DownloadManager и запускает нативный интент установки.
     */
    fun downloadAndInstall(apkUrl: String, fileName: String = "Lorit-AI-Update.apk") {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(apkUrl)

        val request = DownloadManager.Request(uri)
            .setTitle("Обновление Lorit AI")
            .setDescription("Загрузка новой версии приложения...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val downloadedUri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (downloadedUri != null) {
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(downloadedUri, "application/vnd.android.package-archive")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        context.startActivity(installIntent)
                    }
                    context.unregisterReceiver(this)
                }
            }
        }

        context.registerReceiver(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.removePrefix("v").trim()
        val cleanLatest = latest.removePrefix("v").trim()
        return cleanLatest.isNotEmpty() && cleanLatest != cleanCurrent
    }
}
