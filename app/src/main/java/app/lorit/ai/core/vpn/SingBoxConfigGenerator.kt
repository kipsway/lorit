package app.lorit.ai.core.vpn

import app.lorit.ai.core.vpn.model.VpnNode
import app.lorit.ai.core.vpn.model.VpnProtocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * Генератор конфигурационного файла sing-box в формате JSON.
 * Настраивает локальный Mixed прокси (SOCKS5/HTTP), FakeDNS, DoH и маршруты к ИИ-сервисам.
 */
class SingBoxConfigGenerator {

    fun generateConfig(
        nodes: List<VpnNode>,
        selectedNodeId: String? = null,
        localPort: Int = 10808
    ): String {
        val root = JSONObject()

        // 1. Log
        val log = JSONObject().apply {
            put("level", "warn")
            put("timestamp", true)
        }
        root.put("log", log)

        // 2. DNS (Cloudflare DoH для исключения утечек)
        val dns = JSONObject().apply {
            val servers = JSONArray().apply {
                put(JSONObject().apply {
                    put("tag", "cloudflare-doh")
                    put("address", "https://1.1.1.1/dns-query")
                    put("detour", "proxy-out")
                })
                put(JSONObject().apply {
                    put("tag", "direct-dns")
                    put("address", "223.5.5.5")
                    put("detour", "direct")
                })
            }
            put("servers", servers)
            val rules = JSONArray().apply {
                put(JSONObject().apply {
                    put("outbound", "any")
                    put("server", "cloudflare-doh")
                })
            }
            put("rules", rules)
        }
        root.put("dns", dns)

        // 3. Inbounds (Локальный Mixed SOCKS5/HTTP прокси)
        val inbounds = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", "127.0.0.1")
                put("listen_port", localPort)
                put("sniff", true)
            })
        }
        root.put("inbounds", inbounds)

        // 4. Outbounds (Узлы из подписки + Direct + Block)
        val outbounds = JSONArray()
        val nodeTags = mutableListOf<String>()

        nodes.forEachIndexed { index, node ->
            val tag = "node-$index"
            nodeTags.add(tag)
            val nodeOutbound = createOutboundForNode(node, tag)
            if (nodeOutbound != null) {
                outbounds.put(nodeOutbound)
            }
        }

        // Селектор (выбранный узел или fallback urltest)
        val defaultNodeTag = if (selectedNodeId != null) {
            val foundIdx = nodes.indexOfFirst { it.id == selectedNodeId }
            if (foundIdx >= 0) "node-$foundIdx" else nodeTags.firstOrNull() ?: "direct"
        } else {
            nodeTags.firstOrNull() ?: "direct"
        }

        val selectorOutbound = JSONObject().apply {
            put("type", "selector")
            put("tag", "proxy-out")
            val tagsArray = JSONArray()
            tagsArray.put(defaultNodeTag)
            nodeTags.forEach { tagsArray.put(it) }
            put("outbounds", tagsArray)
            put("default", defaultNodeTag)
        }
        outbounds.put(selectorOutbound)

        // Прямой выход
        outbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "direct")
        })

        // Блокировка
        outbounds.put(JSONObject().apply {
            put("type", "block")
            put("tag", "block")
        })

        root.put("outbounds", outbounds)

        // 5. Route Rules (Туннелирование ИИ и встроенного браузера)
        val route = JSONObject().apply {
            val rules = JSONArray().apply {
                put(JSONObject().apply {
                    val domains = JSONArray().apply {
                        put("openai.com")
                        put("chatgpt.com")
                        put("anthropic.com")
                        put("claude.ai")
                        put("google.com")
                        put("googleapis.com")
                        put("gemini.google.com")
                        put("deepseek.com")
                        put("gstatic.com")
                    }
                    put("domain_suffix", domains)
                    put("outbound", "proxy-out")
                })
                // Весь входящий трафик с mixed-in порта идет через proxy-out
                put(JSONObject().apply {
                    put("inbound", "mixed-in")
                    put("outbound", "proxy-out")
                })
            }
            put("rules", rules)
            put("auto_detect_interface", true)
        }
        root.put("route", route)

        return root.toString(2)
    }

    private fun createOutboundForNode(node: VpnNode, tag: String): JSONObject? {
        return try {
            when (node.protocol) {
                VpnProtocol.VLESS -> JSONObject().apply {
                    put("type", "vless")
                    put("tag", tag)
                    put("server", node.address)
                    put("server_port", node.port)
                    put("uuid", node.uuid)
                    if (node.flow.isNotEmpty()) put("flow", node.flow)

                    if (node.security == "reality") {
                        put("tls", JSONObject().apply {
                            put("enabled", true)
                            put("server_name", node.sni)
                            put("reality", JSONObject().apply {
                                put("enabled", true)
                                put("public_key", node.publicKey)
                                put("short_id", node.shortId)
                            })
                        })
                    } else if (node.security == "tls") {
                        put("tls", JSONObject().apply {
                            put("enabled", true)
                            put("server_name", node.sni)
                        })
                    }
                }
                VpnProtocol.VMESS -> JSONObject().apply {
                    put("type", "vmess")
                    put("tag", tag)
                    put("server", node.address)
                    put("server_port", node.port)
                    put("uuid", node.uuid)
                    put("security", "auto")
                }
                VpnProtocol.TROJAN -> JSONObject().apply {
                    put("type", "trojan")
                    put("tag", tag)
                    put("server", node.address)
                    put("server_port", node.port)
                    put("password", node.uuid)
                    put("tls", JSONObject().apply {
                        put("enabled", true)
                        put("server_name", node.sni)
                    })
                }
                VpnProtocol.SHADOWSOCKS -> JSONObject().apply {
                    put("type", "shadowsocks")
                    put("tag", tag)
                    put("server", node.address)
                    put("server_port", node.port)
                    put("method", "chacha20-ietf-poly1305")
                    put("password", node.uuid)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
