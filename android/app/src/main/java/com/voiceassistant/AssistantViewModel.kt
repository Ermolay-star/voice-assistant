package com.voiceassistant

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.voiceassistant.model.Command
import com.voiceassistant.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _typing = MutableLiveData(false)
    val typing: LiveData<Boolean> = _typing

    private val _callIntent = MutableLiveData<Intent?>()
    val callIntent: LiveData<Intent?> = _callIntent

    // История диалога для GigaChat (role/content)
    private val history = mutableListOf<ChatMessage>()

    // ── Публичные действия ─────────────────────────────────────────────────

    fun handleCommand(command: Command) = when (command) {
        is Command.Call -> makeCall(command.contact)
        is Command.Ask  -> askGigaChat(command.question)
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        addUser(text)
        askGigaChat(text)
    }

    fun clearChat() {
        _messages.value = emptyList()
        history.clear()
    }

    // ── Звонок ────────────────────────────────────────────────────────────

    private fun makeCall(contact: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val number = resolveNumber(contact)
            withContext(Dispatchers.Main) {
                if (number != null) {
                    addAssistant("Звоню $contact…")
                    _callIntent.value = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                } else {
                    addAssistant("Контакт «$contact» не найден.")
                }
            }
        }
    }

    private fun resolveNumber(contact: String): String? {
        if (contact.matches(Regex("[+\\d][\\d\\s\\-()]{4,}"))) return contact
        val cursor = getApplication<Application>().contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$contact%"), null
        ) ?: return null
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    // ── GigaChat ──────────────────────────────────────────────────────────

    private fun askGigaChat(question: String) {
        history.add(ChatMessage("user", question))
        viewModelScope.launch {
            _typing.value = true
            try {
                val resp = BackendApiClient.api.ask(AskRequest(history.toList()))
                history.add(ChatMessage("assistant", resp.answer))
                addAssistant(resp.answer)
            } catch (e: Exception) {
                val err = "Ошибка: ${e.localizedMessage}"
                addAssistant(err)
            } finally {
                _typing.value = false
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    fun addUser(text: String) {
        _messages.value = (_messages.value ?: emptyList()) + Message(text, isUser = true)
    }

    private fun addAssistant(text: String) {
        _messages.postValue((_messages.value ?: emptyList()) + Message(text, isUser = false))
    }

    fun clearCallIntent() { _callIntent.value = null }
}
