package com.smartdialer.app.ui.dialer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.app.data.callerid.CallerIdResult
import com.smartdialer.app.data.callerid.CallerIdService
import com.smartdialer.app.data.model.ContactItem
import com.smartdialer.app.data.model.ContactSaveDestination
import com.smartdialer.app.data.model.SimInfo
import com.smartdialer.app.data.repository.ContactsRepository
import com.smartdialer.app.data.repository.IpCallPreferences
import com.smartdialer.app.data.repository.SimRepository
import com.smartdialer.app.telecom.CallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DialerViewModel(
    private val contactsRepository: ContactsRepository,
    private val simRepository: SimRepository,
    val ipPreferences: IpCallPreferences
) : ViewModel() {

    private val callerIdService = CallerIdService(contactsRepository.context)
    private var callerIdJob: Job? = null

    private val _callerIdResult = MutableStateFlow<CallerIdResult?>(null)
    val callerIdResult: StateFlow<CallerIdResult?> = _callerIdResult.asStateFlow()

    private val _isCallerIdLoading = MutableStateFlow(false)
    val isCallerIdLoading: StateFlow<Boolean> = _isCallerIdLoading.asStateFlow()

    private val _inputNumber = MutableStateFlow("")
    val inputNumber: StateFlow<String> = _inputNumber.asStateFlow()

    private val _matchedContacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val matchedContacts: StateFlow<List<ContactItem>> = _matchedContacts.asStateFlow()

    private val _sims = MutableStateFlow<List<SimInfo>>(emptyList())
    val sims: StateFlow<List<SimInfo>> = _sims.asStateFlow()

    private val _showIpDialog = MutableStateFlow(false)
    val showIpDialog: StateFlow<Boolean> = _showIpDialog.asStateFlow()

    private val _showAddContactDialog = MutableStateFlow(false)
    val showAddContactDialog: StateFlow<Boolean> = _showAddContactDialog.asStateFlow()

    private val _availableDestinations = MutableStateFlow<List<ContactSaveDestination>>(emptyList())
    val availableDestinations: StateFlow<List<ContactSaveDestination>> = _availableDestinations.asStateFlow()

    private val _addContactInitialName = MutableStateFlow("")
    val addContactInitialName: StateFlow<String> = _addContactInitialName.asStateFlow()

    private val _addContactInitialPhone = MutableStateFlow("")
    val addContactInitialPhone: StateFlow<String> = _addContactInitialPhone.asStateFlow()

    private val _isSavingContact = MutableStateFlow(false)
    val isSavingContact: StateFlow<Boolean> = _isSavingContact.asStateFlow()

    private var cachedContacts: List<ContactItem> = emptyList()

    init {
        loadSims()
        loadContactsCache()
    }

    fun loadSims() {
        _sims.value = simRepository.getSimCards()
    }

    fun loadContactsCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cachedContacts = contactsRepository.getContacts()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
        callerIdJob?.cancel()
        _callerIdResult.value = null
        _isCallerIdLoading.value = false
    }

    fun setNumber(number: String) {
        _inputNumber.value = number
        searchT9(number)
    }

    private fun searchT9(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            _matchedContacts.value = emptyList()
            callerIdJob?.cancel()
            _callerIdResult.value = null
            _isCallerIdLoading.value = false
            return
        }

        // Live Caller ID & Carrier search
        callerIdJob?.cancel()
        val digits = cleanQuery.filter { it.isDigit() }
        if (digits.length >= 3) {
            callerIdJob = viewModelScope.launch(Dispatchers.IO) {
                delay(200) // Debounce typing
                _isCallerIdLoading.value = true
                try {
                    val res = callerIdService.lookup(cleanQuery, cachedContacts)
                    _callerIdResult.value = res
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isCallerIdLoading.value = false
                }
            }
        } else {
            _callerIdResult.value = null
            _isCallerIdLoading.value = false
        }

        viewModelScope.launch(Dispatchers.Default) {
            if (cachedContacts.isEmpty()) {
                try {
                    cachedContacts = contactsRepository.getContacts()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val queryDigits = cleanQuery.filter { it.isDigit() }
            if (queryDigits.isEmpty() && cleanQuery.none { it == '+' || it == '*' || it == '#' }) {
                _matchedContacts.value = emptyList()
                return@launch
            }

            val matched = cachedContacts.filter { contact ->
                // 1. Phone number match (clean digits, last digits / suffix, prefix, contains)
                val phoneMatch = contact.phoneNumbers.any { raw ->
                    val cleanPhone = raw.filter { it.isDigit() }
                    (queryDigits.isNotEmpty() && cleanPhone.contains(queryDigits)) ||
                        raw.contains(cleanQuery, ignoreCase = true)
                } || (contact.phoneNumber.isNotBlank() && (
                    (queryDigits.isNotEmpty() && contact.phoneNumber.filter { it.isDigit() }.contains(queryDigits)) ||
                        contact.phoneNumber.contains(cleanQuery, ignoreCase = true)
                ))

                if (phoneMatch) return@filter true

                // 2. T9 Dialpad Name match (Letters -> 2..9 digits)
                if (queryDigits.isNotEmpty()) {
                    val nameT9 = contact.name.map { charToT9Digit(it) }.joinToString("")
                    if (nameT9.contains(queryDigits)) return@filter true

                    // Check individual words in name
                    val words = contact.name.trim().split(Regex("""[\s\-_.]+"""))
                    words.any { word ->
                        val wordT9 = word.map { charToT9Digit(it) }.joinToString("")
                        wordT9.startsWith(queryDigits) || wordT9.contains(queryDigits)
                    }
                } else {
                    false
                }
            }.sortedWith { a, b ->
                val aClean = a.phoneNumber.filter { it.isDigit() }
                val bClean = b.phoneNumber.filter { it.isDigit() }
                val aEnds = queryDigits.isNotEmpty() && aClean.endsWith(queryDigits)
                val bEnds = queryDigits.isNotEmpty() && bClean.endsWith(queryDigits)
                val aStarts = queryDigits.isNotEmpty() && aClean.startsWith(queryDigits)
                val bStarts = queryDigits.isNotEmpty() && bClean.startsWith(queryDigits)

                val aNameT9 = if (queryDigits.isNotEmpty()) a.name.map { charToT9Digit(it) }.joinToString("") else ""
                val bNameT9 = if (queryDigits.isNotEmpty()) b.name.map { charToT9Digit(it) }.joinToString("") else ""
                val aNameStarts = queryDigits.isNotEmpty() && aNameT9.startsWith(queryDigits)
                val bNameStarts = queryDigits.isNotEmpty() && bNameT9.startsWith(queryDigits)

                when {
                    aEnds && !bEnds -> -1
                    !aEnds && bEnds -> 1
                    aNameStarts && !bNameStarts -> -1
                    !aNameStarts && bNameStarts -> 1
                    aStarts && !bStarts -> -1
                    !aStarts && bStarts -> 1
                    else -> a.name.compareTo(b.name, ignoreCase = true)
                }
            }

            _matchedContacts.value = matched.take(15)
        }
    }

    private fun charToT9Digit(c: Char): Char {
        return when (c.lowercaseChar()) {
            'a', 'b', 'c' -> '2'
            'd', 'e', 'f' -> '3'
            'g', 'h', 'i' -> '4'
            'j', 'k', 'l' -> '5'
            'm', 'n', 'o' -> '6'
            'p', 'q', 'r', 's' -> '7'
            't', 'u', 'v' -> '8'
            'w', 'x', 'y', 'z' -> '9'
            else -> '0'
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
        if (number.isBlank()) return

        if (!ipPreferences.isConfigured) {
            _showIpDialog.value = true
        } else {
            ipPreferences.makeIpCall(number, 0)
        }
    }

    fun openIpSettings() {
        _showIpDialog.value = true
    }

    fun dismissIpDialog() {
        _showIpDialog.value = false
    }

    fun openAddContactDialog(initialName: String = "", initialPhone: String = "") {
        _addContactInitialName.value = initialName
        _addContactInitialPhone.value = initialPhone
        viewModelScope.launch {
            _availableDestinations.value = contactsRepository.getAvailableSaveDestinations()
            _showAddContactDialog.value = true
        }
    }

    fun closeAddContactDialog() {
        _showAddContactDialog.value = false
    }

    fun saveContact(
        name: String,
        phoneNumber: String,
        secondaryNumber: String?,
        email: String?,
        company: String?,
        destination: ContactSaveDestination,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isSavingContact.value = true
            val result = contactsRepository.saveNewContact(
                name = name,
                phoneNumber = phoneNumber,
                secondaryNumber = secondaryNumber,
                email = email,
                company = company,
                destination = destination
            )
            _isSavingContact.value = false
            if (result.isSuccess) {
                _showAddContactDialog.value = false
                loadContactsCache()
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.localizedMessage ?: "Failed to save contact")
            }
        }
    }
}
