package com.example.hg6245nmonitor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.hg6245nmonitor.model.MonitorUiState

@Composable
fun DashboardScreen(
    uiState: MonitorUiState,
    onRefresh: () -> Unit,
    onUpdateHost: (String) -> Unit,
    onUpdateCredentials: (String, String) -> Unit,
    onLogin: () -> Unit,
    onUpdateInterval: (Int) -> Unit,
    onToggleAutoRefresh: (Boolean) -> Unit,
    onUpdateWifiForm: (String, String) -> Unit,
    onApplyWifi: () -> Unit,
    onUpdatePingTarget: (String) -> Unit,
    onRunPing: () -> Unit,
    onRebootRouter: () -> Unit
) {
    var hostText by remember(uiState.routerHost) { mutableStateOf(uiState.routerHost) }
    var userText by remember(uiState.username) { mutableStateOf(uiState.username) }
    var passText by remember(uiState.password) { mutableStateOf(uiState.password) }
    var ssidText by remember(uiState.wifiSsidInput) { mutableStateOf(uiState.wifiSsidInput) }
    var wifiPassText by remember(uiState.wifiPasswordInput) { mutableStateOf(uiState.wifiPasswordInput) }
    var pingText by remember(uiState.pingTargetIp) { mutableStateOf(uiState.pingTargetIp) }
    var showRebootDialog by remember { mutableStateOf(false) }

    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = { Text("Konfirmasi Reboot Router") },
            text = { Text("Yakin ingin me-restart ${uiState.deviceModel} (${uiState.routerHost}) sekarang?") },
            confirmButton = {
                Button(
                    onClick = {
                        showRebootDialog = false
                        onRebootRouter()
                    }
                ) {
                    Text("Reboot")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${uiState.deviceModel} Live Dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.statusNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Live")
                }
            }
        }

        if (!uiState.actionFeedback.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.actionFeedback,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            val cpuDisplay = uiState.cpuUsagePercent?.let { "${"%.1f".format(it)}%" } ?: "--%"
            val memDisplay = uiState.memoryUsagePercent?.let { "${"%.2f".format(it)}%" } ?: "--%"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "CPU Usage",
                    valueText = cpuDisplay,
                    progress = ((uiState.cpuUsagePercent ?: 0f) / 100f).coerceIn(0f, 1f),
                    icon = Icons.Default.Speed
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Memory Usage",
                    valueText = memDisplay,
                    progress = ((uiState.memoryUsagePercent ?: 0f) / 100f).coerceIn(0f, 1f),
                    icon = Icons.Default.Memory
                )
            }
        }

        item {
            val tempValue = uiState.cpuTempCelsius
            val tempDisplay = tempValue?.let { "${"%.1f".format(it)} °C" } ?: "-- °C"
            val tempStatus = when {
                tempValue == null -> "Menunggu data"
                tempValue >= 75f -> "Panas"
                tempValue >= 58f -> "Hangat"
                else -> "Normal"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Suhu CPU",
                    valueText = tempDisplay,
                    subtitle = "Status: $tempStatus",
                    icon = Icons.Default.Thermostat
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Pengguna Tersambung",
                    valueText = "${uiState.connectedUsers.size} Perangkat",
                    subtitle = "Uptime: ${uiState.uptimeText}",
                    icon = Icons.Default.Devices
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Login Router", modifier = Modifier.size(18.dp))
                            Text("Login & Akses Router", style = MaterialTheme.typography.titleMedium)
                        }
                        SuggestionChip(
                            onClick = {},
                            label = { Text(if (uiState.isLoggedIn) "Masuk" else "Belum Masuk") }
                        )
                    }

                    OutlinedTextField(
                        value = hostText,
                        onValueChange = {
                            hostText = it
                            onUpdateHost(it)
                        },
                        label = { Text("IP / URL Router (mis. http://192.168.1.100)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = userText,
                        onValueChange = {
                            userText = it
                            onUpdateCredentials(it, passText)
                        },
                        label = { Text("Username Router") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = passText,
                        onValueChange = {
                            passText = it
                            onUpdateCredentials(userText, it)
                        },
                        label = { Text("Password Router") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onUpdateHost(hostText)
                            onUpdateCredentials(userText, passText)
                            onLogin()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Masuk & Sinkronkan Data Live")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Refresh Live (${uiState.pollIntervalSec}s)")
                        Switch(
                            checked = uiState.autoRefresh,
                            onCheckedChange = onToggleAutoRefresh
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10).forEach { sec ->
                            FilterChip(
                                selected = uiState.pollIntervalSec == sec,
                                onClick = { onUpdateInterval(sec) },
                                label = { Text("${sec}s") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = "Kontrol Wi-Fi", modifier = Modifier.size(18.dp))
                        Text("Kontrol Router & Wi-Fi", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedTextField(
                        value = ssidText,
                        onValueChange = {
                            ssidText = it
                            onUpdateWifiForm(it, wifiPassText)
                        },
                        label = { Text("Nama SSID Wi-Fi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = wifiPassText,
                        onValueChange = {
                            wifiPassText = it
                            onUpdateWifiForm(ssidText, it)
                        },
                        label = { Text("Password Wi-Fi Baru") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onUpdateWifiForm(ssidText, wifiPassText)
                            onApplyWifi()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simpan Pengaturan Wi-Fi")
                    }

                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.NetworkPing, contentDescription = "Uji Ping", modifier = Modifier.size(18.dp))
                        Text("Uji Ping Jaringan", style = MaterialTheme.typography.titleSmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = pingText,
                            onValueChange = {
                                pingText = it
                                onUpdatePingTarget(it)
                            },
                            label = { Text("IP Target Ping") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(onClick = {
                            onUpdatePingTarget(pingText)
                            onRunPing()
                        }) {
                            Text("Ping")
                        }
                    }

                    OutlinedButton(
                        onClick = { showRebootDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reboot", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reboot Router")
                    }
                }
            }
        }

        item {
            Text(
                text = "Pengguna yang Tersambung (${uiState.connectedUsers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (uiState.connectedUsers.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Belum ada klien terdeteksi dari tabel router atau silakan masuk & refresh saat terhubung ke HG6245N.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(uiState.connectedUsers) { user ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.deviceName, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "IP: ${user.ipAddress} • ${user.interfaceInfo}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "MAC: ${user.macAddress}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Aktif") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    valueText: String,
    progress: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = title, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    valueText: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = title, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
