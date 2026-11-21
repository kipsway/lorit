package app.lorit.ai.presentation.attachments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lorit.ai.domain.model.Attachment
import app.lorit.ai.domain.model.AttachmentType
import java.util.UUID

@Composable
fun RawTextAttachmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (Attachment) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var textContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Прикрепить текст как вложение",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название (например: Логи сервера, Статья)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    label = { Text("Вставьте текст...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (textContent.isNotBlank()) {
                        val displayTitle = title.ifBlank { "Текстовое вложение" }
                        val attachment = Attachment(
                            id = UUID.randomUUID().toString(),
                            messageId = "",
                            type = AttachmentType.RAW_TEXT,
                            displayName = displayTitle,
                            rawContent = textContent.trim(),
                            fileSizeBytes = textContent.toByteArray().size.toLong()
                        )
                        onConfirm(attachment)
                    }
                },
                enabled = textContent.isNotBlank()
            ) {
                Text("Прикрепить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
