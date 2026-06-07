package com.boss.ipv6ddns.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boss.ipv6ddns.R
import com.boss.ipv6ddns.data.LogEntry
import com.boss.ipv6ddns.data.LogLevel

@Composable
fun LogScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val vm: MainViewModel = viewModel(factory = MainViewModel.factory())
    val entries by vm.logs.collectAsState()
    // Newest-first, but cache the reversed list so the LazyColumn doesn't see
    // a brand-new List reference on every state tick (which would force it to
    // re-diff all visible rows even when only the tail changed).
    val reversedEntries = remember(entries) { entries.reversed() }

    Column(modifier = modifier.padding(contentPadding).fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.log_title),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = { vm.clearLogs() }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.log_clear))
            }
        }
        if (reversedEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.log_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // contentType = level so the LazyColumn can recycle same-typed
                // row composables without re-measuring the container color.
                items(
                    items = reversedEntries,
                    key = { it.id },
                    contentType = { it.level }
                ) { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val container = when (entry.level) {
        LogLevel.DEBUG -> MaterialTheme.colorScheme.surface
        LogLevel.INFO -> MaterialTheme.colorScheme.surface
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiaryContainer
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.level.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = entry.formattedTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = entry.message,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            if (entry.tag.isNotBlank()) {
                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
