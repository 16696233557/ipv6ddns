package com.boss.ipv6ddns.network

import com.aliyun.auth.credentials.Credential
import com.aliyun.auth.credentials.provider.StaticCredentialProvider
import com.aliyun.sdk.service.alidns20150109.AsyncClient
import com.aliyun.sdk.service.alidns20150109.models.AddDomainRecordRequest
import com.aliyun.sdk.service.alidns20150109.models.DescribeDomainRecordsRequest
import com.aliyun.sdk.service.alidns20150109.models.UpdateDomainRecordRequest
import com.boss.ipv6ddns.data.DDNSConfig
import com.boss.ipv6ddns.data.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps the Alidns V2 SDK (alibabacloud-alidns20150109:4.0.12).
 *
 * The V1 SDK (aliyun-java-sdk-alidns) is unmaintained on Android 14: it
 * hardcodes Apache HttpClient 4.5.x, whose
 * `AllowAllHostnameVerifier.INSTANCE` static field is missing from Android
 * 14's platform stub and cannot be replaced via R8 because the platform
 * classloader prefers the platform stub over the user-classpath version.
 *
 * The V2 SDK (Tea-style) is built on aliyun-gateway-pop +
 * darabonba-java-core, picks a `cn-hangzhou` region so its default
 * endpoint resolver lands on `alidns.aliyuncs.com`, and authenticates
 * with POP V3 signing (Sha256). No Apache HC transitives.
 *
 * [sync] is a suspending facade: the underlying V2 SDK uses
 * CompletableFuture internally, which we await without blocking a thread,
 * so the caller can dispatch to [Dispatchers.IO] and the call returns
 * only after the SDK has resolved the future.
 */
class AliyunDnsClient(
    private val log: LogRepository
) {

    sealed class Result {
        data class Updated(val recordId: String, val value: String) : Result()
        data class Created(val recordId: String, val value: String) : Result()
        data class Unchanged(val recordId: String, val value: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    @Volatile
    private var client: AsyncClient? = null

    @Volatile
    private var cachedAk: String = ""
    @Volatile
    private var cachedSk: String = ""

    /**
     * Drop the cached async client so the next [sync] call rebuilds it with
     * the current AK/SK. Called when the user saves a new config.
     */
    fun resetClient() {
        client?.close()
        client = null
    }

    /**
     * Look up the AAAA record for (subDomain.rootDomain), create or update
     * it to [ipv6] as needed. Caller is expected to dispatch to IO.
     */
    suspend fun sync(config: DDNSConfig, ipv6: String): Result = withContext(Dispatchers.IO) {
        if (config.accessKeyId.isBlank() || config.accessKeySecret.isBlank()) {
            return@withContext Result.Failure("AccessKey not configured")
        }

        try {
            val c = client(config.accessKeyId, config.accessKeySecret)
            val root = config.rootDomain.trim().lowercase().removeSuffix(".")
            val rr = shortName(config.subDomain, root)

            // 1) Find existing AAAA record
            val describeReq = DescribeDomainRecordsRequest.builder()
                .domainName(root)
                .RRKeyWord(rr)
                .type("AAAA")
                .pageSize(100L)
                .build()
            val describeResp = c.describeDomainRecords(describeReq).get()
            val records = describeResp.body?.domainRecords?.record.orEmpty()
            val existing = records.firstOrNull { it.rr == rr }

            if (existing == null) {
                // 2a) Add new
                val addReq = AddDomainRecordRequest.builder()
                    .domainName(root)
                    .rr(rr)
                    .type("AAAA")
                    .value(ipv6)
                    .TTL(600L)
                    .build()
                val addResp = c.addDomainRecord(addReq).get()
                val newId = addResp.body?.recordId
                if (newId.isNullOrBlank()) {
                    return@withContext Result.Failure("AddDomainRecord returned no recordId")
                }
                log.i(TAG, "AddDomainRecord OK: $newId")
                Result.Created(newId, ipv6)
            } else if (existing.value.equals(ipv6, ignoreCase = true)) {
                log.i(TAG, "Aliyun record already matches: ${existing.recordId}")
                Result.Unchanged(existing.recordId, ipv6)
            } else {
                // 2b) Update existing
                val updReq = UpdateDomainRecordRequest.builder()
                    .recordId(existing.recordId)
                    .rr(existing.rr)
                    .type("AAAA")
                    .value(ipv6)
                    .TTL(existing.getTTL() ?: 600L)
                    .build()
                c.updateDomainRecord(updReq).get()
                log.i(TAG, "UpdateDomainRecord OK: ${existing.recordId}")
                Result.Updated(existing.recordId, ipv6)
            }
        } catch (t: Throwable) {
            val sw = java.io.StringWriter()
            t.printStackTrace(java.io.PrintWriter(sw))
            val stack = sw.toString()
            val msg = t.message ?: t.javaClass.simpleName
            // Also dump to logcat so the user can grab the stack with `adb
            // logcat` even if the in-memory LogRepository rotates its ring
            // buffer between probe cycles.
            android.util.Log.e(TAG, "Alidns sync failed: $msg\n$stack")
            log.e(TAG, "Alidns sync failed: $msg")
            Result.Failure("$msg (caused by ${t.cause?.javaClass?.simpleName ?: "none"})")
        }
    }

    private fun client(ak: String, sk: String): AsyncClient {
        val existing = client
        if (existing != null && ak == cachedAk && sk == cachedSk) return existing
        existing?.close()

        val cred = Credential.builder()
            .accessKeyId(ak)
            .accessKeySecret(sk)
            .build()
        val provider = StaticCredentialProvider.create(cred)

        // cn-hangzhou so the V2 SDK's default endpoint resolver computes
        // `alidns.cn-hangzhou.aliyuncs.com`. Alidns is a global service
        // but the SDK requires *some* region code; cn-hangzhou is the
        // canonical alidns region used by every official SDK example.
        val c = AsyncClient.builder()
            .region(REGION)
            .credentialsProvider(provider)
            .build()
        client = c
        cachedAk = ak
        cachedSk = sk
        return c
    }

    /**
     * Split a configured subdomain into the bare RR + root domain.
     * If user enters "home.example.com" with root "example.com" → rr="home", root="example.com".
     * If user enters "@" or the same as root → rr="@".
     */
    private fun shortName(sub: String, root: String): String {
        if (sub.isBlank()) return "@"
        val s = sub.trim().lowercase().removeSuffix(".")
        val r = root.trim().lowercase().removeSuffix(".")
        if (s == r) return "@"
        if (s.endsWith(".$r")) return s.removeSuffix(".$r")
        return s
    }

    companion object {
        private const val TAG = "AliyunDns"
        private const val REGION = "cn-hangzhou"
    }
}
