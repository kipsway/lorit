# 🔐 Хранилище полезных данных и конфигураций (Data Vault)

> **Назначение:** Централизованное хранилище всех системных ссылок, эндпоинтов, структур конфигурации, шаблонов промптов и правил безопасности проекта.  
> **Статус:** Конфиденциально / Внутренние параметры проекта.

---

## 1. Конфигурация сетевого ядра и подписки V2Ray / Xray

* **Основная ссылка на подписку (Subscription URL):**
  ```
  https://s.wistfalls.cc/fPLkfFpVXdgS3Fkp
  ```
* **Тип подписки:** Base64-encoded stream конфигураций (VLESS Reality / VMess / Trojan / Shadowsocks).
* **Параметры локального встроенного прокси (In-App Proxy):**
  * SOCKS5 хост и порт: `127.0.0.1:10808`
  * HTTP хост и порт: `127.0.0.1:10809`
* **Эндпоинты для проверки задержки (Latency Healthcheck):**
  * `https://api.openai.com/v1/models`
  * `https://api.anthropic.com/v1/messages`
  * `https://generativelanguage.googleapis.com`
  * `https://claude.ai`
* **Периодичность автообновления списка серверов:** каждые 24 часа через `PeriodicWorkRequest`.

### 1.1. Базовый шаблон JSON-конфигурации sing-box
```json
{
  "log": { "level": "warn", "timestamp": true },
  "dns": {
    "servers": [
      { "tag": "cloudflare-doh", "address": "https://1.1.1.1/dns-query" },
      { "tag": "local-dns", "address": "223.5.5.5", "detour": "direct" }
    ],
    "rules": [
      { "outbound": "any", "server": "cloudflare-doh" }
    ]
  },
  "inbounds": [
    {
      "type": "mixed",
      "tag": "mixed-in",
      "listen": "127.0.0.1",
      "listen_port": 10808,
      "sniff": true
    }
  ],
  "outbounds": [
    {
      "type": "urltest",
      "tag": "auto-best",
      "outbounds": ["node-1", "node-2"],
      "url": "https://api.openai.com",
      "interval": "3m",
      "tolerance": 50
    },
    { "type": "direct", "tag": "direct" },
    { "type": "block", "tag": "block" }
  ],
  "route": {
    "rules": [
      { "domain_suffix": ["openai.com", "anthropic.com", "claude.ai", "google.com", "googleapis.com", "deepseek.com"], "outbound": "auto-best" },
      { "outbound": "direct" }
    ]
  }
}
```

---

## 2. Эндпоинты провайдеров и структуры авторизации

### 2.1. Режим API-ключей (Direct API)
| Провайдер | Базовый URL API | Формат заголовка авторизации |
| :--- | :--- | :--- |
| **OpenAI** | `https://api.openai.com/v1` | `Authorization: Bearer <API_KEY>` |
| **Anthropic** | `https://api.anthropic.com/v1` | `x-api-key: <API_KEY>`, `anthropic-version: 2023-06-01` |
| **Google Gemini** | `https://generativelanguage.googleapis.com/v1beta` | `x-goog-api-key: <API_KEY>` |
| **DeepSeek** | `https://api.deepseek.com/v1` | `Authorization: Bearer <API_KEY>` |

---

### 2.2. Режим веб-сессий (Web Session Emulation)
| Сервис | Стартовый URL входа в WebView | Целевые куки / Токены | Эндпоинт опроса моделей аккаунта |
| :--- | :--- | :--- | :--- |
| **Claude** | `https://claude.ai/login` | `sessionKey` | `GET https://claude.ai/api/organizations/{org_id}/models` |
| **ChatGPT** | `https://chatgpt.com/auth/login` | `__Secure-next-auth.session-token`, Bearer | `GET https://chatgpt.com/backend-api/models` |
| **Gemini** | `https://gemini.google.com` | `SID`, `HSID`, `SSID`, `__Secure-1PSID`, `SNlM0e` | Внутренний сервисный RPC / Google AI Studio API |
| **DeepSeek** | `https://chat.deepseek.com` | `userToken` | `GET https://chat.deepseek.com/api/v0/models` |

* **Стандартный User-Agent для WebView (Android Chrome):**
  ```text
  Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UQ1A.240105.004) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36
  ```

---

## 3. Точный шаблон сообщения при наличии вложений

Когда пользователь прикрепляет файлы или вставляет скопированный текст, клиент собирает запрос строго по следующему формату:

```text
Вложение:
{содержимое прикрепленного файла или текст из поля вложения}

Сообщение пользователя:
{основной текст из поля ввода}

При работе учитывай и прикрепленные вложения и сообщение пользователя.
```

---

## 4. Сквозные пользовательские инструкции (Custom Instructions)

Системный контекст, подключаемый к диалогам:
```text
Ты — персональный ИИ-ассистент в нативном клиенте Android.
Отвечай строго по делу, структурированно, грамотно и без лишних предисловий.
Если пользователю требуется программный код — форматируй его в стандартные Markdown-блоки с указанием языка.
При наличии этапа размышлений оформляй его строго в теги <think>...</think>.
```

---

## 5. Алгоритм очистки текста при копировании (WYSIWYG Clean Copy)

При нажатии кнопки «Копировать» к тексту применяется конвейер очистки:

1. **Удаление блоков размышлений (Thinking):**
   ```regex
   (?s)<think>.*?</think>
   ```
2. **Удаление служебных XML-оберток и системных тегов:**
   ```regex
   (?s)<.*?>
   ```
3. **Очистка Markdown-символов (при копировании как чистый текст):**
   ```regex
   ^#{1,6}\s* -> ""
   \*\*(.*?)\*\* -> "$1"
   \*(.*?)\* -> "$1"
   ```

---

## 6. Полная схема таблиц локальной базы данных Room (SQLCipher)

```sql
-- Таблица чатов
CREATE TABLE IF NOT EXISTS `chats` (
    `id` TEXT PRIMARY KEY NOT NULL,
    `title` TEXT NOT NULL,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `active_provider` TEXT NOT NULL,
    `active_model_id` TEXT NOT NULL,
    `is_temporary` INTEGER NOT NULL DEFAULT 0
);

-- Таблица сообщений
CREATE TABLE IF NOT EXISTS `messages` (
    `id` TEXT PRIMARY KEY NOT NULL,
    `chat_id` TEXT NOT NULL,
    `sender` TEXT NOT NULL, -- 'USER' | 'ASSISTANT' | 'SYSTEM'
    `content` TEXT NOT NULL,
    `thinking_process` TEXT,
    `thinking_duration_ms` INTEGER NOT NULL DEFAULT 0,
    `status` TEXT NOT NULL, -- 'PENDING' | 'STREAMING' | 'COMPLETED' | 'ERROR' | 'OUTBOX_QUEUED'
    `created_at` INTEGER NOT NULL,
    FOREIGN KEY(`chat_id`) REFERENCES `chats`(`id`) ON DELETE CASCADE
);

-- Таблица вложений к сообщениям
CREATE TABLE IF NOT EXISTS `attachments` (
    `id` TEXT PRIMARY KEY NOT NULL,
    `message_id` TEXT NOT NULL,
    `type` TEXT NOT NULL, -- 'IMAGE' | 'FILE_DOCUMENT' | 'RAW_TEXT'
    `display_name` TEXT NOT NULL,
    `raw_content` TEXT,
    `local_file_uri` TEXT,
    `file_size_bytes` INTEGER NOT NULL,
    FOREIGN KEY(`message_id`) REFERENCES `messages`(`id`) ON DELETE CASCADE
);

-- Таблица вкладок встроенного браузера
CREATE TABLE IF NOT EXISTS `browser_tabs` (
    `id` TEXT PRIMARY KEY NOT NULL,
    `chat_id` TEXT NOT NULL,
    `url` TEXT NOT NULL,
    `title` TEXT NOT NULL,
    `is_active` INTEGER NOT NULL DEFAULT 0,
    `order_index` INTEGER NOT NULL,
    FOREIGN KEY(`chat_id`) REFERENCES `chats`(`id`) ON DELETE CASCADE
);

-- Виртуальная FTS5-таблица для полнотекстового поиска
CREATE VIRTUAL TABLE IF NOT EXISTS `messages_fts` USING fts5(
    content,
    content='messages',
    content_rowid='rowid'
);
```

---

## 7. Параметры ProGuard / R8 для релизной сборки

```proguard
# Сохранение нативных вызовов ядра sing-box
-keep class io.nekohasekai.libbox.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Сохранение сериализации моделей данных
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @kotlinx.serialization.SerialName <fields>;
}

# Сохранение сущностей Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
```

---

## 8. Спецификация механизма автообновления (In-App Updater)

* **Эндпоинт проверки релизов:**
  ```text
  GET https://api.github.com/repos/{OWNER}/{REPO}/releases/latest
  Headers:
    Accept: application/vnd.github.v3+json
    User-Agent: Gemini-AI-Hub-Android
  ```
* **Парсинг ответа:**
  * Поле `tag_name` (например, `v1.2.0`). Сравнивается с `BuildConfig.VERSION_NAME`.
  * Поле `body` — список изменений (Changelog) для отображения пользователю.
  * Поле `assets` -> поиск файла с расширением `.apk` и извлечение `browser_download_url`.
* **Загрузка:** Через системный `DownloadManager` в папку `Environment.DIRECTORY_DOWNLOADS`.
* **Установка:** Запуск `ACTION_VIEW` с MIME-типом `application/vnd.android.package-archive` через `FileProvider`.
