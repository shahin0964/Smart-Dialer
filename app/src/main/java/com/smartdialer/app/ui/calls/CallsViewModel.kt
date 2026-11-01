package com.smartdialer.app.ui.calls

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.app.data.model.CallLogItem
import com.smartdialer.app.data.model.CallType
import com.smartdialer.app.data.repository.CallLogRepository
import com.smartdialer.app.telecom.CallManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallsViewModel(
    private val callLogRepository: CallLogRepository
) : ViewModel() {

    private val _callLogs = MutableStateFlow<List<CallLogItem>>(emptyList())
    val callLogs: StateFlow<List<CallLogItem>> = _callLogs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow<CallType?>(null)
    val activeFilter: StateFlow<CallType?> = _activeFilter.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    fun loadCallLogs() {
        viewModelScope.launch {
            val raw = callLogRepository.getCallLogs(_activeFilter.value)
            val query = _searchQuery.value.trim().lowercase()
            _callLogs.value = if (query.isEmpty()) {
                raw
            } else {
                raw.filter {
                    (it.name?.lowercase()?.contains(query) == true) ||
                            it.phoneNumber.contains(query)
                }
            }
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

    fun setFilter(filter: CallType?) {
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
}
