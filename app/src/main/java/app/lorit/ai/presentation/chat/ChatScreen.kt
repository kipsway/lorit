package app.lorit.ai.presentation.chat

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lorit.ai.core.multimodal.DocumentTextExtractor
import app.lorit.ai.domain.model.Attachment
import app.lorit.ai.domain.model.AttachmentType
import app.lorit.ai.domain.model.ModelInfo
import app.lorit.ai.presentation.attachments.AttachmentPreviewBottomSheet
import app.lorit.ai.presentation.attachments.RawTextAttachmentDialog
import app.lorit.ai.presentation.browser.SideSheetBrowser
import app.lorit.ai.presentation.camera.CameraCaptureScreen
import app.lorit.ai.presentation.chat.components.FloatingInputCapsule
import app.lorit.ai.presentation.chat.components.MessageBubble
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showModelMenu by remember { mutableStateOf(false) }
    var isBrowserOpen by remember { mutableStateOf(false) }
    var isCameraOpen by remember { mutableStateOf(false) }
    var showAttachmentOptionsSheet by remember { mutableStateOf(false) }
    var showCompareModelSheet by remember { mutableStateOf(false) }
    var showRawTextDialog by remember { mutableStateOf(false) }
    var previewAttachment by remember { mutableStateOf<Attachment?>(null) }

    // Лаунчер системного выбора файлов документов и фото
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.take(10 - uiState.pendingAttachments.size).forEach { uri ->
            var fileName = "Документ"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
            val mimeType = context.contentResolver.getType(uri)
            val extractedText = DocumentTextExtractor.extractText(context, uri, mimeType, fileName)
            val isImage = mimeType?.startsWith("image/") == true
            val attachment = Attachment(
                id = UUID.randomUUID().toString(),
                messageId = "",
                type = if (isImage) AttachmentType.IMAGE else AttachmentType.FILE_DOCUMENT,
                displayName = fileName,
                rawContent = extractedText,
                localFileUri = uri.toString(),
                fileSizeBytes = fileSize
            )
            viewModel.addAttachment(attachment)
        }
    }

    // Автопрокрутка к последнему сообщению при добавлении сообщений или стриминге
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content?.length) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    if (isCameraOpen) {
        CameraCaptureScreen(
            onPhotoCaptured = { uri ->
                val attachment = Attachment(
                    id = UUID.randomUUID().toString(),
                    messageId = "",
                    type = AttachmentType.IMAGE,
                    displayName = "Снимок с камеры.jpg",
                    localFileUri = uri.toString(),
                    fileSizeBytes = 0L
                )
                viewModel.addAttachment(attachment)
                isCameraOpen = false
            },
            onClose = { isCameraOpen = false }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Lorit AI",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.size(16.dp))

                    // Кнопка создания нового чата
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                viewModel.startNewChat(isTemporary = false)
                                scope.launch { drawerState.close() }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Новый диалог",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Новый диалог",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    // Режим Инкогнито (RAM)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable {
                                viewModel.startNewChat(isTemporary = true)
                                scope.launch { drawerState.close() }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "Инкогнито",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Временный чат (RAM)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.size(24.dp))
                    Text(
                        text = "История диалогов",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.size(8.dp))

                    // Список сохраненных диалогов
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(uiState.chats) { chat ->
                            val isSelected = chat.id == uiState.currentChat?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .clickable {
                                        viewModel.selectChat(chat.id)
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = chat.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Настройки внизу меню
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onNavigateToSettings()
                                scope.launch { drawerState.close() }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Настройки",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Настройки",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        // Выбор активной модели
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showModelMenu = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = uiState.activeModelId,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Выбрать модель",
                                modifier = Modifier.size(18.dp)
                            )

                            // Выпадающий список моделей
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false }
                            ) {
                                uiState.availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = model.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                                )
                                                if (model.supportsReasoning) {
                                                    Text(
                                                        text = "Поддерживает размышления",
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.changeModel(model)
                                            showModelMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = "Меню диалогов"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Настройки"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Основной список сообщений
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onRegenerate = { viewModel.regenerate() },
                            onCompareAnotherModel = { showCompareModelSheet = true }
                        )
                    }
                }

                // Индикатор правой выдвижной колонки (Side-Sheet Peek Preview толщиной 16dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(width = 16.dp, height = 64.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        .clickable { isBrowserOpen = true }
                )

                // Плавающая капсула ввода внизу экрана
                FloatingInputCapsule(
                    inputText = uiState.inputText,
                    onInputTextChanged = { viewModel.onInputTextChanged(it) },
                    onSendMessage = { viewModel.sendMessage() },
                    onStopGeneration = { viewModel.stopGeneration() },
                    isGenerating = uiState.isGenerating,
                    isGlobeActive = uiState.isGlobeActive,
                    onToggleGlobe = { viewModel.toggleGlobe() },
                    onAddAttachmentClick = { showAttachmentOptionsSheet = true },
                    attachments = uiState.pendingAttachments,
                    onRemoveAttachment = { viewModel.removeAttachment(it) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Встроенный Side-Sheet Браузер
                SideSheetBrowser(
                    isOpen = isBrowserOpen,
                    onClose = { isBrowserOpen = false },
                    onExtractPageContent = { pageTitle, pageText ->
                        if (uiState.isGlobeActive && pageText.isNotBlank()) {
                            val attachment = Attachment(
                                id = UUID.randomUUID().toString(),
                                messageId = "",
                                type = AttachmentType.RAW_TEXT,
                                displayName = "Веб: $pageTitle",
                                rawContent = pageText,
                                fileSizeBytes = pageText.toByteArray().size.toLong()
                            )
                            viewModel.addAttachment(attachment)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    // Меню добавления вложений
    if (showAttachmentOptionsSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showAttachmentOptionsSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Добавить вложение",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 1. Камера
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showAttachmentOptionsSheet = false
                            isCameraOpen = true
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Rounded.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Сделать снимок (Камера)", style = MaterialTheme.typography.bodyLarge)
                        Text("Съемка в полном разрешении матрицы CameraX", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Файлы и документы
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showAttachmentOptionsSheet = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Файл или документ", style = MaterialTheme.typography.bodyLarge)
                        Text("PDF, DOCX, TXT, CSV, код, изображения", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Текст
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showAttachmentOptionsSheet = false
                            showRawTextDialog = true
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Rounded.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Вставить текст как вложение", style = MaterialTheme.typography.bodyLarge)
                        Text("Длинные статьи, конспекты, логи", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Меню выбора модели для сравнения ответов
    if (showCompareModelSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showCompareModelSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Сравнить ответ с другой моделью",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                val models = remember { viewModel.getAllSupportedModels() }
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(models) { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showCompareModelSheet = false
                                    viewModel.compareWithModel(model)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "${model.provider.displayName} • ${model.contextTokenLimit / 1000}k токенов",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Диалог ввода сырого текста
    if (showRawTextDialog) {
        RawTextAttachmentDialog(
            onDismiss = { showRawTextDialog = false },
            onConfirm = { attachment ->
                viewModel.addAttachment(attachment)
                showRawTextDialog = false
            }
        )
    }

    // Предпросмотр вложения
    previewAttachment?.let { attachment ->
        AttachmentPreviewBottomSheet(
            attachment = attachment,
            onDismiss = { previewAttachment = null },
            onDelete = {
                viewModel.removeAttachment(attachment)
                previewAttachment = null
            }
        )
    }
}
