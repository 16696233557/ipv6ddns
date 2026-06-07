package com.boss.ipv6ddns.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boss.ipv6ddns.R
import com.boss.ipv6ddns.service.DDNSState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    activity: com.boss.ipv6ddns.MainActivity? = null
) {
    val vm: MainViewModel = viewModel(factory = MainViewModel.factory())
    val snapshot by vm.state.collectAsState()
    val context = LocalContext.current
    val whitelisted = vm.isIgnoringBatteryOptimizations(context)

    Column(
        modifier = modifier
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Permission / battery hints. Only render the cards whose condition holds
        // so a fully-set-up install shows nothing extra here.
        if (vm.isNotificationDenied(context)) {
            NotificationDeniedCard(
                onReRequest = { activity?.requestNotificationPermissionInApp() },
                onOpenSettings = { vm.openAppNotificationSettings(context) }
            )
        }
        // Only nag about the battery whitelist AFTER the user has actually
        // started the foreground service. Pre-service this is a confusing
        // "permission wall" that Google Play also frowns on; post-service
        // the user has context for why we need to keep running.
        if (!whitelisted && snapshot.serviceRunning) {
            BatteryCard(
                whitelisted = false,
                onRequest = { vm.openBatteryWhitelist(context) }
            )
        }
        RunningCard(running = snapshot.serviceRunning)
        Ipv6Card(snapshot)
        DnsCard(snapshot)
        TimingCard(snapshot)
        ErrorCard(snapshot)
        ActionsCard(
            onStart = { vm.startService(context) },
            onStop = { vm.stopService(context) },
            onCheckNow = { vm.checkNow(context) }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun NotificationDeniedCard(
    onReRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer
    )) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.status_notif_denied_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.status_notif_denied_msg),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary: re-request in-app. Works once per the system
                // "shouldShow" window; if the user picked "Don't ask again"
                // the call is a no-op and the system-settings button is the
                // only remaining path.
                TextButton(onClick = onReRequest) {
                    Text(stringResource(R.string.status_notif_denied_rerequest))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.status_notif_denied_action))
                }
            }
        }
    }
}

@Composable
private fun BatteryCard(whitelisted: Boolean, onRequest: () -> Unit) {
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer
    )) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.status_battery_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.status_battery_msg),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRequest) {
                    Text(stringResource(R.string.status_battery_action))
                }
            }
        }
    }
}

@Composable
private fun RunningCard(running: Boolean) {
    Card(colors = CardDefaults.cardColors(
        containerColor = if (running) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    )) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(20.dp)
                        .padding(2.dp),
                    strokeWidth = 2.dp,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = if (running) stringResource(R.string.status_running)
                else stringResource(R.string.status_stopped),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun Ipv6Card(s: DDNSState.Snapshot) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.status_current_ip),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = s.lastKnownIpv6.ifBlank { stringResource(R.string.status_no_ip) },
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DnsCard(s: DDNSState.Snapshot) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.status_dns_record),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = s.lastDnsValue.ifBlank { stringResource(R.string.status_dns_value) },
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
            if (s.lastDnsRecordId.isNotBlank()) {
                Text(
                    text = "RecordId: ${s.lastDnsRecordId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (s.config.fqdn.isNotBlank()) {
                HorizontalDivider()
                Text(
                    text = s.config.fqdn,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun TimingCard(s: DDNSState.Snapshot) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TimingRow(
                label = stringResource(R.string.status_last_check),
                value = formatTime(s.lastCheckAtMillis)
            )
            TimingRow(
                label = stringResource(R.string.status_next_check),
                value = if (s.nextCheckAtMillis <= 0L) "—"
                else countdown(s.nextCheckAtMillis)
            )
        }
    }
}

@Composable
private fun TimingRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ErrorCard(s: DDNSState.Snapshot) {
    if (s.lastError.isBlank()) return
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer
    )) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.status_last_error),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = s.lastError,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ActionsCard(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCheckNow: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCheckNow,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.status_check_now)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ElevatedButton(
                    onClick = onStart,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.status_start)) }
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.status_stop)) }
            }
        }
    }
}

private fun formatTime(epoch: Long): String =
    if (epoch <= 0L) "—"
    else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(epoch))

private fun countdown(target: Long): String {
    val remain = (target - System.currentTimeMillis()) / 1000L
    if (remain <= 0) return "now"
    val m = remain / 60
    val s = remain % 60
    return if (m > 0) String.format(Locale.getDefault(), "%dm %02ds", m, s)
    else String.format(Locale.getDefault(), "%ds", s)
}
