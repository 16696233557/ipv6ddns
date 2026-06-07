package com.boss.ipv6ddns.network

import com.aliyuncs.DefaultAcsClient
import com.aliyuncs.IAcsClient
import com.aliyuncs.alidns.model.v20150109.AddDomainRecordRequest
import com.aliyuncs.alidns.model.v20150109.DescribeDomainRecordsRequest
import com.aliyuncs.alidns.model.v20150109.DescribeDomainRecordsResponse
import com.aliyuncs.alidns.model.v20150109.UpdateDomainRecordRequest
import com.aliyuncs.profile.DefaultProfile
import com.boss.ipv6ddns.data.DDNSConfig
import com.boss.ipv6ddns.data.LogRepository

/**
 * Wraps the Alidns SDK: lookup the AAAA record, create or update as needed.
 * Thread-blocking (synchronous) — call sites must dispatch to IO.
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

    private var client: IAcsClient? = null

    @Synchronized
    private fun client(ak: String, sk: String): IAcsClient {
        val existing = client
        if (existing != null) return existing
        val profile = DefaultProfile.getProfile(REGION, ak, sk)
        // Alidns uses a global endpoint regardless of region
        DefaultProfile.addEndpoint(REGION, "alidns", "alidns.aliyuncs.com")
        val c = DefaultAcsClient(profile)
        client = c
        return c
    }

    fun resetClient() {
        client = null
    }

    fun sync(config: DDNSConfig, ipv6: String): Result {
        if (config.accessKeyId.isBlank() || config.accessKeySecret.isBlank()) {
            return Result.Failure("AccessKey not configured")
        }
        return try {
            val c = client(config.accessKeyId, config.accessKeySecret)
            val fqdn = config.fqdn
            val root = config.rootDomain

            // 1) Find existing AAAA record
            val describe = DescribeDomainRecordsRequest().apply {
                domainName = root
                rrKeyWord = shortName(config.subDomain, root)
                typeKeyWord = "AAAA"
            }
            val describeResp: DescribeDomainRecordsResponse = c.getAcsResponse(describe)
            val records = describeResp.domainRecords.orEmpty()
            val existing = records.firstOrNull { it.rr == shortName(config.subDomain, root) }

            if (existing == null) {
                // 2a) Add new
                val add = AddDomainRecordRequest().apply {
                    domainName = root
                    rr = shortName(config.subDomain, root)
                    type = "AAAA"
                    value = ipv6
                }
                val resp = c.getAcsResponse(add)
                val newId = resp.recordId
                log.i(TAG, "AddDomainRecord OK: $newId")
                Result.Created(newId, ipv6)
            } else if (existing.value.equals(ipv6, ignoreCase = true)) {
                log.i(TAG, "Aliyun record already matches: ${existing.recordId}")
                Result.Unchanged(existing.recordId, ipv6)
            } else {
                // 2b) Update existing
                val upd = UpdateDomainRecordRequest().apply {
                    recordId = existing.recordId
                    rr = existing.rr
                    type = "AAAA"
                    value = ipv6
                }
                c.getAcsResponse(upd)
                log.i(TAG, "UpdateDomainRecord OK: ${existing.recordId}")
                Result.Updated(existing.recordId, ipv6)
            }
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            log.e(TAG, "Alidns sync failed: $msg")
            Result.Failure(msg)
        }
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
        // Alidns is a global service but the SDK needs *some* region code;
        // the endpoint is hard-coded via DefaultProfile.addEndpoint below.
        private const val REGION = "cn-hangzhou"
    }
}
