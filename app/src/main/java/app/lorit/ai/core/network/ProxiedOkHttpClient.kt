package app.lorit.ai.core.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Фабрика HTTP-клиента OkHttp.
 * Направляет все исходящие сетевые запросы приложения через локальный In-App прокси sing-box.
 */
object ProxiedOkHttpClient {

    private const val DEFAULT_TIMEOUT_SECONDS = 60L
    private const val PROXY_PORT = 10808

    fun create(useProxy: Boolean = true): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (useProxy) {
            // Маршрутизация через локальный SOCKS5 прокси ядра sing-box
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", PROXY_PORT))
            builder.proxy(proxy)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        builder.addInterceptor(logging)

        return builder.build()
    }
}
