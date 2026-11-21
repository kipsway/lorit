package app.lorit.ai.core.multimodal

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Локальный экстрактор текста из документов (PDF, DOCX, TXT, CSV, Код).
 * Работает 100% на устройстве без отправки файлов на сторонние сервисы.
 */
object DocumentTextExtractor {

    /**
     * Извлекает текстовое содержимое файла по его Uri.
     */
    fun extractText(context: Context, uri: Uri, mimeType: String?, fileName: String): String {
        return try {
            when {
                mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true) -> {
                    extractFromPdf(context, uri)
                }
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                        fileName.endsWith(".docx", ignoreCase = true) -> {
                    extractFromDocx(context, uri)
                }
                else -> {
                    // TXT, CSV, JSON, MD, Kotlin, Java, Python и любые текстовые файлы
                    extractFromPlainText(context, uri)
                }
            }
        } catch (e: Exception) {
            "Ошибка локального чтения содержимого документа: ${e.localizedMessage}"
        }
    }

    /**
     * Извлекает текст из DOCX (распаковка word/document.xml через встроенный ZipInputStream и XmlPullParser).
     */
    private fun extractFromDocx(context: Context, uri: Uri): String {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val zipStream = ZipInputStream(inputStream)
        val extractedText = StringBuilder()

        try {
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val factory = XmlPullParserFactory.newInstance()
                    factory.isNamespaceAware = true
                    val parser = factory.newPullParser()
                    parser.setInput(zipStream, "UTF-8")

                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "t") {
                            parser.next()
                            if (parser.text != null) {
                                extractedText.append(parser.text).append(" ")
                            }
                        } else if (eventType == XmlPullParser.END_TAG && parser.name == "p") {
                            extractedText.append("\n")
                        }
                        eventType = parser.next()
                    }
                    break
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        } finally {
            zipStream.close()
        }

        return extractedText.toString().trim()
    }

    /**
     * Чтение текстового файла / кода / CSV.
     */
    private fun extractFromPlainText(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        return BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    /**
     * Базовый извлекатель метаданных PDF страниц через ParcelFileDescriptor.
     */
    private fun extractFromPdf(context: Context, uri: Uri): String {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return ""
        return try {
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            renderer.close()
            // Возвращаем метаданные о документе
            "Документ PDF: $pageCount стр. Примечание: Для полного OCR используйте режим с прикреплением изображения страниц."
        } finally {
            pfd.close()
        }
    }
}
