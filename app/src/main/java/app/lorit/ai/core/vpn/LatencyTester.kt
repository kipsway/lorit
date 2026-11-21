package app.lorit.ai.core.vpn

import app.lorit.ai.core.vpn.model.VpnNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Тестер задержки узлов через прямое измерение TCP Handshake.
 * Позволяет за доли секунды отсеять мертвые или заблокированные серверы.
 */
class LatencyTester {

    suspend fun testAllNodes(nodes: List<VpnNode>, timeoutMs: Int = 2000): List<VpnNode> = withContext(Dispatchers.IO) {
        val jobs = nodes.map { node ->
            async {
                val ping = measureTcpPing(node.address, node.port, timeoutMs)
                node.copy(
                    latencyMs = ping,
                    isAlive = ping >= 0
                )
            }
        }
        jobs.awaitAll()
    }

    private fun measureTcpPing(host: String, port: Int, timeoutMs: Int): Long {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1L // Узел недоступен
        }
    }
}
