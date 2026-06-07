package com.boss.ipv6ddns.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists user config. AccessKey Secret is stored in EncryptedSharedPreferences,
 * other (non-secret) fields use a normal SharedPreferences for simplicity.
 *
 * The user-editable list of IPv6 probe sources is stored as a JSON string in the
 * plain SharedPreferences; if it is missing or unparseable, [DEFAULT_PROBE_SOURCES]
 * is returned so a fresh install always has something to try.
 */
class ConfigRepository(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val plainPrefs: SharedPreferences =
        appContext.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)

    // Local Moshi — the only thing we serialise is the user-editable probe-source list.
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val probeListType = Types.newParameterizedType(List::class.java, ProbeSource::class.java)
    private val probeAdapter: JsonAdapter<List<ProbeSource>> = moshi.adapter(probeListType)

    private val _configFlow = MutableStateFlow(loadConfig())

    /** Reactive snapshot of the config; updated whenever saveConfig is called. */
    fun observe(): StateFlow<DDNSConfig> = _configFlow.asStateFlow()

    fun saveConfig(config: DDNSConfig) {
        securePrefs.edit()
            .putString(KEY_AK_ID, config.accessKeyId)
            .putString(KEY_AK_SECRET, config.accessKeySecret)
            .apply()
        plainPrefs.edit()
            .putString(KEY_ROOT, config.rootDomain)
            .putString(KEY_SUB, config.subDomain)
            .putInt(KEY_INTERVAL, config.intervalMinutes)
            .putLong(KEY_LAST_IPV6, config.lastKnownIpv6Hash)
            .putString(KEY_LAST_IPV6_STR, config.lastKnownIpv6)
            .putLong(KEY_LAST_CHECK_AT, config.lastCheckAtMillis)
            .putString(KEY_LAST_ERROR, config.lastError)
            .putString(KEY_LAST_DNS_VALUE, config.lastDnsValue)
            .putString(KEY_LAST_DNS_RECORD, config.lastDnsRecordId)
            .putLong(KEY_LAST_SUCCESS_AT, config.lastSuccessAtMillis)
            .putString(KEY_PROBE_SOURCES, probeAdapter.toJson(config.probeSources))
            .apply()
        _configFlow.value = config
    }

    fun loadConfig(): DDNSConfig {
        val probeRaw = plainPrefs.getString(KEY_PROBE_SOURCES, null)
        val probeList: List<ProbeSource> = if (probeRaw.isNullOrBlank()) {
            DEFAULT_PROBE_SOURCES
        } else {
            runCatching { probeAdapter.fromJson(probeRaw) }
                .getOrNull()
                ?.filter { it.url.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_PROBE_SOURCES
        }
        return DDNSConfig(
            accessKeyId = securePrefs.getString(KEY_AK_ID, "").orEmpty(),
            accessKeySecret = securePrefs.getString(KEY_AK_SECRET, "").orEmpty(),
            rootDomain = plainPrefs.getString(KEY_ROOT, "").orEmpty(),
            subDomain = plainPrefs.getString(KEY_SUB, "").orEmpty(),
            intervalMinutes = plainPrefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_MINUTES),
            lastKnownIpv6 = plainPrefs.getString(KEY_LAST_IPV6_STR, "").orEmpty(),
            lastKnownIpv6Hash = plainPrefs.getLong(KEY_LAST_IPV6, 0L),
            lastCheckAtMillis = plainPrefs.getLong(KEY_LAST_CHECK_AT, 0L),
            lastError = plainPrefs.getString(KEY_LAST_ERROR, "").orEmpty(),
            lastDnsValue = plainPrefs.getString(KEY_LAST_DNS_VALUE, "").orEmpty(),
            lastDnsRecordId = plainPrefs.getString(KEY_LAST_DNS_RECORD, "").orEmpty(),
            lastSuccessAtMillis = plainPrefs.getLong(KEY_LAST_SUCCESS_AT, 0L),
            probeSources = probeList
        )
    }

    fun hasAccessKey(): Boolean = securePrefs.getString(KEY_AK_ID, "").orEmpty().isNotBlank() &&
        securePrefs.getString(KEY_AK_SECRET, "").orEmpty().isNotBlank()

    fun hasFullConfig(): Boolean {
        val c = loadConfig()
        return c.accessKeyId.isNotBlank() &&
            c.accessKeySecret.isNotBlank() &&
            c.rootDomain.isNotBlank() &&
            c.subDomain.isNotBlank()
    }

    companion object {
        private const val SECURE_PREFS_NAME = "ipv6ddns_secure_prefs"
        private const val PLAIN_PREFS_NAME = "ipv6ddns_plain_prefs"

        private const val KEY_AK_ID = "ak_id"
        private const val KEY_AK_SECRET = "ak_secret"
        private const val KEY_ROOT = "root_domain"
        private const val KEY_SUB = "sub_domain"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_LAST_IPV6 = "last_ipv6_hash"
        private const val KEY_LAST_IPV6_STR = "last_ipv6"
        private const val KEY_LAST_CHECK_AT = "last_check_at"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_DNS_VALUE = "last_dns_value"
        private const val KEY_LAST_DNS_RECORD = "last_dns_record"
        private const val KEY_LAST_SUCCESS_AT = "last_success_at"
        private const val KEY_PROBE_SOURCES = "probe_sources_json"

        const val DEFAULT_INTERVAL_MINUTES = 30

        /**
         * Built-in defaults — the user can edit/remove these and add their own.
         * Shipped here (not in the DB) so a fresh install always has something usable.
         */
        val DEFAULT_PROBE_SOURCES: List<ProbeSource> = listOf(
            ProbeSource("ipify", "https://api64.ipify.org?format=json", "json"),
            ProbeSource("ident.me", "https://v6.ident.me", "text"),
            ProbeSource("ipw.cn", "https://6.ipw.cn", "text")
        )
    }
}
