package app.lorit.ai.core.vpn

import app.lorit.ai.core.vpn.model.VpnNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Маршрутизатор с механизмом Auto-Failover:
 * Выбирает лучший по задержке узел и мгновенно переключается на резервный при обрыве связи.
 */
class FailoverRouter(
    private val latencyTester: LatencyTester = LatencyTester()
) {
    private val _nodes = MutableStateFlow<List<VpnNode>>(emptyList())
    val nodes: StateFlow<List<VpnNode>> = _nodes.asStateFlow()

    private val _activeNode = MutableStateFlow<VpnNode?>(null)
    val activeNode: StateFlow<VpnNode?> = _activeNode.asStateFlow()

    fun updateNodes(newNodes: List<VpnNode>) {
        _nodes.value = newNodes
    }

    suspend fun autoSelectBestNode(): VpnNode? {
        val currentList = _nodes.value
        if (currentList.isEmpty()) return null

        val tested = latencyTester.testAllNodes(currentList)
        _nodes.value = tested

        val best = tested.filter { it.isAlive && it.latencyMs > 0 }
            .minByOrNull { it.latencyMs }

        _activeNode.value = best ?: currentList.firstOrNull()
        return _activeNode.value
    }

    suspend fun failoverToNextNode(): VpnNode? {
        val current = _activeNode.value
        val available = _nodes.value.filter { it.id != current?.id && it.isAlive }

        val nextBest = available.minByOrNull { if (it.latencyMs > 0) it.latencyMs else Long.MAX_VALUE }
            ?: _nodes.value.firstOrNull { it.id != current?.id }

        _activeNode.value = nextBest
        return nextBest
    }

    fun selectNodeManually(nodeId: String) {
        val found = _nodes.value.find { it.id == nodeId }
        if (found != null) {
            _activeNode.value = found
        }
    }
}
