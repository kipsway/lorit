package app.lorit.ai.presentation.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lorit.ai.domain.model.Message
import app.lorit.ai.domain.model.MessageSender
import app.lorit.ai.domain.model.MessageStatus

@Composable
fun MessageBubble(
    message: Message,
    onRegenerate: () -> Unit,
    onCompareAnotherModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == MessageSender.USER
    val isStreaming = message.status == MessageStatus.STREAMING
    val isOutbox = message.status == MessageStatus.OUTBOX_QUEUED

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 1.0f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Вложения пользователя (если есть)
            if (isUser && message.attachments.isNotEmpty()) {
                AttachmentChipsRow(
                    attachments = message.attachments,
                    onRemoveAttachment = {},
                    onAttachmentClick = {},
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Спойлер размышлений для ассистента
            if (!isUser && (!message.thinkingProcess.isNullOrEmpty() || isStreaming && message.content.isEmpty())) {
                val thinking = message.thinkingProcess ?: "Анализирую контекст и формирую структурированный ответ..."
                ThinkingSpoiler(
                    thinkingText = thinking,
                    durationMs = message.thinkingDurationMs,
                    initiallyExpanded = isStreaming,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Само тело сообщения
            if (isUser) {
                // Пузырь пользователя
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Индикатор очереди Outbox
                if (isOutbox) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = "В очереди на отправку",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "В очереди",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                // Текст ответа ассистента в стиле Gemini (прямой элегантный текст без тяжелого пузыря)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Мерцающий курсор стриминга
                        if (isStreaming) {
                            val infiniteTransition = rememberInfiniteTransition(label = "cursor")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 500),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "cursor_alpha"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 8.dp, height = 18.dp)
                                    .alpha(alpha)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    // Панель из 4 действий после завершения генерации
                    if (message.status == MessageStatus.COMPLETED && message.content.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        MessageActionRow(
                            rawContent = message.content,
                            onRegenerate = onRegenerate,
                            onCompareAnotherModel = onCompareAnotherModel
                        )
                    }
                }
            }
        }
    }
}
