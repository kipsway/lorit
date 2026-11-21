package app.lorit.ai.core.vpn.model

import kotlinx.serialization.Serializable

/**
 * Типы поддерживаемых протоколов V2Ray / Xray
 */
enum class VpnProtocol {
    VLESS,
    VMESS,
    TROJAN,
    SHADOWSOCKS
}

/**
 * Унифицированное представление сервера из подписки
 */
@Serializable
data class VpnNode(
    val id: String,
    val name: String,
    val protocol: VpnProtocol,
    val address: String,
    val port: Int,
    val uuid: String = "",
    val security: String = "auto", // reality, tls, none
    val sni: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val flow: String = "", // xtls-rprx-vision
    val path: String = "",
    val network: String = "tcp", // tcp, ws, grpc
    var latencyMs: Long = -1L,
    var isAlive: Boolean = true
)
