package com.smartdialer.app.ui.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.app.data.callerid.CallerIdResult
import com.smartdialer.app.data.callerid.CallerIdService
import com.smartdialer.app.data.model.ContactCounts
import com.smartdialer.app.data.model.ContactItem
import com.smartdialer.app.data.model.ContactSaveDestination
import com.smartdialer.app.data.model.ContactSaveDestinationType
import com.smartdialer.app.data.model.ContactSourceFilter
import com.smartdialer.app.data.model.DuplicateContactGroup
import com.smartdialer.app.data.repository.ContactsRepository
import com.smartdialer.app.telecom.CallManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactsViewModel(
    private val contactsRepository: ContactsRepository
) : ViewModel() {

    private val callerIdService = CallerIdService(contactsRepository.context)
    private var callerIdSearchJob: Job? = null

    private val _searchCallerIdResult = MutableStateFlow<CallerIdResult?>(null)
    val searchCallerIdResult: StateFlow<CallerIdResult?> = _searchCallerIdResult.asStateFlow()

    private val _isSearchingCallerId = MutableStateFlow(false)
    val isSearchingCallerId: StateFlow<Boolean> = _isSearchingCallerId.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = _contacts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(ContactSourceFilter.ALL)
    val activeFilter: StateFlow<ContactSourceFilter> = _activeFilter.asStateFlow()

    private val _counts = MutableStateFlow(ContactCounts())
    val counts: StateFlow<ContactCounts> = _counts.asStateFlow()

    private val _googleAccounts = MutableStateFlow<List<String>>(emptyList())
    val googleAccounts: StateFlow<List<String>> = _googleAccounts.asStateFlow()

    private val _selectedGoogleAccount = MutableStateFlow<String?>(null)
    val selectedGoogleAccount: StateFlow<String?> = _selectedGoogleAccount.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedContact = MutableStateFlow<ContactItem?>(null)
    val selectedContact: StateFlow<ContactItem?> = _selectedContact.asStateFlow()

    private val _showDeletedDialog = MutableStateFlow(false)
    val showDeletedDialog: StateFlow<Boolean> = _showDeletedDialog.asStateFlow()

    private val _showAddContactDialog = MutableStateFlow(false)
    val showAddContactDialog: StateFlow<Boolean> = _showAddContactDialog.asStateFlow()

    private val _initialContactName = MutableStateFlow("")
    val initialContactName: StateFlow<String> = _initialContactName.asStateFlow()

    private val _initialContactPhone = MutableStateFlow("")
    val initialContactPhone: StateFlow<String> = _initialContactPhone.asStateFlow()

    private val _availableDestinations = MutableStateFlow<List<ContactSaveDestination>>(emptyList())
    val availableDestinations: StateFlow<List<ContactSaveDestination>> = _availableDestinations.asStateFlow()

    private val _isSavingContact = MutableStateFlow(false)
    val isSavingContact: StateFlow<Boolean> = _isSavingContact.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    // Duplicate Contacts Management
    private val _duplicateGroups = MutableStateFlow<List<DuplicateContactGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateContactGroup>> = _duplicateGroups.asStateFlow()

    private val _isScanningDuplicates = MutableStateFlow(false)
    val isScanningDuplicates: StateFlow<Boolean> = _isScanningDuplicates.asStateFlow()

    private val _isMergingDuplicates = MutableStateFlow(false)
    val isMergingDuplicates: StateFlow<Boolean> = _isMergingDuplicates.asStateFlow()

    private val _showDuplicatesDialog = MutableStateFlow(false)
    val showDuplicatesDialog: StateFlow<Boolean> = _showDuplicatesDialog.asStateFlow()

    fun loadContacts() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val query = _searchQuery.value.trim()
            val filter = _activeFilter.value
            val selGAccount = _selectedGoogleAccount.value
            val list = contactsRepository.getContacts(
                searchQuery = query,
                filter = filter,
                selectedGoogleAccount = selGAccount
            )
            _contacts.value = list

            val cnts = contactsRepository.getContactCounts()
            _counts.value = cnts

            val gAccounts = contactsRepository.getGoogleAccounts()
            _googleAccounts.value = gAccounts

            val destinations = contactsRepository.getAvailableSaveDestinations()
            _availableDestinations.value = destinations

            _isRefreshing.value = false

            // Scan duplicates in background
            try {
                val dups = contactsRepository.findDuplicates()
                _duplicateGroups.value = dups
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun scanDuplicates(openDialogWhenDone: Boolean = true) {
        viewModelScope.launch {
            _isScanningDuplicates.value = true
            val duplicates = contactsRepository.findDuplicates()
            _duplicateGroups.value = duplicates
            _isScanningDuplicates.value = false
            if (openDialogWhenDone) {
                _showDuplicatesDialog.value = true
            }
        }
    }

    fun setShowDuplicatesDialog(show: Boolean) {
        _showDuplicatesDialog.value = show
    }

    fun mergeDuplicateGroup(group: DuplicateContactGroup) {
        viewModelScope.launch {
            _isMergingDuplicates.value = true
            val res = contactsRepository.mergeDuplicateGroup(group)
            _isMergingDuplicates.value = false
            if (res.isSuccess) {
                _duplicateGroups.value = _duplicateGroups.value.filter { it.key != group.key }
                loadContacts()
                _syncMessage.value = "Merged duplicate contact successfully!"
                delay(2500)
                _syncMessage.value = null
            }
        }
    }

    fun mergeAllDuplicates() {
        viewModelScope.launch {
            _isMergingDuplicates.value = true
            val groups = _duplicateGroups.value
            val res = contactsRepository.mergeAllDuplicates(groups)
            _isMergingDuplicates.value = false
            if (res.isSuccess) {
                val count = res.getOrDefault(0)
                _duplicateGroups.value = emptyList()
                _showDuplicatesDialog.value = false
                loadContacts()
                _syncMessage.value = "Cleaned and merged $count duplicate contacts!"
                delay(3000)
                _syncMessage.value = null
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        loadContacts()

        callerIdSearchJob?.cancel()
        val cleanDigits = query.filter { it.isDigit() }
        if (cleanDigits.length >= 3) {
            callerIdSearchJob = viewModelScope.launch {
                delay(200) // Debounce typing
                _isSearchingCallerId.value = true
                try {
                    val res = callerIdService.lookup(query, _contacts.value)
                    _searchCallerIdResult.value = res
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isSearchingCallerId.value = false
                }
            }
        } else {
            _searchCallerIdResult.value = null
            _isSearchingCallerId.value = false
        }
    }

    fun clearCallerIdSearchResult() {
        callerIdSearchJob?.cancel()
        _searchCallerIdResult.value = null
        _isSearchingCallerId.value = false
    }

    fun setFilter(filter: ContactSourceFilter) {
        _activeFilter.value = filter
        if (filter != ContactSourceFilter.GOOGLE) {
            _selectedGoogleAccount.value = null
        }
        loadContacts()
    }

    fun selectGoogleAccount(account: String?) {
        _selectedGoogleAccount.value = account
        _activeFilter.value = ContactSourceFilter.GOOGLE
        loadContacts()
    }

    fun openAddContactDialog(initialName: String = "", initialPhone: String = "") {
        _initialContactName.value = initialName
        _initialContactPhone.value = initialPhone
        viewModelScope.launch {
            val destinations = contactsRepository.getAvailableSaveDestinations()
            _availableDestinations.value = destinations
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
        onComplete: (Boolean, String?) -> Unit
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
                _syncMessage.value = "Contact saved to ${destination.title} successfully!"
                loadContacts()
                _showAddContactDialog.value = false
                onComplete(true, null)
                delay(3000)
                _syncMessage.value = null
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to save contact"
                onComplete(false, err)
            }
        }
    }

    fun syncGoogleContacts() {
        viewModelScope.launch {
            _isRefreshing.value = true
            contactsRepository.syncGoogleContacts()
            _syncMessage.value = "Google Contacts sync requested..."
            delay(1500)
            loadContacts()
            _syncMessage.value = "Contacts list refreshed."
            delay(2000)
            _syncMessage.value = null
        }
    }

    fun openGoogleSyncSettings() {
        contactsRepository.openSyncSettings()
    }

    fun openGoogleContactsTrash() {
        contactsRepository.openGoogleContactsTrash()
    }

    fun selectContact(contact: ContactItem?) {
        _selectedContact.value = contact
    }

    fun setShowDeletedDialog(show: Boolean) {
        _showDeletedDialog.value = show
    }

    fun callContact(context: Context, number: String) {
        val cleanNumber = number.trim()
        if (cleanNumber.isNotBlank()) {
            CallManager.initiateCall(context, cleanNumber, 0)
        }
    }

    fun sendSms(context: Context, number: String) {
        val cleanNumber = number.trim()
        if (cleanNumber.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$cleanNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendEmail(context: Context, email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$cleanEmail")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createNewContact(context: Context) {
        openAddContactDialog()
    }

    fun editContact(context: Context, contact: ContactItem) {
        try {
            val contactUri = if (!contact.lookupKey.isNullOrBlank()) {
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, contact.lookupKey)
            } else {
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id)
            }
            val intent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(contactUri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}
