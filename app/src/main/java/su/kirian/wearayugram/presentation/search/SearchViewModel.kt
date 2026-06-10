package su.kirian.wearayugram.presentation.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.domain.model.TgChat
import su.kirian.wearayugram.domain.model.TgFoundMessage

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val chatRepo = (app as WearAyugramApp).chatRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _chats = MutableStateFlow<List<TgChat>>(emptyList())
    val chats: StateFlow<List<TgChat>> = _chats.asStateFlow()

    private val _messages = MutableStateFlow<List<TgFoundMessage>>(emptyList())
    val messages: StateFlow<List<TgFoundMessage>> = _messages.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _query.value = q
        _searching.value = true
        viewModelScope.launch {
            // Independent requests — run in parallel so the slower public-chat
            // search doesn't delay message results.
            val chatsDeferred = async { chatRepo.searchChats(q) }
            val messagesDeferred = async { chatRepo.searchMessages(q) }
            _chats.value = chatsDeferred.await()
            _messages.value = messagesDeferred.await()
            _searching.value = false
        }
    }

    fun openChat(chatId: Long, onResolved: (isForum: Boolean) -> Unit) {
        viewModelScope.launch { onResolved(chatRepo.isForum(chatId)) }
    }
}
