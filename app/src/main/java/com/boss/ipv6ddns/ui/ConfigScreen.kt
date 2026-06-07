package com.boss.ipv6ddns.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boss.ipv6ddns.R
import com.boss.ipv6ddns.data.ConfigRepository
import com.boss.ipv6ddns.data.DDNSConfig
import com.boss.ipv6ddns.data.ProbeSource
import com.boss.ipv6ddns.network.Ipv6Probe

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val vm: MainViewModel = viewModel(factory = MainViewModel.factory())
    val context = LocalContext.current
    val saved by vm.configFlow.collectAsState()

    var akId by rememberSaveable { mutableStateOf("") }
    var akSecret by rememberSaveable { mutableStateOf("") }
    var rootDomain by rememberSaveable { mutableStateOf("") }
    var subDomain by rememberSaveable { mutableStateOf("") }
    var intervalExpanded by remember { mutableStateOf(false) }
    var interval by rememberSaveable { mutableStateOf(30) }
    var initialised by rememberSaveable { mutableStateOf(false) }

    // Probe sources are edited in a sub-dialog so the main form stays simple.
    val probeSources = remember { mutableStateListOf<ProbeSource>() }
    var probeSourcesLoaded by rememberSaveable { mutableStateOf(false) }
    var showProbeDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (!initialised) {
            akId = saved.accessKeyId
            akSecret = saved.accessKeySecret
            rootDomain = saved.rootDomain
            subDomain = saved.subDomain
            interval = saved.intervalMinutes.coerceIn(1, 60)
            initialised = true
        }
        if (!probeSourcesLoaded) {
            probeSources.clear()
            probeSources.addAll(saved.probeSources)
            probeSourcesLoaded = true
        }
    }

    val whitelisted = vm.isIgnoringBatteryOptimizations(context)

    Column(
        modifier = modifier
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BatteryCard(
            whitelisted = whitelisted,
            onRequest = { vm.openBatteryWhitelist(context) }
        )

        Text(
            text = stringResource(R.string.config_title),
            style = MaterialTheme.typography.titleLarge
        )

        // AccessKey ID + paste button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = akId,
                onValueChange = { akId = it.trim() },
                label = { Text(stringResource(R.string.config_ak_id)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val text = readClipboard(context)
                    if (!text.isNullOrBlank()) {
                        akId = text.trim()
                        Toast.makeText(context, R.string.toast_pasted, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = stringResource(R.string.config_paste)
                )
            }
        }

        // AccessKey Secret + paste button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = akSecret,
                onValueChange = { akSecret = it.trim() },
                label = { Text(stringResource(R.string.config_ak_secret)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val text = readClipboard(context)
                    if (!text.isNullOrBlank()) {
                        akSecret = text.trim()
                        Toast.makeText(context, R.string.toast_pasted, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = stringResource(R.string.config_paste)
                )
            }
        }

        OutlinedTextField(
            value = rootDomain,
            onValueChange = { rootDomain = it.trim().lowercase() },
            label = { Text(stringResource(R.string.config_root_domain)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = subDomain,
            onValueChange = { subDomain = it.trim().lowercase() },
            label = { Text(stringResource(R.string.config_sub_domain)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(
            expanded = intervalExpanded,
            onExpandedChange = { intervalExpanded = it }
        ) {
            OutlinedTextField(
                value = intervalLabel(interval),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.config_interval)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = intervalExpanded,
                onDismissRequest = { intervalExpanded = false }
            ) {
                listOf(1, 5, 10, 30, 60).forEach { mins ->
                    DropdownMenuItem(
                        text = { Text(intervalLabel(mins)) },
                        onClick = {
                            interval = mins
                            intervalExpanded = false
                        }
                    )
                }
            }
        }

        // Probe sources card (clickable to open the editor)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.probe_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.probe_summary, probeSources.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                probeSources.take(3).forEach { src ->
                    Text(
                        text = "• ${src.name}  (${src.format})",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                if (probeSources.size > 3) {
                    Text(
                        text = "… +${probeSources.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showProbeDialog = true }) {
                        Text(stringResource(R.string.probe_edit))
                    }
                }
            }
        }

        Button(
            onClick = {
                if (akId.isBlank() || akSecret.isBlank() || rootDomain.isBlank() || subDomain.isBlank()) {
                    Toast.makeText(context, R.string.config_invalid, Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (probeSources.isEmpty()) {
                    Toast.makeText(context, R.string.probe_empty, Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val cfg = DDNSConfig(
                    accessKeyId = akId,
                    accessKeySecret = akSecret,
                    rootDomain = rootDomain,
                    subDomain = subDomain,
                    intervalMinutes = interval,
                    probeSources = probeSources.toList()
                )
                vm.saveConfig(cfg) {
                    Toast.makeText(context, R.string.config_saved, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.config_save))
        }
    }

    if (showProbeDialog) {
        ProbeSourcesDialog(
            initial = probeSources.toList(),
            onDismiss = { showProbeDialog = false },
            onConfirm = { newList ->
                probeSources.clear()
                probeSources.addAll(newList)
                showProbeDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProbeSourcesDialog(
    initial: List<ProbeSource>,
    onDismiss: () -> Unit,
    onConfirm: (List<ProbeSource>) -> Unit
) {
    val items = remember { mutableStateListOf<ProbeSource>().apply { addAll(initial) } }
    // Test status is keyed by the source's identity (name+url+format) so reordering
    // or editing the source naturally invalidates the cached result. Map value is
    // a small sealed class so the UI can show a spinner / success / error.
    val testStatus = remember { mutableStateMapOf<String, ProbeTestState>() }
    val testInProgress = remember { mutableStateMapOf<String, Boolean>() }
    val vm: MainViewModel = viewModel(factory = MainViewModel.factory())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.probe_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.probe_dialog_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                items.forEachIndexed { idx, src ->
                    val key = probeKey(src)
                    ProbeSourceEditor(
                        source = src,
                        status = testStatus[key],
                        testing = testInProgress[key] == true,
                        onUpdate = {
                            items[idx] = it
                            // Editing invalidates any prior test result for either
                            // the old or new identity.
                            testStatus.remove(probeKey(src))
                            testStatus.remove(probeKey(it))
                        },
                        onDelete = {
                            items.removeAt(idx)
                            testStatus.remove(probeKey(src))
                            testInProgress.remove(probeKey(src))
                        },
                        onTest = {
                            testInProgress[key] = true
                            testStatus.remove(key)
                            vm.testProbeSource(src) { result ->
                                testInProgress[key] = false
                                testStatus[key] = when (result) {
                                    is Ipv6Probe.Result.Ok -> ProbeTestState.Ok(result.ipv6)
                                    is Ipv6Probe.Result.Fail -> ProbeTestState.Fail(result.reason)
                                }
                            }
                        }
                    )
                }
                if (items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.probe_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        items.clear()
                        items.addAll(ConfigRepository.DEFAULT_PROBE_SOURCES)
                    }) {
                        Icon(Icons.Filled.Restore, contentDescription = null)
                        Text("  ${stringResource(R.string.probe_restore_defaults)}")
                    }
                    TextButton(onClick = {
                        items.add(ProbeSource(name = "new", url = "https://", format = "text"))
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("  ${stringResource(R.string.probe_add)}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Drop empty/blank URLs and any that don't start with http(s).
                val cleaned = items
                    .map { it.copy(url = it.url.trim()) }
                    .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
                if (cleaned.isEmpty()) {
                    // Nothing valid — keep the dialog open by re-confirming with the same
                    // items minus the bad ones; the empty-state message inside the dialog
                    // will guide the user.
                    onConfirm(emptyList())
                } else {
                    onConfirm(cleaned)
                }
            }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProbeSourceEditor(
    source: ProbeSource,
    status: ProbeTestState? = null,
    testing: Boolean = false,
    onUpdate: (ProbeSource) -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = source.name,
                    onValueChange = { onUpdate(source.copy(name = it)) },
                    label = { Text("name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onTest, enabled = !testing) {
                    if (testing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Science,
                            contentDescription = stringResource(R.string.probe_test)
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.probe_delete)
                    )
                }
            }
            OutlinedTextField(
                value = source.url,
                onValueChange = { onUpdate(source.copy(url = it)) },
                label = { Text("URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.probe_format_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val formatOptions = listOf("text", "json")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                formatOptions.forEachIndexed { idx, fmt ->
                    SegmentedButton(
                        selected = source.format.equals(fmt, ignoreCase = true),
                        onClick = { onUpdate(source.copy(format = fmt)) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = formatOptions.size)
                    ) { Text(fmt) }
                }
            }
            // Test result row. Hidden when there's nothing to show so the editor
            // stays compact for the common "just edit & save" path.
            status?.let { s ->
                TestStatusRow(state = s)
            }
        }
    }
}

@Composable
private fun TestStatusRow(state: ProbeTestState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (state) {
            is ProbeTestState.Ok -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.probe_test_ok, state.ipv6),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            is ProbeTestState.Fail -> {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.probe_test_fail, state.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private sealed class ProbeTestState {
    data class Ok(val ipv6: String) : ProbeTestState()
    data class Fail(val reason: String) : ProbeTestState()
}

private fun probeKey(s: ProbeSource): String = "${s.name}|${s.url}|${s.format}"

@Composable
private fun BatteryCard(whitelisted: Boolean, onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (whitelisted) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.config_battery_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.config_battery_msg),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (whitelisted) {
                    Text(
                        text = stringResource(R.string.config_battery_done),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    TextButton(onClick = onRequest) {
                        Text(stringResource(R.string.config_battery_action))
                    }
                }
            }
        }
    }
}

private fun intervalLabel(minutes: Int): String = when (minutes) {
    1 -> "1 min (debug)"
    5 -> "5 min"
    10 -> "10 min"
    30 -> "30 min (recommended)"
    60 -> "60 min"
    else -> "$minutes min"
}

private fun readClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    val clip: ClipData = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
