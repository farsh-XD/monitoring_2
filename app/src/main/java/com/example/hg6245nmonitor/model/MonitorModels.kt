package com.example.hg6245nmonitor.model

data class ConnectedUser(
    val deviceName: String,
    val ipAddress: String,
    val macAddress: String,
    val interfaceInfo: String
)

data class MonitorUiState(
    val routerHost: String = "http://192.168.1.100",
    val username: String = "admin",
    val password: String = "",
    val isLoggedIn: Boolean = false,
    val deviceModel: String = "HG6245N",
    val uptimeText: String = "-",
    val cpuUsagePercent: Float? = null,
    val memoryUsagePercent: Float? = null,
    val cpuTempCelsius: Float? = null,
    val pollIntervalSec: Int = 5,
    val autoRefresh: Boolean = true,
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val statusNote: String = "Masukkan kredensial lalu hubungkan ke 192.168.1.100",
    val connectedUsers: List<ConnectedUser> = emptyList(),
    val wifiSsidInput: String = "",
    val wifiPasswordInput: String = "",
    val pingTargetIp: String = "192.168.1.1",
    val actionFeedback: String? = null
)
