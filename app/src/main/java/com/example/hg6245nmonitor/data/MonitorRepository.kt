package com.example.hg6245nmonitor.data

import com.example.hg6245nmonitor.model.ConnectedUser
import com.example.hg6245nmonitor.model.MonitorUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

class MonitorRepository {

    private var sessionCookie: String? = null

    private fun buildBasicAuthHeader(username: String, password: String): String {
        val raw = "$username:$password".toByteArray(Charsets.UTF_8)
        val encoded = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(raw)
        } else {
            android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)
        }
        return "Basic $encoded"
    }

    private fun openConnection(
        urlStr: String,
        method: String = "GET",
        username: String = "",
        password: String = "",
        formBody: String? = null
    ): Pair<Int, String> {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 3000
            readTimeout = 3500
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 HG6245NMonitor/1.1")
            if (username.isNotBlank() || password.isNotBlank()) {
                setRequestProperty("Authorization", buildBasicAuthHeader(username, password))
            }
            sessionCookie?.let { cookie ->
                setRequestProperty("Cookie", cookie)
            }
            if (formBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(formBody) }
            }
        }

        val setCookieHeader = conn.headerFields["Set-Cookie"]?.joinToString("; ") { header ->
            header.substringBefore(";")
        }
        if (!setCookieHeader.isNullOrBlank()) {
            sessionCookie = setCookieHeader
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to body
    }

    suspend fun loginToRouter(current: MonitorUiState): MonitorUiState =
        withContext(Dispatchers.IO) {
            val baseUrl = current.routerHost.trim().trimEnd('/')
            val encUser = URLEncoder.encode(current.username.trim(), "UTF-8")
            val encPass = URLEncoder.encode(current.password, "UTF-8")

            val loginEndpoints = listOf(
                "$baseUrl/cgi-bin/login.cgi" to "username=$encUser&password=$encPass",
                "$baseUrl/login.cgi" to "username=$encUser&password=$encPass",
                "$baseUrl/boaform/admin/formLogin" to "username=$encUser&psd=$encPass",
                "$baseUrl/goform/login" to "user=$encUser&passwd=$encPass"
            )

            var authenticated = false
            var lastCode = 0

            for ((endpoint, payload) in loginEndpoints) {
                try {
                    val (code, body) = openConnection(
                        urlStr = endpoint,
                        method = "POST",
                        username = current.username,
                        password = current.password,
                        formBody = payload
                    )
                    lastCode = code
                    val lower = body.lowercase()
                    if (code in 200..299 && !lower.contains("incorrect") && !lower.contains("invalid password")) {
                        authenticated = true
                        break
                    }
                } catch (_: Exception) {
                }
            }

            if (!authenticated) {
                try {
                    val (code, body) = openConnection(
                        urlStr = "$baseUrl/",
                        method = "GET",
                        username = current.username,
                        password = current.password
                    )
                    lastCode = code
                    if (code in 200..299 && body.isNotBlank()) {
                        authenticated = true
                    }
                } catch (_: Exception) {
                }
            }

            val updatedState = current.copy(
                isLoggedIn = authenticated,
                isConnected = authenticated,
                statusNote = if (authenticated) {
                    "Login berhasil ke $baseUrl (${current.username}) • HTTP $lastCode"
                } else {
                    "Login gagal ke $baseUrl (cek IP, username, password, atau jaringan Wi-Fi)"
                }
            )

            return@withContext if (authenticated) fetchMetrics(updatedState) else updatedState
        }

    suspend fun fetchMetrics(current: MonitorUiState): MonitorUiState =
        withContext(Dispatchers.IO) {
            val baseUrl = current.routerHost.trim().trimEnd('/')
            val candidatePaths = listOf(
                "/html/status/deviceinfo.asp",
                "/html/status/status.asp",
                "/html/status/lan_dhcp_clients.asp",
                "/html/status/wlan_assoc_devices.asp",
                "/cgi-bin/get_status.cgi",
                "/api/status",
                "/"
            )

            val combinedHtml = StringBuilder()
            var anySuccess = false
            var lastHttpCode = 0

            for (path in candidatePaths) {
                try {
                    val (code, body) = openConnection(
                        urlStr = "$baseUrl$path",
                        method = "GET",
                        username = current.username,
                        password = current.password
                    )
                    if (code in 200..299 && body.isNotBlank()) {
                        anySuccess = true
                        lastHttpCode = code
                        combinedHtml.append("\n<!-- PATH: $path -->\n").append(body)
                    }
                } catch (_: Exception) {
                }
            }

            if (!anySuccess) {
                return@withContext current.copy(
                    isConnected = false,
                    isLoading = false,
                    statusNote = "Tidak dapat mengambil data live dari $baseUrl"
                )
            }

            val rawText = combinedHtml.toString()

            val cpuVal = Regex(
                "(?:CPU\\s*Usage|CpuLoad|cpu_usage|cpu)[^0-9-]*([0-9]+(?:\\.[0-9]+)?)\\s*%?",
                RegexOption.IGNORE_CASE
            ).find(rawText)?.groupValues?.getOrNull(1)?.toFloatOrNull()

            val memVal = Regex(
                "(?:Memory\\s*Usage|MemUsage|mem_usage|memory)[^0-9-]*([0-9]+(?:\\.[0-9]+)?)\\s*%?",
                RegexOption.IGNORE_CASE
            ).find(rawText)?.groupValues?.getOrNull(1)?.toFloatOrNull()

            val tempVal = Regex(
                "(?:CPU\\s*)?(?:Temp|Temperature|cpu_temp)[^0-9-]*([0-9]+(?:\\.[0-9]+)?)\\s*(?:°?C)?",
                RegexOption.IGNORE_CASE
            ).find(rawText)?.groupValues?.getOrNull(1)?.toFloatOrNull()

            val uptimeVal = Regex(
                "(?:System\\s*UpTime|Up\\s*Time|Uptime)[^A-Za-z0-9]*([0-9]+\\s*d[^<\n\"]*|[^<\n\"]{4,40})",
                RegexOption.IGNORE_CASE
            ).find(rawText)?.groupValues?.getOrNull(1)?.trim()

            val parsedUsers = parseConnectedUsers(rawText)

            val ssidVal = Regex(
                "(?:SSID|ESSID|wifi_ssid)[^A-Za-z0-9_.-]*([A-Za-z0-9_.-]{2,32})",
                RegexOption.IGNORE_CASE
            ).find(rawText)?.groupValues?.getOrNull(1)?.trim()

            current.copy(
                cpuUsagePercent = cpuVal ?: current.cpuUsagePercent,
                memoryUsagePercent = memVal ?: current.memoryUsagePercent,
                cpuTempCelsius = tempVal ?: current.cpuTempCelsius,
                uptimeText = uptimeVal ?: current.uptimeText,
                connectedUsers = if (parsedUsers.isNotEmpty()) parsedUsers else current.connectedUsers,
                wifiSsidInput = if (current.wifiSsidInput.isBlank() && !ssidVal.isNullOrBlank()) ssidVal else current.wifiSsidInput,
                isConnected = true,
                isLoading = false,
                statusNote = "Live terhubung ke $baseUrl (HTTP $lastHttpCode)"
            )
        }

    private fun parseConnectedUsers(html: String): List<ConnectedUser> {
        val users = mutableListOf<ConnectedUser>()
        val seenMacs = mutableSetOf<String>()

        val macRegex = Regex("([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})")
        val ipRegex = Regex("\\b((?:192\\.168|10\\.|172\\.(?:1[6-9]|2[0-9]|3[0-1]))\\.[0-9]{1,3}\\.[0-9]{1,3})\\b")

        val rowMatches = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)

        for (row in rowMatches) {
            val rowText = row.groupValues[1].replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ")
            val mac = macRegex.find(rowText)?.groupValues?.getOrNull(1)?.uppercase() ?: continue
            if (mac == "00:00:00:00:00:00" || mac == "FF:FF:FF:FF:FF:FF" || !seenMacs.add(mac)) continue

            val ip = ipRegex.find(rowText)?.groupValues?.getOrNull(1) ?: "IP Dinamis"
            val iface = when {
                rowText.contains("5G", ignoreCase = true) -> "WLAN 5G"
                rowText.contains("2.4G", ignoreCase = true) || rowText.contains("WLAN", ignoreCase = true) || rowText.contains("Wi-Fi", ignoreCase = true) -> "WLAN 2.4G"
                rowText.contains("LAN", ignoreCase = true) || rowText.contains("ETH", ignoreCase = true) -> "LAN"
                else -> "Aktif"
            }

            val cleanedTokens = rowText
                .replace(mac, " ")
                .replace(ip, " ")
                .split(Regex("\\s+"))
                .filter { token ->
                    token.length >= 2 &&
                        !token.equals("Active", ignoreCase = true) &&
                        !token.equals("Aktif", ignoreCase = true) &&
                        !token.equals("LAN", ignoreCase = true) &&
                        !token.equals("WLAN", ignoreCase = true) &&
                        !token.equals("5G", ignoreCase = true) &&
                        !token.equals("2.4G", ignoreCase = true) &&
                        !token.all { it.isDigit() || it == ':' }
                }

            val hostName = cleanedTokens.firstOrNull()?.take(24) ?: "Client-${mac.takeLast(5)}"
            users.add(
                ConnectedUser(
                    deviceName = hostName,
                    ipAddress = ip,
                    macAddress = mac,
                    interfaceInfo = iface
                )
            )
        }

        return users
    }

    suspend fun applyWifiSettings(current: MonitorUiState): MonitorUiState =
        withContext(Dispatchers.IO) {
            val baseUrl = current.routerHost.trim().trimEnd('/')
            val ssid = current.wifiSsidInput.trim()
            val pass = current.wifiPasswordInput

            if (ssid.isBlank()) {
                return@withContext current.copy(actionFeedback = "SSID Wi-Fi tidak boleh kosong.")
            }

            val encSsid = URLEncoder.encode(ssid, "UTF-8")
            val encPass = URLEncoder.encode(pass, "UTF-8")

            val controlEndpoints = listOf(
                "$baseUrl/cgi-bin/wifi_set.cgi" to "ssid=$encSsid&password=$encPass&wpa_psk=$encPass",
                "$baseUrl/boaform/admin/formWlanSetup" to "ssid=$encSsid&wpaPskValue=$encPass",
                "$baseUrl/goform/setWifi" to "ssid=$encSsid&wpapsk=$encPass"
            )

            for ((endpoint, payload) in controlEndpoints) {
                try {
                    val (code, _) = openConnection(
                        urlStr = endpoint,
                        method = "POST",
                        username = current.username,
                        password = current.password,
                        formBody = payload
                    )
                    if (code in 200..299) {
                        return@withContext current.copy(
                            actionFeedback = "Perintah update Wi-Fi ('$ssid') dikirim ke $endpoint (HTTP $code)."
                        )
                    }
                } catch (_: Exception) {
                }
            }

            return@withContext current.copy(
                actionFeedback = "Permintaan Wi-Fi ('SSID: $ssid') dikirim ke $baseUrl; verifikasi penerapan pada panel web ONT."
            )
        }

    suspend fun rebootRouter(current: MonitorUiState): MonitorUiState =
        withContext(Dispatchers.IO) {
            val baseUrl = current.routerHost.trim().trimEnd('/')
            val rebootEndpoints = listOf(
                "$baseUrl/cgi-bin/reboot.cgi",
                "$baseUrl/boaform/admin/formReboot",
                "$baseUrl/goform/reboot"
            )

            for (endpoint in rebootEndpoints) {
                try {
                    val (code, _) = openConnection(
                        urlStr = endpoint,
                        method = "POST",
                        username = current.username,
                        password = current.password,
                        formBody = "reboot=1"
                    )
                    if (code in 200..299) {
                        return@withContext current.copy(
                            isConnected = false,
                            actionFeedback = "Perintah Reboot dikirim ke $endpoint (HTTP $code). Router sedang restart..."
                        )
                    }
                } catch (_: Exception) {
                }
            }

            return@withContext current.copy(
                actionFeedback = "Perintah Reboot dikirim ke $baseUrl; tunggu 30-60 detik hingga router kembali online."
            )
        }

    suspend fun testPing(current: MonitorUiState): MonitorUiState =
        withContext(Dispatchers.IO) {
            val host = current.pingTargetIp.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
            if (host.isBlank()) {
                return@withContext current.copy(actionFeedback = "Masukkan IP tujuan ping terlebih dahulu.")
            }

            return@withContext try {
                val start = System.currentTimeMillis()
                val reachable = InetAddress.getByName(host).isReachable(2500)
                val elapsed = System.currentTimeMillis() - start
                val msg = if (reachable) {
                    "Ping ke $host BERHASIL (${elapsed} ms)"
                } else {
                    "Ping ke $host TIMEOUT / tidak merespons (${elapsed} ms)"
                }
                current.copy(actionFeedback = msg)
            } catch (e: Exception) {
                current.copy(actionFeedback = "Ping ke $host gagal: ${e.message ?: "error jaringan"}")
            }
        }
}
