package com.boss.ipv6ddns.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    /** Monotonic id assigned at creation. Stable across the lifetime of the entry
     *  and unique within a process — use this for LazyColumn keys (timestampMillis
     *  + message hash collide when two log lines land in the same millisecond). */
    val id: Long,
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    fun formattedTime(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestampMillis))
}

/**
 * In-memory ring buffer of log entries (max 200). UI consumes via StateFlow.
 */
class LogRepository {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val raw = CopyOnWriteArrayList<LogEntry>()
    private val nextId = AtomicLong(0L)

    @Synchronized
    fun log(level: LogLevel, tag: String, message: String) {
        if (raw.size >= MAX_ENTRIES) {
            raw.removeAt(0)
        }
        raw.add(
            LogEntry(
                id = nextId.incrementAndGet(),
                timestampMillis = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message
            )
        )
        _entries.update { raw.toList() }
    }

    fun d(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(LogLevel.WARN, tag, msg)
    fun e(tag: String, msg: String) = log(LogLevel.ERROR, tag, msg)

    fun clear() {
        raw.clear()
        _entries.value = emptyList()
    }

    companion object {
        const val MAX_ENTRIES = 200
    }
}
