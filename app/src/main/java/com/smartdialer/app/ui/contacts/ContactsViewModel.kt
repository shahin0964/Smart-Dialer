package com.smartdialer.app.ui.contacts

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.app.data.model.ContactItem
import com.smartdialer.app.data.repository.ContactsRepository
import com.smartdialer.app.telecom.CallManager
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

    fun loadContacts() {
        viewModelScope.launch {
            val query = _searchQuery.value.trim()
            val list = contactsRepository.getContacts(query)
            _contacts.value = list
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        loadContacts()
    }

    fun callContact(context: Context, number: String) {
        CallManager.initiateCall(context, number, 0)
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
}
