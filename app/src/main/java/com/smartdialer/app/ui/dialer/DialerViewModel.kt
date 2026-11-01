package com.smartdialer.app.ui.dialer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.app.data.model.ContactItem
import com.smartdialer.app.data.model.SimInfo
import com.smartdialer.app.data.repository.ContactsRepository
import com.smartdialer.app.data.repository.SimRepository
import com.smartdialer.app.telecom.CallManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DialerViewModel(
    private val contactsRepository: ContactsRepository,
    private val simRepository: SimRepository
) : ViewModel() {

    private val _inputNumber = MutableStateFlow("")
    val inputNumber: StateFlow<String> = _inputNumber.asStateFlow()

    private val _matchedContacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val matchedContacts: StateFlow<List<ContactItem>> = _matchedContacts.asStateFlow()

    private val _sims = MutableStateFlow<List<SimInfo>>(emptyList())
    val sims: StateFlow<List<SimInfo>> = _sims.asStateFlow()

    init {
        loadSims()
    }

    fun loadSims() {
        _sims.value = simRepository.getSimCards()
    }

    fun onDigitClick(digit: String) {
        _inputNumber.value += digit
        searchT9(_inputNumber.value)
    }

    fun onZeroLongClick() {
        _inputNumber.value += "+"
        searchT9(_inputNumber.value)
    }

    fun onBackspaceClick() {
        if (_inputNumber.value.isNotEmpty()) {
            _inputNumber.value = _inputNumber.value.dropLast(1)
            searchT9(_inputNumber.value)
        }
    }

    fun onClearAll() {
        _inputNumber.value = ""
        _matchedContacts.value = emptyList()
    }

    fun setNumber(number: String) {
        _inputNumber.value = number
        searchT9(number)
    }

    private fun searchT9(query: String) {
        if (query.length < 2) {
            _matchedContacts.value = emptyList()
            return
        }
        viewModelScope.launch {
            val results = contactsRepository.getContacts(query)
            _matchedContacts.value = results.take(3)
        }
    }

    fun callWithSim(context: Context, simSlot: Int) {
        val number = _inputNumber.value.trim()
        if (number.isNotBlank()) {
            CallManager.initiateCall(context, number, simSlot)
        }
    }

    fun makeIpCall(context: Context) {
        val number = _inputNumber.value.trim()
        if (number.isNotBlank()) {
            CallManager.initiateCall(context, number, 0)
        }
    }
}
