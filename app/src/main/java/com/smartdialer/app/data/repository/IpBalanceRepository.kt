package com.smartdialer.app.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Repository for fetching and updating the real account balance of an IPTSP / SIP line.
 * Automatically authenticates with provider gateways (e.g., Sarker Communication / Semux / amarip.net)
 * or parses real balance from SIP 200 OK headers. Eliminates all fake numbers or manual dummy additions.
 */
object IpBalanceRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    private val _lastFetchError = MutableStateFlow<String?>(null)
    val lastFetchError: StateFlow<String?> = _lastFetchError.asStateFlow()

    private val _balanceFlow = MutableStateFlow<Double?>(null)
    val balanceFlow: StateFlow<Double?> = _balanceFlow.asStateFlow()

    fun updateFromSipHeader(context: Context, balance: Double) {
        val prefs = IpCallPreferences(context)
        prefs.balance = balance
        prefs.isRealBalanceFetched = true
        _balanceFlow.value = balance
    }

    /**
     * Queries the actual live balance from the gateway backend.
     */
    fun fetchRealBalance(context: Context, force: Boolean = false) {
        if (_isFetching.value && !force) return

        scope.launch {
            _isFetching.value = true
            _lastFetchError.value = null
            try {
                val prefs = IpCallPreferences(context)
                val user = prefs.username.trim()
                val pass = prefs.password.trim()

                if (user.isBlank() || pass.isBlank()) {
                    _lastFetchError.value = "SIP credentials not set"
                    _isFetching.value = false
                    return@launch
                }

                val fetched = tryFetchSemuxBalance(user, pass)
                if (fetched != null) {
                    prefs.balance = fetched
                    prefs.isRealBalanceFetched = true
                    _balanceFlow.value = fetched
                    _lastFetchError.value = null
                } else {
                    _lastFetchError.value = "Could not retrieve balance"
                }
            } catch (e: Exception) {
                _lastFetchError.value = e.message ?: "Failed to check balance"
            } finally {
                _isFetching.value = false
            }
        }
    }

    private suspend fun tryFetchSemuxBalance(username: String, pass: String): Double? = withContext(Dispatchers.IO) {
        // Try standard username and formatted variants (with/without 09649 prefix)
        val usernamesToTry = mutableListOf(username)
        if (username.startsWith("09649")) {
            usernamesToTry.add(username.removePrefix("09649"))
        } else if (username.length == 6 && username.all { it.isDigit() }) {
            usernamesToTry.add("09649$username")
        }

        for (u in usernamesToTry.distinct()) {
            val token = loginAndGetToken(u, pass)
            if (token != null) {
                val balance = queryBalanceWithToken(token)
                if (balance != null) return@withContext balance
            }
        }
        null
    }

    private fun loginAndGetToken(username: String, pass: String): String? {
        return try {
            val url = URL("https://amarip.net/api/login")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val body = JSONObject().apply {
                put("username", username)
                put("password", pass)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(body.toString())
                it.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream ?: return null
            val raw = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
            conn.disconnect()

            val json = JSONObject(raw)
            json.optString("token").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun queryBalanceWithToken(token: String): Double? {
        val endpoints = listOf("https://amarip.net/api/whoami", "https://amarip.net/api/clients/me")
        for (ep in endpoints) {
            try {
                val url = URL(ep)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doInput = true
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val raw = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    conn.disconnect()
                    val json = JSONObject(raw)

                    // 1. Direct balance field
                    if (json.has("balance")) {
                        val b = json.optDouble("balance", Double.NaN)
                        if (!b.isNaN()) return b
                        val strB = json.optString("balance", "").trim()
                        val parsed = strB.toDoubleOrNull()
                        if (parsed != null) return parsed
                    }

                    // 2. Client sub-object
                    val clientObj = json.optJSONObject("client")
                    if (clientObj != null && clientObj.has("balance")) {
                        val b = clientObj.optDouble("balance", Double.NaN)
                        if (!b.isNaN()) return b
                        val strB = clientObj.optString("balance", "").trim()
                        val parsed = strB.toDoubleOrNull()
                        if (parsed != null) return parsed
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                // Try next endpoint
            }
        }
        return null
    }
}
