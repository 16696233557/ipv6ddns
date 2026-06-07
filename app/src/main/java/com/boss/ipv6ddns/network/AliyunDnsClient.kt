package com.boss.ipv6ddns.network

import com.boss.ipv6ddns.data.DDNSConfig
import com.boss.ipv6ddns.data.LogRepository
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.SortedMap
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Hand-rolled Aliyun DNS client.
 *
 * Talks to the Alidns RPC-style REST API over HTTPS directly via OkHttp,
 * signing every request with POP V3 (HMAC-SHA1). Replaces the v2
 * alibabacloud-alidns20150109 SDK, which failed at runtime on Android 14
 * inside darabonba-core's `UserAgentPolicy.<clinit>` static initializer
 * (it tried to read `user-agent.properties` from the classpath and got
 * `null`).
 *
 * Endpoints used:
 *  - DescribeDomainRecords — list AAAA records for a domain
 *  - AddDomainRecord       — create a new AAAA record
 *  - UpdateDomainRecord    — change value / TTL of an existing record
 *
 * Signing: https://help.aliyun.com/document_detail/29745.html
 * The signature canonicalisation is the same for every Aliyun RPC
 * endpoint, so this single signer covers all three calls.
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

    /**
     * No-op kept for ABI compatibility. The old SDK kept a cached
     * AsyncClient that had to be torn down when the user changed their
     * AccessKey; the OkHttp-based implementation rebuilds nothing per-call
     * and so has nothing to reset.
     */
    fun resetClient() {
        // intentionally empty
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
            val root = config.rootDomain.trim().lowercase().removeSuffix(".")
            val rr = shortName(config.subDomain, root)

            // 1) Find existing AAAA record
            val existing = describeAaaa(config.accessKeyId, config.accessKeySecret, root, rr)

            if (existing == null) {
                // 2a) Add new
                val newId = addAaaa(
                    config.accessKeyId, config.accessKeySecret, root, rr, ipv6
                )
                if (newId.isBlank()) {
                    return@withContext Result.Failure("AddDomainRecord returned no recordId")
                }
                log.i(TAG, "AddDomainRecord OK: $newId")
                Result.Created(newId, ipv6)
            } else if (existing.value.equals(ipv6, ignoreCase = true)) {
                log.i(TAG, "Aliyun record already matches: ${existing.recordId}")
                Result.Unchanged(existing.recordId, ipv6)
            } else {
                // 2b) Update existing
                updateAaaa(
                    config.accessKeyId, config.accessKeySecret,
                    existing.recordId, rr, ipv6, existing.ttl ?: 600L
                )
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

    // -- internal REST helpers -------------------------------------------

    private data class ExistingRecord(
        val recordId: String,
        val rr: String,
        val value: String,
        val ttl: Long?
    )

    private fun describeAaaa(
        ak: String, sk: String, root: String, rr: String
    ): ExistingRecord? {
        val params = sortedMapOf(
            "Action" to "DescribeDomainRecords",
            "DomainName" to root,
            "RRKeyWord" to rr,
            "Type" to "AAAA",
            "PageSize" to "100"
        )
        val body = executeGet(ak, sk, params)
        val resp = ADAPTER_DESCRIBE.fromJson(body)
            ?: throw AliyunApiException("DescribeDomainRecords: empty response body")
        val record = resp.DomainRecords?.Record
            ?.firstOrNull { it.RR == rr && it.Type.equals("AAAA", ignoreCase = true) }
            ?: return null
        if (record.RecordId.isBlank() || record.Value.isBlank()) {
            throw AliyunApiException("DescribeDomainRecords: malformed record entry")
        }
        return ExistingRecord(record.RecordId, record.RR, record.Value, record.TTL)
    }

    private fun addAaaa(
        ak: String, sk: String, root: String, rr: String, ipv6: String
    ): String {
        val params = sortedMapOf(
            "Action" to "AddDomainRecord",
            "DomainName" to root,
            "RR" to rr,
            "Type" to "AAAA",
            "Value" to ipv6,
            "TTL" to "600"
        )
        val body = executeGet(ak, sk, params)
        val resp = ADAPTER_EMPTY.fromJson(body)
            ?: throw AliyunApiException("AddDomainRecord: empty response body")
        // AddDomainRecord's success body is `{"RecordId":"...","RequestId":"..."}`
        // but Moshi is happy to ignore the unknown RecordId field and leave it
        // null in EmptyResp — fall back to a raw field extraction so we don't
        // need a second adapter.
        val newId = extractRecordId(body)
        if (newId.isBlank()) {
            throw AliyunApiException("AddDomainRecord: response missing RecordId (RequestId=${resp.RequestId ?: "?"})")
        }
        return newId
    }

    private fun updateAaaa(
        ak: String, sk: String, recordId: String, rr: String, ipv6: String, ttl: Long
    ) {
        val params = sortedMapOf(
            "Action" to "UpdateDomainRecord",
            "RecordId" to recordId,
            "RR" to rr,
            "Type" to "AAAA",
            "Value" to ipv6,
            "TTL" to ttl.toString()
        )
        val body = executeGet(ak, sk, params)
        val resp = ADAPTER_EMPTY.fromJson(body)
            ?: throw AliyunApiException("UpdateDomainRecord: empty response body")
        // We don't get a RecordId back from Update; a 200 with no `Code` is
        // the success signal. Moshi tolerates extra fields, so we just
        // ensure parsing didn't silently drop everything.
        if (resp.RequestId == null) {
            // body parsed but RequestId missing — treat as soft warning only;
            // the empty-shape success body for Update has RequestId, so this
            // is just defensive.
            log.i(TAG, "UpdateDomainRecord returned body without RequestId (len=${body.length})")
        }
    }

    /**
     * Sign + dispatch a GET to alidns.aliyuncs.com. Returns the raw
     * response body; throws AliyunApiException on Aliyun-level error
     * responses and IOException on transport failures.
     */
    private fun executeGet(
        ak: String, sk: String, bizParams: SortedMap<String, String>
    ): String {
        // 1. Compose public + business params, then sign.
        val publicParams: SortedMap<String, String> = sortedMapOf(
            "Format" to "JSON",
            "Version" to "2015-01-09",
            "AccessKeyId" to ak,
            "SignatureMethod" to "HMAC-SHA1",
            "Timestamp" to DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            "SignatureVersion" to "1.0",
            "SignatureNonce" to UUID.randomUUID().toString()
        )
        // Merge, then re-sort (TreeMap is sorted, but we want public+biz
        // merged; the easiest way is to put business params on top and let
        // TreeMap resort).
        val all = sortedMapOf<String, String>()
        all.putAll(publicParams)
        all.putAll(bizParams)

        val signed = sign("GET", all, sk)
        all["Signature"] = signed

        // 2. Build URL.
        val urlBuilder = ENDPOINT.toHttpUrl().newBuilder()
        for ((k, v) in all) {
            urlBuilder.addQueryParameter(k, v)
        }

        // 3. Dispatch.
        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val resp = httpClient.newCall(req).execute()
        resp.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                throw IOException("HTTP ${r.code} from alidns: ${body.take(200)}")
            }
            if (body.contains("\"Code\"")) {
                val err = ADAPTER_ERROR.fromJson(body)
                val code = err?.Code ?: "UnknownCode"
                val msg = err?.Message ?: body.take(200)
                throw AliyunApiException("Alidns $code: $msg")
            }
            return body
        }
    }

    /**
     * POP V3 signer. See https://help.aliyun.com/document_detail/29745.html
     * Steps:
     *   1. Sort params by key (caller supplies a TreeMap — we still
     *      re-copy defensively).
     *   2. URL-encode each key and value with Aliyun's tweak:
     *        URLEncoder.encode(s, "UTF-8")
     *          .replace("+", "%20")
     *          .replace("*", "%2A")
     *          .replace("%7E", "~")
     *   3. Build the "canonicalised query string" by joining
     *      "k=v" pairs with "&".
     *   4. Build the "string-to-sign" by URL-encoding the whole
     *      canonical string once more, then prefixing with "GET&%2F&".
     *   5. HMAC-SHA1 it with key = (accessKeySecret + "&"), base64 the
     *      digest.
     */
    private fun sign(
        method: String, params: Map<String, String>, sk: String
    ): String {
        val canonical = params.entries.joinToString("&") { (k, v) ->
            "${aliyunEncode(k)}=${aliyunEncode(v)}"
        }
        val stringToSign = "${method}&${aliyunEncode("/")}&${aliyunEncode(canonical)}"

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec("$sk&".toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val digest = mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
    }

    private fun aliyunEncode(s: String): String =
        URLEncoder.encode(s, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    /**
     * Cheap extract of the "RecordId":"..." field from AddDomainRecord's
     * response, without depending on a second Moshi data class. We
     * intentionally don't use a regex over the whole body — the field
     * position is bounded by the server's stable response shape.
     */
    private fun extractRecordId(body: String): String {
        val key = "\"RecordId\""
        val idx = body.indexOf(key)
        if (idx < 0) return ""
        val rest = body.substring(idx + key.length).trimStart(' ', ':', '\t', '\n', '\r')
        // Expect `"<id>"` — strip the surrounding quotes.
        if (!rest.startsWith("\"")) return ""
        val end = rest.indexOf('"', startIndex = 1)
        if (end <= 1) return ""
        return rest.substring(1, end)
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
        private const val ENDPOINT = "https://alidns.aliyuncs.com/"
        private const val USER_AGENT = "IPv6DDNS/1.0 (Android)"

        // Local Moshi + OkHttp, same pattern as ConfigRepository.kt.
        // We avoid the AppContainer singleton so this class stays a
        // self-contained leaf that can be unit-tested without a
        // Context. Performance is fine: the constructor builds the
        // adapter once and we share it across every call.
        private val moshi: Moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        private val httpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        private val ADAPTER_DESCRIBE = moshi.adapter(DescribeResp::class.java)
        private val ADAPTER_EMPTY = moshi.adapter(EmptyResp::class.java)
        private val ADAPTER_ERROR = moshi.adapter(ErrorResp::class.java)
    }
}

internal class AliyunApiException(message: String) : Exception(message)

// -- Moshi response shapes (internal to this file) ----------------------
//
// Field names match Aliyun's REST JSON exactly — the API is RPC-style
// and PascalCase top-level, so we deliberately do NOT use Moshi's
// @Json(name = "...") mapping. Reflection is fine here because these
// classes are only used for one-shot deserialisation; the project
// already pulls in KotlinJsonAdapterFactory elsewhere.

@JsonClass(generateAdapter = false)
internal data class DescribeResp(
    val DomainRecords: Records? = null,
    val RequestId: String? = null
)

@JsonClass(generateAdapter = false)
internal data class Records(
    val Record: List<DnsRecord>? = null
)

@JsonClass(generateAdapter = false)
internal data class DnsRecord(
    val RecordId: String = "",
    val RR: String = "",
    val Type: String = "",
    val Value: String = "",
    val TTL: Long? = null
)

@JsonClass(generateAdapter = false)
internal data class ErrorResp(
    val Code: String? = null,
    val Message: String? = null,
    val RequestId: String? = null
)

@JsonClass(generateAdapter = false)
internal data class EmptyResp(
    val RequestId: String? = null
)
