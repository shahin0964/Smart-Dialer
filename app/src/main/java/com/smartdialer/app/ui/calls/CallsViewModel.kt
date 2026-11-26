package com.smartdialer.app.ui.calls

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.app.data.model.CallLogItem
import com.smartdialer.app.data.model.CallType
import com.smartdialer.app.data.model.GroupedCallLog
import com.smartdialer.app.data.repository.CallLogRepository
import com.smartdialer.app.data.repository.IpCallStatsRepository
import com.smartdialer.app.telecom.CallManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CallsHistoryFilter {
    ALL,
    INCOMING,
    OUTGOING,
    MISSED,
    SIP,
    SIM
}

class CallsViewModel(
    private val callLogRepository: CallLogRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private val _groupedCallLogs = MutableStateFlow<List<GroupedCallLog>>(emptyList())
    val groupedCallLogs: StateFlow<List<GroupedCallLog>> = _groupedCallLogs.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLogItem>>(emptyList())
    val callLogs: StateFlow<List<CallLogItem>> = _callLogs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(CallsHistoryFilter.ALL)
    val activeFilter: StateFlow<CallsHistoryFilter> = _activeFilter.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _missedCallCount = MutableStateFlow(0)
    val missedCallCount: StateFlow<Int> = _missedCallCount.asStateFlow()

    private var lastClearedMissedMillis: Long = 0L

    fun clearMissedBadge() {
        lastClearedMissedMillis = System.currentTimeMillis()
        _missedCallCount.value = 0
    }

    fun loadCallLogs() {
        viewModelScope.launch {
            val rawSystem = callLogRepository.getCallLogs(null)
            val ipKeys = if (appContext != null) {
                IpCallStatsRepository.getCallHistory(appContext).map { rec ->
                    Pair(
                        CallLogRepository.normalizeNumberForGrouping(rec.number),
                        rec.timestampMillis / 10_000L // ~10s window match
                    )
                }.toSet()
            } else {
                emptySet()
            }

            val enrichedSystem = rawSystem.map { item ->
                val norm = CallLogRepository.normalizeNumberForGrouping(item.phoneNumber)
                val tsBucket = item.dateMillis / 10_000L
                val isIp = ipKeys.contains(norm to tsBucket) ||
                    ipKeys.contains(norm to (tsBucket - 1)) ||
                    ipKeys.contains(norm to (tsBucket + 1))
                if (isIp) item.copy(isIpCall = true) else item
            }

            // Compute unread missed badge for today's missed calls
            val missedToday = enrichedSystem.count {
                it.type == CallType.MISSED && it.isToday && it.dateMillis > lastClearedMissedMillis
            }
            _missedCallCount.value = missedToday

            val baseList = when (_activeFilter.value) {
                CallsHistoryFilter.ALL -> enrichedSystem
                CallsHistoryFilter.INCOMING -> enrichedSystem.filter { it.type == CallType.INCOMING }
                CallsHistoryFilter.OUTGOING -> enrichedSystem.filter { it.type == CallType.OUTGOING }
                CallsHistoryFilter.MISSED -> enrichedSystem.filter { it.type == CallType.MISSED }
                CallsHistoryFilter.SIM -> enrichedSystem.filter { !it.isIpCall }
                CallsHistoryFilter.SIP -> {
                    val sipFromSystem = enrichedSystem.filter { it.isIpCall }
                    if (sipFromSystem.isNotEmpty() || appContext == null) {
                        sipFromSystem
                    } else {
                        // Fallback directly from IpCallStatsRepository if WRITE_CALL_LOG wasn't granted
                        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        val dateFmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        val today = Calendar.getInstance()
                        IpCallStatsRepository.getCallHistory(appContext).map { rec ->
                            val c = Calendar.getInstance().apply { timeInMillis = rec.timestampMillis }
                            val isToday = c.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                            CallLogItem(
                                id = rec.id,
                                name = rec.contactName,
                                phoneNumber = rec.number,
                                type = rec.callType,
                                dateMillis = rec.timestampMillis,
                                durationSeconds = rec.durationSeconds,
                                formattedTime = timeFmt.format(Date(rec.timestampMillis)),
                                formattedDate = dateFmt.format(Date(rec.timestampMillis)),
                                isToday = isToday,
                                isYesterday = false,
                                isIpCall = true
                            )
                        }
                    }
                }
            }

            val query = _searchQuery.value.trim().lowercase()
            val filteredRaw = if (query.isEmpty()) {
                baseList
            } else {
                baseList.filter {
                    (it.name?.lowercase()?.contains(query) == true) ||
                            it.phoneNumber.contains(query)
                }
            }
            _callLogs.value = filteredRaw
            _groupedCallLogs.value = CallLogRepository.groupCallLogs(filteredRaw)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        loadCallLogs()
    }

    fun toggleSearchVisibility() {
        _isSearchVisible.value = !_isSearchVisible.value
        if (!_isSearchVisible.value) {
            _searchQuery.value = ""
            loadCallLogs()
        }
    }

    fun setFilter(filter: CallsHistoryFilter) {
        _activeFilter.value = filter
        loadCallLogs()
    }

    fun callContact(context: Context, number: String) {
        CallManager.initiateCall(context, number, 0)
    }

    fun deleteLog(id: String) {
        viewModelScope.launch {
            val success = callLogRepository.deleteCallLog(id)
            if (success) {
                loadCallLogs()
            }
        }
    }

    fun deleteLogs(ids: List<String>) {
        viewModelScope.launch {
            val success = callLogRepository.deleteCallLogs(ids)
            if (success) {
                loadCallLogs()
            }
        }
    }

    fun deleteLogsForNumber(number: String) {
        viewModelScope.launch {
            val success = callLogRepository.deleteCallLogsForNumber(number)
            if (success) {
                loadCallLogs()
            }
        }
    }
}
