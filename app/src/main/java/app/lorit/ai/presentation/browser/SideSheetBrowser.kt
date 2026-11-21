package app.lorit.ai.presentation.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.lorit.ai.domain.model.BrowserTab
import java.util.UUID

/**
 * Встроенный Side-Sheet браузер с поддержкой до 10 вкладок и туннелированием трафика через VPN.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SideSheetBrowser(
    isOpen: Boolean,
    onClose: () -> Unit,
    onExtractPageContent: (String, String) -> Unit, // (title, cleanText)
    modifier: Modifier = Modifier
) {
    val tabs = remember {
        mutableStateListOf(
            BrowserTab(
                id = UUID.randomUUID().toString(),
                chatId = "",
                url = "https://google.com",
                title = "Поиск",
                isActive = true,
                orderIndex = 0
            )
        )
    }
    var activeTabIndex by remember { mutableStateOf(0) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.85f),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Панель вкладок (до 10 вкладок)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = index == activeTabIndex
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.surface
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .clickable {
                                        activeTabIndex = index
                                        webViewInstance?.loadUrl(tab.url)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(70.dp)
                                )
                                if (tabs.size > 1) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Закрыть вкладку",
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable {
                                                tabs.removeAt(index)
                                                if (activeTabIndex >= tabs.size) {
                                                    activeTabIndex = tabs.size - 1
                                                }
                                                webViewInstance?.loadUrl(tabs[activeTabIndex].url)
                                            }
                                    )
                                }
                            }
                        }
                    }

                    if (tabs.size < 10) {
                        IconButton(
                            onClick = {
                                val newTab = BrowserTab(
                                    id = UUID.randomUUID().toString(),
                                    chatId = "",
                                    url = "https://google.com",
                                    title = "Вкладка ${tabs.size + 1}",
                                    isActive = true,
                                    orderIndex = tabs.size
                                )
                                tabs.add(newTab)
                                activeTabIndex = tabs.lastIndex
                                webViewInstance?.loadUrl(newTab.url)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.Add, contentDescription = "Новая вкладка", modifier = Modifier.size(18.dp))
                        }
                    }

                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Закрыть браузер", modifier = Modifier.size(18.dp))
                    }
                }

                // Панель навигации браузера
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (webViewInstance?.canGoBack() == true) webViewInstance?.goBack() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Назад", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { if (webViewInstance?.canGoForward() == true) webViewInstance?.goForward() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.ArrowForward, contentDescription = "Вперед", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Обновить", modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tabs.getOrNull(activeTabIndex)?.url ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // WebView с проксированием sing-box (SOCKS5/HTTP 127.0.0.1:10808/10809)
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val currentTitle = view?.title ?: "Страница"
                                    if (activeTabIndex in tabs.indices) {
                                        tabs[activeTabIndex] = tabs[activeTabIndex].copy(
                                            title = currentTitle,
                                            url = url ?: tabs[activeTabIndex].url
                                        )
                                    }
                                    // Извлечение Readability DOM (чистого текста статьи)
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            var text = document.body ? document.body.innerText : '';
                                            return text.substring(0, 10000);
                                        })();
                                        """.trimIndent()
                                    ) { extractedText ->
                                        val clean = extractedText?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""
                                        onExtractPageContent(currentTitle, clean)
                                    }
                                }
                            }
                            loadUrl(tabs[activeTabIndex].url)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
