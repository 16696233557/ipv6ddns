package com.boss.ipv6ddns.data

/**
 * A user-configurable IPv6 echo endpoint. `format` is the response shape:
 *  - "text": first non-blank line is the IPv6 (e.g. https://v6.ident.me)
 *  - "json": body parses as `{"ip": "..."}` (e.g. https://api64.ipify.org?format=json)
 */
data class ProbeSource(
    val name: String,
    val url: String,
    val format: String = "text"
)

/**
 * Snapshot of user config + last known state.
 * `lastKnownIpv6Hash` lets us cheaply detect "no change" without re-encoding strings.
 */
data class DDNSConfig(
    val accessKeyId: String = "",
    val accessKeySecret: String = "",
    val rootDomain: String = "",
    val subDomain: String = "",
    val intervalMinutes: Int = ConfigRepository.DEFAULT_INTERVAL_MINUTES,
    val lastKnownIpv6: String = "",
    val lastKnownIpv6Hash: Long = 0L,
    val lastCheckAtMillis: Long = 0L,
    val lastError: String = "",
    val lastDnsValue: String = "",
    val lastDnsRecordId: String = "",
    val lastSuccessAtMillis: Long = 0L,
    val probeSources: List<ProbeSource> = ConfigRepository.DEFAULT_PROBE_SOURCES
) {
    val fqdn: String
        get() = if (subDomain.endsWith(".$rootDomain", ignoreCase = true) || subDomain.equals(rootDomain, true)) {
            subDomain.lowercase()
        } else {
            "$subDomain.$rootDomain".lowercase()
        }
}
