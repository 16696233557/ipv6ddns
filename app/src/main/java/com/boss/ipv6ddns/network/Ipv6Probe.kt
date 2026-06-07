package com.boss.ipv6ddns.network

import com.boss.ipv6ddns.data.LogRepository
import com.boss.ipv6ddns.data.ProbeSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress

/**
 * Probes the public IPv6 address by hitting the user-configured echo services in order.
 * Each source is requested via OkHttp; the result is validated as a real IPv6 address
 * (rejects anything that doesn't parse via InetAddress or that resolves to IPv4).
 */
class Ipv6Probe(
    private val client: OkHttpClient,
    moshi: Moshi,
    private val log: LogRepository
) {

    private val ipifyAdapter = moshi.adapter(IpifyResponse::class.java)

    sealed class Result {
        data class Ok(val ipv6: String, val source: String) : Result()
        data class Fail(val reason: String, val source: String? = null) : Result()
    }

    /**
     * Test a single probe source in isolation. Used by the UI "Test" button so
     * the user can verify a custom source works before saving. Reuses the same
     * fetch + validation pipeline as the main loop; only the iteration is skipped.
     */
    suspend fun testOne(source: ProbeSource): Result = withContext(Dispatchers.IO) {
        try {
            val raw = fetch(source)
                ?: return@withContext Result.Fail(
                    reason = "Empty body or non-2xx",
                    source = source.name
                )
            val cleaned = raw.trim()
            if (!isValidIpv6(cleaned)) {
                val msg = "Invalid IPv6: '$cleaned'"
                log.w(TAG, "Test ${source.name} -> $msg")
                return@withContext Result.Fail(msg, source.name)
            }
            log.i(TAG, "Test ${source.name} OK: $cleaned")
            Result.Ok(cleaned, source.name)
        } catch (io: IOException) {
            val msg = io.message ?: io.javaClass.simpleName
            log.w(TAG, "Test ${source.name} IO error: $msg")
            Result.Fail(msg, source.name)
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            log.w(TAG, "Test ${source.name} threw: $msg")
            Result.Fail(msg, source.name)
        }
    }

    /**
     * Probe using the given list of sources. An empty list yields an immediate Fail.
     * The list is taken fresh on every call so the user can edit it without restarting
     * the service.
     */
    suspend fun probe(sources: List<ProbeSource>): Result {
        if (sources.isEmpty()) {
            log.w(TAG, "No probe sources configured")
            return Result.Fail("No probe sources configured")
        }
        var lastReason: String? = null
        for (source in sources) {
            try {
                val raw = fetch(source) ?: continue
                val cleaned = raw.trim()
                if (!isValidIpv6(cleaned)) {
                    val msg = "Invalid IPv6 from ${source.name}: '$cleaned'"
                    log.w(TAG, msg)
                    lastReason = msg
                    continue
                }
                log.i(TAG, "Probe OK via ${source.name}: $cleaned")
                return Result.Ok(cleaned, source.name)
            } catch (io: IOException) {
                val msg = io.message ?: io.javaClass.simpleName
                log.w(TAG, "Probe source ${source.name} failed: $msg")
                lastReason = msg
            } catch (t: Throwable) {
                val msg = t.message ?: t.javaClass.simpleName
                log.w(TAG, "Probe source ${source.name} threw: $msg")
                lastReason = msg
            }
        }
        return Result.Fail(lastReason ?: "All probe sources failed")
    }

    private suspend fun fetch(source: ProbeSource): String? = withContext(Dispatchers.IO) {
        val accept = if (source.format.equals("json", ignoreCase = true)) "application/json" else "text/plain"
        val req = Request.Builder()
            .url(source.url)
            .header("User-Agent", "IPv6DDNS/1.0 (Android)")
            .header("Accept", accept)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                log.w(TAG, "${source.name} HTTP ${resp.code}")
                return@withContext null
            }
            val body = resp.body?.string().orEmpty()
            when (source.format.lowercase()) {
                "json" -> runCatching { ipifyAdapter.fromJson(body)?.ip }.getOrNull()
                else -> body.lineSequence().firstOrNull { it.isNotBlank() }
            }
        }
    }

    private fun isValidIpv6(s: String): Boolean {
        if (s.isBlank() || s.contains('/')) return false
        // Reject obvious IPv4
        if (s.all { it.isDigit() || it == '.' }) return false
        return try {
            val addr = InetAddress.getByName(s)
            addr.hostAddress?.contains(':') == true
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private const val TAG = "Ipv6Probe"
    }
}

@JsonClass(generateAdapter = false)
data class IpifyResponse(val ip: String? = null)
