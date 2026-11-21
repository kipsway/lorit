package app.lorit.ai.core.vpn

import android.util.Base64
import android.net.Uri
import app.lorit.ai.core.vpn.model.VpnNode
import app.lorit.ai.core.vpn.model.VpnProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Парсер подписок V2Ray / Xray / sing-box.
 * Поддерживает Base64-потоки, форматы vless://, vmess://, trojan://, ss://
 */
class SubscriptionParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawContent: String): List<VpnNode> {
        val decoded = tryDecodeBase64(rawContent.trim())
        val lines = decoded.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val nodes = mutableListOf<VpnNode>()

        for (line in lines) {
            try {
                when {
                    line.startsWith("vless://", ignoreCase = true) -> parseVless(line)?.let { nodes.add(it) }
                    line.startsWith("vmess://", ignoreCase = true) -> parseVmess(line)?.let { nodes.add(it) }
                    line.startsWith("trojan://", ignoreCase = true) -> parseTrojan(line)?.let { nodes.add(it) }
                    line.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(line)?.let { nodes.add(it) }
                }
            } catch (e: Exception) {
                // Пропускаем поврежденные строки
            }
        }
        return nodes
    }

    private fun parseVless(uriStr: String): VpnNode? {
        val uri = Uri.parse(uriStr)
        val uuid = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val rawName = uri.fragment ?: "$host:$port"
        val name = decodeUrlComponent(rawName)

        val security = uri.getQueryParameter("security") ?: "none"
        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: host
        val pbk = uri.getQueryParameter("pbk") ?: ""
        val sid = uri.getQueryParameter("sid") ?: ""
        val flow = uri.getQueryParameter("flow") ?: ""
        val network = uri.getQueryParameter("type") ?: "tcp"
        val path = uri.getQueryParameter("path") ?: ""

        return VpnNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = VpnProtocol.VLESS,
            address = host,
            port = port,
            uuid = uuid,
            security = security,
            sni = sni,
            publicKey = pbk,
            shortId = sid,
            flow = flow,
            network = network,
            path = path
        )
    }

    private fun parseVmess(uriStr: String): VpnNode? {
        val b64 = uriStr.removePrefix("vmess://").removePrefix("VMESS://")
        val decodedJsonStr = String(Base64.decode(b64, Base64.URL_SAFE or Base64.DEFAULT), StandardCharsets.UTF_8)
        val jsonObj = json.parseToJsonElement(decodedJsonStr).jsonObject

        val host = jsonObj["add"]?.jsonPrimitive?.content ?: return null
        val port = jsonObj["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 443
        val uuid = jsonObj["id"]?.jsonPrimitive?.content ?: return null
        val name = jsonObj["ps"]?.jsonPrimitive?.content ?: "$host:$port"
        val net = jsonObj["net"]?.jsonPrimitive?.content ?: "tcp"
        val path = jsonObj["path"]?.jsonPrimitive?.content ?: ""
        val tls = jsonObj["tls"]?.jsonPrimitive?.content ?: "none"
        val sni = jsonObj["sni"]?.jsonPrimitive?.content ?: host

        return VpnNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = VpnProtocol.VMESS,
            address = host,
            port = port,
            uuid = uuid,
            security = if (tls.equals("tls", true)) "tls" else "none",
            sni = sni,
            network = net,
            path = path
        )
    }

    private fun parseTrojan(uriStr: String): VpnNode? {
        val uri = Uri.parse(uriStr)
        val password = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val name = decodeUrlComponent(uri.fragment ?: "$host:$port")
        val sni = uri.getQueryParameter("sni") ?: host

        return VpnNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = VpnProtocol.TROJAN,
            address = host,
            port = port,
            uuid = password,
            security = "tls",
            sni = sni
        )
    }

    private fun parseShadowsocks(uriStr: String): VpnNode? {
        val uri = Uri.parse(uriStr)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 8388
        val name = decodeUrlComponent(uri.fragment ?: "$host:$port")

        return VpnNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = VpnProtocol.SHADOWSOCKS,
            address = host,
            port = port
        )
    }

    private fun tryDecodeBase64(input: String): String {
        return try {
            val decodedBytes = Base64.decode(input, Base64.DEFAULT or Base64.URL_SAFE)
            val str = String(decodedBytes, StandardCharsets.UTF_8)
            if (str.contains("://")) str else input
        } catch (e: Exception) {
            input
        }
    }

    private fun decodeUrlComponent(value: String): String {
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            value
        }
    }
}
