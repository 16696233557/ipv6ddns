package com.boss.ipv6ddns.service

import com.boss.ipv6ddns.data.DDNSConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state for status screen + notification text.
 * Lives on the AppContainer, updated by the service or worker.
 */
class DDNSState {

    data class Snapshot(
        val serviceRunning: Boolean = false,
        val lastKnownIpv6: String = "",
        val lastDnsValue: String = "",
        val lastDnsRecordId: String = "",
        val lastCheckAtMillis: Long = 0L,
        val lastSuccessAtMillis: Long = 0L,
        val lastError: String = "",
        val nextCheckAtMillis: Long = 0L,
        val config: DDNSConfig = DDNSConfig()
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun update(transform: (Snapshot) -> Snapshot) {
        _state.value = transform(_state.value)
    }

    fun setRunning(running: Boolean) = update { it.copy(serviceRunning = running) }
    fun setNextCheckAt(epoch: Long) = update { it.copy(nextCheckAtMillis = epoch) }
    fun setLastError(msg: String) = update { it.copy(lastError = msg) }
    fun setLastSuccess(ipv6: String, dnsValue: String, recordId: String) = update {
        it.copy(
            lastKnownIpv6 = ipv6,
            lastDnsValue = dnsValue,
            lastDnsRecordId = recordId,
            lastSuccessAtMillis = System.currentTimeMillis()
        )
    }
    fun setLastCheck() = update { it.copy(lastCheckAtMillis = System.currentTimeMillis()) }
    fun setConfigSnapshot(c: DDNSConfig) = update { it.copy(config = c) }
}
