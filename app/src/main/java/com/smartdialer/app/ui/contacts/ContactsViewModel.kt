package com.smartdialer.app.ui.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.app.data.model.ContactCounts
import com.smartdialer.app.data.model.ContactItem
import com.smartdialer.app.data.model.ContactSourceFilter
import com.smartdialer.app.data.repository.ContactsRepository
import com.smartdialer.app.telecom.CallManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactsViewModel(
    private val contactsRepository: ContactsRepository
) : ViewModel() {

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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedContact = MutableStateFlow<ContactItem?>(null)
    val selectedContact: StateFlow<ContactItem?> = _selectedContact.asStateFlow()

    private val _showDeletedDialog = MutableStateFlow(false)
    val showDeletedDialog: StateFlow<Boolean> = _showDeletedDialog.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun loadContacts() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val query = _searchQuery.value.trim()
            val filter = _activeFilter.value
            val list = contactsRepository.getContacts(searchQuery = query, filter = filter)
            _contacts.value = list

            val cnts = contactsRepository.getContactCounts()
            _counts.value = cnts

            val gAccounts = contactsRepository.getGoogleAccounts()
            _googleAccounts.value = gAccounts
            _isRefreshing.value = false
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        loadContacts()
    }

    fun setFilter(filter: ContactSourceFilter) {
        _activeFilter.value = filter
        loadContacts()
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
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
