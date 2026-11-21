package app.lorit.ai.core.multimodal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class EncodedImage(
    val base64Data: String,
    val mimeType: String
)

object ImageHelper {

    private const val MAX_IMAGE_BYTES = 15 * 1024 * 1024 // 15 МБ

    /**
     * Считывает и кодирует изображение в Base64.
     * При необходимости деликатно оптимизирует размер для отправки в Vision API.
     */
    suspend fun encodeImageToBase64(context: Context, uri: Uri): EncodedImage? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val rawMime = contentResolver.getType(uri) ?: "image/jpeg"
            val mimeType = if (rawMime.startsWith("image/")) rawMime else "image/jpeg"

            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bytes = inputStream?.use { it.readBytes() } ?: return@withContext null

            if (bytes.size <= MAX_IMAGE_BYTES) {
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                return@withContext EncodedImage(base64, mimeType)
            }

            // Если размер превышает лимит провайдеров, применяем деликатное сжатие
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val compressedBytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            EncodedImage(base64, "image/jpeg")
        } catch (_: Exception) {
            null
        }
    }
}
